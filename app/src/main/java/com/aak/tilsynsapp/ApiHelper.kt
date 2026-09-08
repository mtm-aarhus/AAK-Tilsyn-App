package com.aak.tilsynsapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ApiHelper {
    private val gson = Gson()
    
    // Main client for PyOrchestrator
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Dedicated KDS client: Force HTTP/1.1 to avoid "source exhausted prematurely"
    // and other protocol issues known with Dataforsyningen's infrastructure.
    private val kdsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            try { this.cancel() } catch (_: Throwable) {}
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Suppress("SameReturnValue")
    private fun getBaseUrl(): String {
        return BuildConfig.API_URL
    }

    suspend fun getUnifiedTasks(context: Context): List<TilsynItem>? = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext null
            val request = Request.Builder()
                .url("${getBaseUrl()}tilsyn/tasks")
                .get()
                .addHeader("X-API-Key", apiKey)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val rawJson = response.body.string()
                val type = object : TypeToken<List<TilsynItem>>() {}.type
                return@withContext gson.fromJson(rawJson, type)
            }
        } catch (e: Exception) { null }
    }

    suspend fun getUnifiedHistory(context: Context): List<TilsynItem>? = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext null
            val request = Request.Builder()
                .url("${getBaseUrl()}tilsyn/history")
                .get()
                .addHeader("X-API-Key", apiKey)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val rawJson = response.body.string()
                val type = object : TypeToken<List<TilsynItem>>() {}.type
                return@withContext gson.fromJson(rawJson, type)
            }
        } catch (e: Exception) { null }
    }

    suspend fun unifiedInspect(
        context: Context,
        id: String,
        type: String,
        comment: String?,
        selection: String? = null,
        oldStatus: String? = null,
        updates: Map<String, Any?>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext false
            val email = SecurePrefs.getEmail(context) ?: return@withContext false

            val payload = mutableMapOf<String, Any?>(
                "id" to id,
                "type" to type,
                "inspector_email" to email,
                "comment" to comment,
                "selection" to selection,
                "inspected_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).format(Date())
            )
            if (oldStatus != null) payload["oldStatus"] = oldStatus
            if (updates != null) {
                payload["updates"] = updates
                if (updates.containsKey("kvadratmeter")) payload["kvadratmeter"] = updates["kvadratmeter"]
                if (updates.containsKey("fakturaStatus")) payload["faktura_status"] = updates["fakturaStatus"]
            }

            val jsonPayload = gson.toJson(payload)
            val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${getBaseUrl()}tilsyn/inspect")
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).awaitResponse().use { it.isSuccessful }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
    }

    suspend fun sendRegelrytterenPayload(
        context: Context,
        inspectors: List<Map<String, String>>,
        vejman: Boolean,
        henstillinger: Boolean,
        indmeldte: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext "Ingen API-nøgle fundet"
            val payload = mapOf(
                "queue_name" to "RegelRytteren",
                "status" to "NEW",
                "data" to mapOf(
                    "inspectors" to inspectors,
                    "vejman" to vejman,
                    "henstillinger" to henstillinger,
                    "indmeldte" to indmeldte
                )
            )
            val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${getBaseUrl()}queue")
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) "Success" else "Fejl: ${response.code}"
            }
        } catch (_: Exception) { "Netværksfejl" }
    }

    suspend fun sendLoginRequest(email: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("email" to email))
            val requestBody = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("${getBaseUrl()}auth/request-link").post(requestBody).addHeader("Content-Type", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val map: Map<String, String> = gson.fromJson(response.body.string(), object : TypeToken<Map<String, String>>() {}.type)
                map["token"]
            }
        } catch (_: Exception) { null }
    }

    suspend fun pollAuth(token: String): Map<String, String?>? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("token" to token))
            val requestBody = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("${getBaseUrl()}auth/check").post(requestBody).addHeader("Content-Type", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val map: Map<String, Any> = gson.fromJson(response.body.string(), object : TypeToken<Map<String, Any>>() {}.type)
                if (map["authorized"] != true) return@withContext null
                mapOf(
                    "api_key" to (map["api_key"] as? String),
                    "email" to (map["email"] as? String),
                    "kds_token" to (map["kds_token"] as? String)
                )
            }
        } catch (_: Exception) { null }
    }

    suspend fun registerFcmToken(context: Context, fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext false
            val email = SecurePrefs.getEmail(context) ?: return@withContext false
            val payload = mapOf("email" to email, "fcm_token" to fcmToken)
            val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("${getBaseUrl()}tilsyn/register-token").post(body).addHeader("X-API-Key", apiKey).addHeader("Content-Type", "application/json").build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    suspend fun fetchVersionInfo(): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${getBaseUrl()}tilsynapp/version").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                gson.fromJson(response.body.string(), object : TypeToken<Map<String, Any>>() {}.type)
            }
        } catch (_: Exception) { null }
    }

    data class IndmeldtCreateResult(val success: Boolean, val id: String?, val caseNumber: String?)

    suspend fun createIndmeldt(
        context: Context,
        fullAddress: String,
        streetName: String?,
        latitude: Double,
        longitude: Double,
        title: String,
        description: String?,
        createdBy: String,
        createdBySource: String = "app"
    ): IndmeldtCreateResult = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext IndmeldtCreateResult(false, null, null)
            val payload = mapOf(
                "full_address" to fullAddress,
                "street_name" to streetName,
                "latitude" to latitude,
                "longitude" to longitude,
                "title" to title,
                "description" to description,
                "created_by" to createdBy,
                "created_by_source" to createdBySource,
            )
            val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("${getBaseUrl()}tilsyn/indmeldt").post(body).addHeader("X-API-Key", apiKey).addHeader("Content-Type", "application/json").build()
            client.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) return@withContext IndmeldtCreateResult(false, null, null)
                val map: Map<String, Any> = gson.fromJson(response.body.string(), object : TypeToken<Map<String, Any>>() {}.type)
                IndmeldtCreateResult(true, map["id"] as? String, map["case_number"] as? String)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { IndmeldtCreateResult(false, null, null) }
    }

    data class AddressSuggestion(
        val label: String,
        val streetName: String?,
        val fullAddress: String,
        val latitude: Double,
        val longitude: Double,
        val id: String? = null,
        val type: String? = null
    )

    private const val AARHUS_KOMMUNEKODE = "0751"

    suspend fun addressAutocomplete(context: Context, query: String): List<AddressSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) return@withContext emptyList()
        try {
            val token = SecurePrefs.getKdsToken(context) ?: "adressevaelger123"
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.dataforsyningen.dk/adresser/autocomplete?q=$encoded&per_side=20&kommunekode=$AARHUS_KOMMUNEKODE&token=$token"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "TilsynsApp-Android/${BuildConfig.VERSION_NAME}")
                .addHeader("Accept", "application/json")
                .build()

            kdsClient.newCall(request).execute().use { response ->
                val body = response.body
                val text = body.string()
                Log.d("ApiHelper", "Autocomplete raw: $text")
                if (!response.isSuccessful || text.isBlank()) return@withContext emptyList()
                
                // The log shows a raw JSON array: [ { "tekst": "...", "adresse": { ... } }, ... ]
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val raw: List<Map<String, Any>> = gson.fromJson(text, listType) ?: return@withContext emptyList()
                
                raw.mapNotNull { entry ->
                    val adresse = entry["adresse"] as? Map<*, *> ?: return@mapNotNull null
                    val street = adresse["vejnavn"] as? String ?: return@mapNotNull null
                    AddressSuggestion(
                        label = entry["tekst"] as? String ?: "",
                        streetName = street,
                        fullAddress = (entry["tekst"] as? String ?: "").replace("  ", " ").trim(),
                        latitude = (adresse["y"] as? Double) ?: (adresse["wgs84koordinat_bredde"] as? Double) ?: 0.0,
                        longitude = (adresse["x"] as? Double) ?: (adresse["wgs84koordinat_længde"] as? Double) ?: 0.0,
                        id = adresse["id"] as? String,
                        type = "adresse"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Autocomplete exception: ${e.message}")
            emptyList()
        }
    }

    suspend fun addressLookup(context: Context, id: String, type: String): AddressSuggestion? = withContext(Dispatchers.IO) {
        try {
            val token = SecurePrefs.getKdsToken(context) ?: "adressevaelger123"
            val url = "https://adressevaelger.dk/husnumre/$id?token=$token"
            val request = Request.Builder().url(url).get().addHeader("User-Agent", "TilsynsApp-Android/${BuildConfig.VERSION_NAME}").addHeader("Accept", "application/json").build()
            kdsClient.newCall(request).execute().use { response ->
                val body = response.body
                val text = body.string()
                Log.d("ApiHelper", "Lookup raw: $text")
                if (!response.isSuccessful || text.isBlank()) return@withContext null
                val root: Map<String, Any> = gson.fromJson(text, object : TypeToken<Map<String, Any>>() {}.type)
                if (root["status"] != "ok") return@withContext null
                val data = (root["husnummer"] ?: root["adresse"]) as? Map<*, *> ?: return@withContext null
                val point = data["adgangspunkt"] as? Map<*, *>
                val coords = point?.get("koordinater") as? Map<*, *>
                AddressSuggestion(
                    label = (data["adgangsadressebetegnelse"] ?: data["adressebetegnelse"]) as? String ?: "",
                    streetName = data["vejnavn"] as? String ?: "",
                    fullAddress = (data["adgangsadressebetegnelse"] ?: data["adressebetegnelse"]) as? String ?: "",
                    latitude = (coords?.get("y") as? Double) ?: (data["y"] as? Double) ?: (data["wgs84koordinat_bredde"] as? Double) ?: 0.0,
                    longitude = (coords?.get("x") as? Double) ?: (data["x"] as? Double) ?: (data["wgs84koordinat_længde"] as? Double) ?: 0.0,
                    id = id,
                    type = type
                )
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Lookup exception: ${e.message}")
            null
        }
    }

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): AddressSuggestion? = withContext(Dispatchers.IO) {
        try {
            val token = SecurePrefs.getKdsToken(context) ?: "adressevaelger123"
            val url = "https://api.dataforsyningen.dk/adgangsadresser/reverse?x=$longitude&y=$latitude&srid=4326&struktur=mini&token=$token"
            val request = Request.Builder().url(url).get().addHeader("User-Agent", "TilsynsApp-Android/${BuildConfig.VERSION_NAME}").addHeader("Accept", "application/json").build()
            kdsClient.newCall(request).execute().use { response ->
                val body = response.body
                val text = body.string()
                Log.d("ApiHelper", "Reverse raw: $text")
                if (!response.isSuccessful || text.isBlank()) return@withContext null
                val data: Map<String, Any> = gson.fromJson(text, object : TypeToken<Map<String, Any>>() {}.type)
                
                // Detailed check for kommunekode
                val kommunekode = data["kommunekode"] as? String
                if (kommunekode != AARHUS_KOMMUNEKODE) {
                    Log.d("ApiHelper", "Reverse rejected: kommunekode=$kommunekode (outside Aarhus)")
                    return@withContext null
                }

                val street = data["vejnavn"] as? String ?: return@withContext null
                // Use 'betegnelse' or 'adressebetegnelse' for the full address string
                val full = (data["betegnelse"] ?: data["adressebetegnelse"]) as? String ?: ""

                AddressSuggestion(
                    label = full,
                    streetName = street,
                    fullAddress = full,
                    latitude = (data["y"] as? Double) ?: (data["etrs89koordinat_nord"] as? Double) ?: latitude,
                    longitude = (data["x"] as? Double) ?: (data["etrs89koordinat_øst"] as? Double) ?: longitude
                )
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Reverse exception: ${e.message}")
            null
        }
    }

    suspend fun uploadImage(context: Context, id: String, imageBytes: ByteArray, fileName: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext false
            val finalFileName = fileName ?: (SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date()) + ".jpg")
            val requestBody = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).addFormDataPart("image", finalFileName, imageBytes.toRequestBody("image/jpeg".toMediaType())).addFormDataPart("id", id).addFormDataPart("filename", finalFileName).build()
            val request = Request.Builder().url("${getBaseUrl()}tilsyn/upload-image").post(requestBody).addHeader("X-API-Key", apiKey).build()
            client.newCall(request).awaitResponse().use { it.isSuccessful }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
    }
}
