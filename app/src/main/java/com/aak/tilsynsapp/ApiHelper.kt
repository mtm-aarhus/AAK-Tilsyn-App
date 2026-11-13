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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow
import kotlin.math.sqrt

object ApiHelper {
    private val gson = Gson()
    
    // Main client for TilsynAPI (mtmtilsyn.aarhuskommune.dk)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Dedicated KDS / Datafordeler client (HTTP/1.1, shorter timeouts). Certificates are
    // validated normally; the roots older devices lack are added in
    // res/xml/network_security_config.xml, so never swap in a trust-all manager here.
    private val kdsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
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
            // TilsynAPI turns this into the RegelRytteren queue item; the app's key
            // can't create queue items itself.
            val payload = mapOf(
                "inspectors" to inspectors,
                "vejman" to vejman,
                "henstillinger" to henstillinger,
                "indmeldte" to indmeldte
            )
            val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${getBaseUrl()}tilsyn/regelrytteren")
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) "Success" else "Fejl: ${response.code}"
            }
        } catch (_: Exception) { "Netværksfejl" }
    }

    /** The months a permission is left off the weekly list. The rule's own months
     *  (October to March for a parklet, else none) make it follow the rule again. */
    suspend fun setHiddenMonths(context: Context, id: String, months: List<Int>): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext false
            val payload = mapOf(
                "id" to id,
                "hidden_months" to months,
                "changed_by" to SecurePrefs.getEmail(context)
            )
            val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${getBaseUrl()}tilsyn/hidden-months")
                .post(body)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).awaitResponse().use { it.isSuccessful }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
    }

    suspend fun sendLoginRequest(email: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("email" to email))
            val requestBody = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("${getBaseUrl()}tilsyn/auth/request-link").post(requestBody).addHeader("Content-Type", "application/json").build()
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
            val request = Request.Builder().url("${getBaseUrl()}tilsyn/auth/check").post(requestBody).addHeader("Content-Type", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val map: Map<String, Any> = gson.fromJson(response.body.string(), object : TypeToken<Map<String, Any>>() {}.type)
                if (map["authorized"] != true) return@withContext null
                val datafordelerKey = (map["datafordeler_api_key"] ?: map["datafordeler_key"] ?: map["datafordelerApiKey"]) as? String
                mapOf(
                    "api_key" to (map["api_key"] as? String),
                    "email" to (map["email"] as? String),
                    "kds_token" to (map["kds_token"] as? String),
                    "datafordeler_api_key" to datafordelerKey
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
            val request = Request.Builder().url("${getBaseUrl()}tilsyn/app/version").get().build()
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

    private fun extractObjectOrFirst(map: Map<*, *>?, key: String): Map<*, *>? {
        if (map == null) return null
        val value = map[key]
        if (value is Map<*, *>) return value
        if (value is List<*> && value.isNotEmpty()) {
            val first = value[0]
            if (first is Map<*, *>) return first
        }
        return null
    }

    private data class AdressepunktData(
        val id: String,
        val utmX: Double,
        val utmY: Double
    )

    private data class HusnummerData(
        val id: String,
        val adgangspunktId: String,
        val utmX: Double,
        val utmY: Double,
        val distance: Double
    )

    private const val AARHUS_KOMMUNEKODE = "0751"

    private fun extractCoordinatesFromMap(map: Map<*, *>): Pair<Double, Double> {
        val adgangspunkt = extractObjectOrFirst(map, "adgangspunkt")
        val coords = extractObjectOrFirst(adgangspunkt, "koordinater")

        val rawX = (coords?.get("x") as? Number)?.toDouble()
            ?: (map["x"] as? Number)?.toDouble()
            ?: (map["wgs84koordinat_længde"] as? Number)?.toDouble()
            ?: 0.0

        val rawY = (coords?.get("y") as? Number)?.toDouble()
            ?: (map["y"] as? Number)?.toDouble()
            ?: (map["wgs84koordinat_bredde"] as? Number)?.toDouble()
            ?: 0.0

        return if (rawX > 1000.0 && rawY > 1000.0) {
            val latLon = Utm32Converter.utm32ToLatLon(rawX, rawY)
            Pair(latLon.latitude, latLon.longitude)
        } else {
            Pair(rawY, rawX)
        }
    }

    private fun adressevaelgerToken(context: Context): String =
        SecurePrefs.getKdsToken(context)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DATA_FORSYNINGEN_TOKEN.takeIf { it.isNotBlank() }
            ?: "adressevaelger123"

    private suspend fun adressevaelgerGet(context: Context, path: String, params: Map<String, String> = emptyMap()): Map<*, *>? {
        // HttpUrl encodes æ/ø/å as UTF-8, which Adressevælger requires.
        val url = "https://adressevaelger.dk/".toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .addQueryParameter("token", adressevaelgerToken(context))
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "TilsynsApp-Android/${BuildConfig.VERSION_NAME}")
            .addHeader("Accept", "application/json")
            .build()
        return kdsClient.newCall(request).awaitResponse().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) {
                // Log the path only: the full URL carries the token.
                Log.e("ApiHelper", "Adressevælger $path HTTP ${resp.code}: ${text.take(300)}")
                null
            } else gson.fromJson(text, Map::class.java)
        }
    }

    /**
     * Search box autocomplete via Adressevælger /husnumre/soeg (same as the Vejmankassen web app).
     * Hits of type husnummer/adresse carry an id and are resolved with [addressLookup] on tap;
     * vejnavn/navngivenvejpostnummer hits only complete the street name (id = null).
     */
    suspend fun addressAutocomplete(context: Context, query: String): List<AddressSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) return@withContext emptyList()
        try {
            val root = adressevaelgerGet(
                context,
                "husnumre/soeg",
                mapOf("tekst" to query.trim(), "kommunekode" to AARHUS_KOMMUNEKODE, "maksimum" to "10")
            ) ?: return@withContext emptyList()

            (root["fund"] as? List<*>).orEmpty().mapNotNull { raw ->
                val hit = raw as? Map<*, *> ?: return@mapNotNull null
                val type = hit["type"] as? String
                val title = (hit["titel"] as? String)?.trim().orEmpty()
                if (title.isBlank()) return@mapNotNull null
                val street = (hit["vejnavn"] ?: hit["vejNavn"]) as? String
                val selectable = type == "husnummer" || type == "adresse"
                AddressSuggestion(
                    label = title,
                    streetName = street,
                    fullAddress = title,
                    latitude = 0.0,
                    longitude = 0.0,
                    id = if (selectable) hit["id"] as? String else null,
                    type = type
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ApiHelper", "addressAutocomplete exception: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun addressLookup(context: Context, id: String, type: String): AddressSuggestion? = withContext(Dispatchers.IO) {
        try {
            // /adresser/{id} -> {"adresse": {adressebetegnelse, husnummer: {...}}}
            // /husnumre/{id} -> {"husnummer": {adgangsadressebetegnelse, vejnavn, adgangspunkt, ...}}
            val isAdresse = type == "adresse"
            val root = adressevaelgerGet(context, if (isAdresse) "adresser/$id" else "husnumre/$id")
                ?: return@withContext null
            val adresse = if (isAdresse) extractObjectOrFirst(root, "adresse") else null
            val husnummer = (if (isAdresse) extractObjectOrFirst(adresse, "husnummer") else extractObjectOrFirst(root, "husnummer"))
                ?: return@withContext null

            val kommune = extractObjectOrFirst(husnummer, "navngivenvejkommunedel")?.get("kommune")?.toString()
            if (kommune != null && kommune != AARHUS_KOMMUNEKODE) return@withContext null

            // Adressevælger returns EPSG:25832; extractCoordinatesFromMap converts to WGS84.
            val (lat, lon) = extractCoordinatesFromMap(husnummer)
            if (lat == 0.0 && lon == 0.0) return@withContext null

            val text = ((adresse?.get("adressebetegnelse") ?: husnummer["adgangsadressebetegnelse"]) as? String).orEmpty()
            AddressSuggestion(
                label = text,
                streetName = husnummer["vejnavn"] as? String,
                fullAddress = text,
                latitude = lat,
                longitude = lon,
                id = id,
                type = type
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ApiHelper", "Lookup exception: ${e.message}")
            null
        }
    }

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): AddressSuggestion? = withContext(Dispatchers.IO) {
        try {
            val dfApiKey = SecurePrefs.getDatafordelerApiKey(context)?.takeIf { it.isNotBlank() }
                ?: SecurePrefs.getKdsToken(context)?.takeIf { it.isNotBlank() }
                ?: BuildConfig.DATAFORDELER_API_KEY.takeIf { it.isNotBlank() }
                ?: BuildConfig.DATA_FORSYNINGEN_TOKEN.takeIf { it.isNotBlank() }
                ?: ""
            if (dfApiKey.isBlank()) {
                Log.e("ApiHelper", "Ingen Datafordeler API nøgle fundet")
                return@withContext AddressSuggestion(
                    label = "Ingen Datafordeler API nøgle fundet",
                    streetName = null,
                    fullAddress = "Ingen Datafordeler API nøgle fundet",
                    latitude = latitude,
                    longitude = longitude
                )
            }

            val utm = Utm32Converter.latLonToUtm32(latitude, longitude)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val ts = sdf.format(Date())

            val pointRegex = Regex("""POINT\s*\(\s*([0-9.-]+)\s+([0-9.-]+)\s*\)""", RegexOption.IGNORE_CASE)

            var foundAdressepunkte = emptyList<AdressepunktData>()

            for (radius in listOf(50, 150, 300)) {
                val x1 = String.format(Locale.US, "%.2f", utm.easting - radius)
                val x2 = String.format(Locale.US, "%.2f", utm.easting + radius)
                val y1 = String.format(Locale.US, "%.2f", utm.northing - radius)
                val y2 = String.format(Locale.US, "%.2f", utm.northing + radius)

                val polygonWkt = "POLYGON (($x1 $y1, $x2 $y1, $x2 $y2, $x1 $y2, $x1 $y1))"
                val query1 = "query { DAR_Adressepunkt(registreringstid: \"$ts\", virkningstid: \"$ts\", first: 80, where: { position: { within: { wkt: \"$polygonWkt\", crs: 25832 } } }) { nodes { id_lokalId position { wkt } } } }"

                val requestBody = gson.toJson(mapOf("query" to query1)).toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://graphql.datafordeler.dk/flexible/v3?apiKey=$dfApiKey")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val jsonText = kdsClient.newCall(request).awaitResponse().use { resp ->
                    if (!resp.isSuccessful) null else resp.body.string()
                } ?: continue

                val root: Map<String, Any> = gson.fromJson(jsonText, object : TypeToken<Map<String, Any>>() {}.type) ?: continue
                val errors = root["errors"] as? List<*>
                if (!errors.isNullOrEmpty()) {
                    val firstErr = errors[0] as? Map<*, *>
                    val errMsg = firstErr?.get("message") as? String ?: "GraphQL fejl"
                    Log.e("ApiHelper", "GraphQL DAR_Adressepunkt fejl: $errMsg")
                    return@withContext AddressSuggestion(
                        label = errMsg,
                        streetName = null,
                        fullAddress = errMsg,
                        latitude = latitude,
                        longitude = longitude
                    )
                }

                val dataMap = extractObjectOrFirst(root, "data")
                val darAdressepunkt = extractObjectOrFirst(dataMap, "DAR_Adressepunkt")
                val nodes = darAdressepunkt?.get("nodes") as? List<*> ?: emptyList<Any>()

                val currentPoints = mutableListOf<AdressepunktData>()
                for (node in nodes) {
                    val nodeMap = node as? Map<*, *> ?: continue
                    val id = nodeMap["id_lokalId"] as? String ?: continue
                    val posObj = extractObjectOrFirst(nodeMap, "position")
                    val wkt = posObj?.get("wkt") as? String ?: continue
                    val match = pointRegex.find(wkt)
                    val pX = match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val pY = match?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
                    currentPoints.add(AdressepunktData(id, pX, pY))
                }

                if (currentPoints.isNotEmpty()) {
                    foundAdressepunkte = currentPoints
                    break
                }
            }

            if (foundAdressepunkte.isEmpty()) {
                return@withContext AddressSuggestion(
                    label = "Ingen adresse i Aarhus Kommune",
                    streetName = null,
                    fullAddress = "Ingen adresse i Aarhus Kommune",
                    latitude = latitude,
                    longitude = longitude
                )
            }

            val apMap = foundAdressepunkte.associateBy { it.id }
            val idsJson = foundAdressepunkte.joinToString(separator = "\", \"", prefix = "[\"", postfix = "\"]") { it.id }
            val query2 = "query { DAR_Husnummer(registreringstid: \"$ts\", virkningstid: \"$ts\", first: 80, where: { adgangspunkt: { in: $idsJson } }) { nodes { id_lokalId adgangspunkt status } } }"

            val requestBody2 = gson.toJson(mapOf("query" to query2)).toRequestBody("application/json".toMediaType())
            val request2 = Request.Builder()
                .url("https://graphql.datafordeler.dk/flexible/v3?apiKey=$dfApiKey")
                .post(requestBody2)
                .addHeader("Content-Type", "application/json")
                .build()

            val jsonText2 = kdsClient.newCall(request2).awaitResponse().use { resp ->
                if (!resp.isSuccessful) null else resp.body.string()
            } ?: return@withContext AddressSuggestion(
                label = "Ingen adresse i Aarhus Kommune",
                streetName = null,
                fullAddress = "Ingen adresse i Aarhus Kommune",
                latitude = latitude,
                longitude = longitude
            )

            val root2: Map<String, Any> = gson.fromJson(jsonText2, object : TypeToken<Map<String, Any>>() {}.type)
            val errors2 = root2["errors"] as? List<*>
            if (!errors2.isNullOrEmpty()) {
                val firstErr = errors2[0] as? Map<*, *>
                val errMsg = firstErr?.get("message") as? String ?: "GraphQL fejl"
                Log.e("ApiHelper", "GraphQL DAR_Husnummer fejl: $errMsg")
                return@withContext AddressSuggestion(
                    label = errMsg,
                    streetName = null,
                    fullAddress = errMsg,
                    latitude = latitude,
                    longitude = longitude
                )
            }

            val dataMap2 = extractObjectOrFirst(root2, "data")
            val darHusnummer = extractObjectOrFirst(dataMap2, "DAR_Husnummer")
            val husnummerNodes = darHusnummer?.get("nodes") as? List<*> ?: emptyList<Any>()

            val validHusnumre = mutableListOf<HusnummerData>()
            for (node in husnummerNodes) {
                val nodeMap = node as? Map<*, *> ?: continue
                val status = nodeMap["status"]
                val isDropped = when (status) {
                    is Number -> status.toInt() in listOf(4, 5)
                    is String -> status.trim() in listOf("4", "5")
                    else -> false
                }
                if (isDropped) continue

                val id = nodeMap["id_lokalId"] as? String ?: continue
                val adgangspunktId = nodeMap["adgangspunkt"] as? String ?: continue
                val apData = apMap[adgangspunktId] ?: continue

                val dist = sqrt((apData.utmX - utm.easting).pow(2) + (apData.utmY - utm.northing).pow(2))
                validHusnumre.add(HusnummerData(id, adgangspunktId, apData.utmX, apData.utmY, dist))
            }

            if (validHusnumre.isEmpty()) {
                return@withContext AddressSuggestion(
                    label = "Ingen adresse i Aarhus Kommune",
                    streetName = null,
                    fullAddress = "Ingen adresse i Aarhus Kommune",
                    latitude = latitude,
                    longitude = longitude
                )
            }

            validHusnumre.sortBy { it.distance }

            val kdsToken = SecurePrefs.getKdsToken(context) ?: BuildConfig.DATA_FORSYNINGEN_TOKEN
            val top5 = validHusnumre.take(5)

            for (hn in top5) {
                try {
                    val url = "https://adressevaelger.dk/husnumre/${hn.id}?token=$kdsToken"
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("User-Agent", "TilsynsApp-Android/${BuildConfig.VERSION_NAME}")
                        .addHeader("Accept", "application/json")
                        .build()

                    val text = kdsClient.newCall(request).awaitResponse().use { resp ->
                        if (!resp.isSuccessful) null else resp.body.string()
                    } ?: continue

                    val rootMap: Map<String, Any> = gson.fromJson(text, object : TypeToken<Map<String, Any>>() {}.type) ?: continue
                    val husnummerObj = extractObjectOrFirst(rootMap, "husnummer") ?: continue

                    val kommuneObj = extractObjectOrFirst(husnummerObj, "navngivenvejkommunedel")
                    val rawKommune = kommuneObj?.get("kommune")
                    val kommunekode = when (rawKommune) {
                        is String -> rawKommune
                        is Number -> "%.0f".format(rawKommune.toDouble()).padStart(4, '0')
                        else -> null
                    }

                    if (kommunekode == AARHUS_KOMMUNEKODE) {
                        val fullAddress = (husnummerObj["adgangsadressebetegnelse"] ?: husnummerObj["adressebetegnelse"]) as? String ?: ""
                        val streetName = husnummerObj["vejnavn"] as? String ?: ""

                        val adgangspunktObj = extractObjectOrFirst(husnummerObj, "adgangspunkt")
                        val coordsObj = extractObjectOrFirst(adgangspunktObj, "koordinater")
                        val resUtmX = (coordsObj?.get("x") as? Number)?.toDouble() ?: hn.utmX
                        val resUtmY = (coordsObj?.get("y") as? Number)?.toDouble() ?: hn.utmY

                        val latLon = Utm32Converter.utm32ToLatLon(resUtmX, resUtmY)

                        return@withContext AddressSuggestion(
                            label = fullAddress,
                            streetName = streetName,
                            fullAddress = fullAddress,
                            latitude = latLon.latitude,
                            longitude = latLon.longitude,
                            id = hn.id,
                            type = "husnummer"
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    continue
                }
            }

            return@withContext AddressSuggestion(
                label = "Ingen adresse i Aarhus Kommune",
                streetName = null,
                fullAddress = "Ingen adresse i Aarhus Kommune",
                latitude = latitude,
                longitude = longitude
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ApiHelper", "Reverse geocode exception: ${e.message}")
            AddressSuggestion(
                label = "Ingen adresse i Aarhus Kommune",
                streetName = null,
                fullAddress = "Ingen adresse i Aarhus Kommune",
                latitude = latitude,
                longitude = longitude
            )
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
