package com.aak.tilsynsapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ApiHelper {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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
                if (!response.isSuccessful) {
                    Log.e("ApiHelper", "Tasks error: ${response.code}")
                    return@withContext null
                }
                val rawJson = response.body.string()
                val type = object : TypeToken<List<TilsynItem>>() {}.type
                return@withContext gson.fromJson(rawJson, type)
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Error fetching tasks: ${e.message}")
            null
        }
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
                if (!response.isSuccessful) {
                    Log.e("ApiHelper", "History error: ${response.code}")
                    return@withContext null
                }
                val rawJson = response.body.string()
                val type = object : TypeToken<List<TilsynItem>>() {}.type
                return@withContext gson.fromJson(rawJson, type)
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Error fetching history: ${e.message}")
            null
        }
    }

    suspend fun unifiedInspect(
        context: Context,
        id: String,
        type: String, // "permission" or "henstilling"
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
            
            // Extract specific fields for history tracking if they exist in updates
            if (updates != null) {
                payload["updates"] = updates
                if (updates.containsKey("kvadratmeter")) payload["kvadratmeter"] = updates["kvadratmeter"]
                if (updates.containsKey("fakturaStatus")) payload["faktura_status"] = updates["fakturaStatus"]
            }

            val jsonPayload = gson.toJson(payload)
            Log.d("ApiHelper", "unifiedInspect Payload: $jsonPayload")

            val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${getBaseUrl()}tilsyn/inspect")
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                Log.d("ApiHelper", "unifiedInspect Response (${response.code}): $responseBody")
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Unified inspect error: ${e.message}", e)
            return@withContext false
        }
    }

    suspend fun sendRegelrytterenPayload(
        context: Context, 
        inspectors: List<Map<String, String>>, 
        vejman: Boolean, 
        henstillinger: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext "Ingen API-nøgle fundet"
            val payload = mapOf(
                "queue_name" to "RegelRytteren", 
                "status" to "NEW", 
                "data" to mapOf(
                    "inspectors" to inspectors, 
                    "vejman" to vejman, 
                    "henstillinger" to henstillinger
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

    suspend fun pollAuth(token: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("token" to token))
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${getBaseUrl()}auth/check")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val map: Map<String, Any> = gson.fromJson(response.body.string(), object : TypeToken<Map<String, Any>>() {}.type)
                val authorized = map["authorized"] == true
                val keyFromServer = map["api_key"] as? String
                val emailFromServer = map["email"] as? String
                if (authorized && !keyFromServer.isNullOrBlank()) Pair(keyFromServer, emailFromServer) else null
            }
        } catch (_: Exception) { null }
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
            val apiKey = SecurePrefs.getApiKey(context)
                ?: return@withContext IndmeldtCreateResult(false, null, null)

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
            val request = Request.Builder()
                .url("${getBaseUrl()}tilsyn/indmeldt")
                .post(body)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ApiHelper", "createIndmeldt error: ${response.code}")
                    return@withContext IndmeldtCreateResult(false, null, null)
                }
                val map: Map<String, Any> = gson.fromJson(
                    response.body.string(),
                    object : TypeToken<Map<String, Any>>() {}.type
                )
                IndmeldtCreateResult(true, map["id"] as? String, map["case_number"] as? String)
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "createIndmeldt exception: ${e.message}")
            IndmeldtCreateResult(false, null, null)
        }
    }

    data class DawaSuggestion(
        val label: String,
        val streetName: String?,
        val fullAddress: String,
        val latitude: Double,
        val longitude: Double,
    )

    private const val DAWA_BASE = "https://api.dataforsyningen.dk"
    private const val AARHUS_KOMMUNEKODE = "0751"

    // Dedicated DAWA client: force HTTP/1.1 (some Android stacks hit issues with
    // HTTP/2 gzip handling against DAWA) and let OkHttp transparently decompress.
    private val dawaClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()
    }

    private fun dawaRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "TilsynsApp-Android/${BuildConfig.VERSION_NAME}")
            .addHeader("Accept", "application/json")
            .addHeader("Accept-Encoding", "identity") // disable gzip so we always see raw JSON
            .build()

    private fun readBodyOrNull(response: okhttp3.Response): String? {
        return try {
            val bytes = response.body.bytes()
            if (bytes.isEmpty()) null else bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("ApiHelper", "readBody failed: ${e.message}")
            null
        }
    }

    suspend fun dawaAutocomplete(query: String): List<DawaSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) return@withContext emptyList()
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$DAWA_BASE/adresser/autocomplete?q=$encoded&per_side=10&kommunekode=$AARHUS_KOMMUNEKODE"
            dawaClient.newCall(dawaRequest(url)).execute().use { response ->
                val text = readBodyOrNull(response)
                Log.d("ApiHelper", "DAWA autocomplete: code=${response.code}, len=${text?.length ?: 0}")
                if (!response.isSuccessful || text.isNullOrBlank()) return@withContext emptyList()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val raw: List<Map<String, Any>> = gson.fromJson(text, type) ?: return@withContext emptyList()
                raw.mapNotNull { entry ->
                    val adresse = entry["adresse"] as? Map<*, *> ?: return@mapNotNull null
                    val street = adresse["vejnavn"] as? String ?: return@mapNotNull null
                    val husnr = adresse["husnr"] as? String ?: ""
                    val postnr = adresse["postnr"] as? String ?: ""
                    val postnrnavn = adresse["postnrnavn"] as? String ?: ""
                    val x = (adresse["x"] as? Double) ?: return@mapNotNull null
                    val y = (adresse["y"] as? Double) ?: return@mapNotNull null
                    val label = entry["tekst"] as? String
                        ?: "$street $husnr, $postnr $postnrnavn".trim()
                    DawaSuggestion(
                        label = label,
                        streetName = street,
                        fullAddress = "$street $husnr, $postnr $postnrnavn".replace("  ", " ").trim(),
                        latitude = y,
                        longitude = x,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "dawaAutocomplete exception", e)
            emptyList()
        }
    }

    suspend fun dawaReverseGeocode(latitude: Double, longitude: Double): DawaSuggestion? = withContext(Dispatchers.IO) {
        try {
            // struktur=flad gives kommunekode at the top level so we can verify Aarhus.
            val url = "$DAWA_BASE/adgangsadresser/reverse?x=$longitude&y=$latitude&struktur=flad"
            dawaClient.newCall(dawaRequest(url)).execute().use { response ->
                val text = readBodyOrNull(response)
                Log.d("ApiHelper", "DAWA reverse: code=${response.code}, len=${text?.length ?: 0}")
                if (!response.isSuccessful || text.isNullOrBlank()) return@withContext null
                val map: Map<String, Any> = gson.fromJson(
                    text,
                    object : TypeToken<Map<String, Any>>() {}.type
                ) ?: return@withContext null

                // Reject addresses outside Aarhus Kommune.
                val kommunekode = map["kommunekode"] as? String
                if (kommunekode != AARHUS_KOMMUNEKODE) {
                    Log.d("ApiHelper", "DAWA reverse: rejected kommunekode=$kommunekode (outside Aarhus)")
                    return@withContext null
                }

                val street = map["vejnavn"] as? String ?: return@withContext null
                val husnr = map["husnr"] as? String ?: ""
                val postnr = map["postnr"] as? String ?: ""
                val postnrnavn = map["postnrnavn"] as? String ?: ""
                // In struktur=flad coordinates are wgs84: (etrs89koordinat_øst = x, etrs89koordinat_nord = y)
                // but there's also wgs84koordinat_bredde/længde. Fall back to the input coords.
                val x = (map["wgs84koordinat_længde"] as? Double) ?: longitude
                val y = (map["wgs84koordinat_bredde"] as? Double) ?: latitude
                val full = "$street $husnr, $postnr $postnrnavn".replace("  ", " ").trim()
                DawaSuggestion(
                    label = full,
                    streetName = street,
                    fullAddress = full,
                    latitude = y,
                    longitude = x,
                )
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "dawaReverseGeocode exception", e)
            null
        }
    }

    suspend fun uploadImage(context: Context, id: String, imageBytes: ByteArray, fileName: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext false
            
            // Use provided filename or create a readable one: 20240520_143005_123.jpg
            val finalFileName = fileName ?: (SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date()) + ".jpg")

            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("image", finalFileName, 
                    imageBytes.toRequestBody("image/jpeg".toMediaType()))
                .addFormDataPart("id", id)
                .addFormDataPart("filename", finalFileName)
                .build()

            val request = Request.Builder()
                .url("${getBaseUrl()}tilsyn/upload-image")
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Image upload failed: ${e.message}")
            false
        }
    }
}
