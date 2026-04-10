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
                "inspected_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH).format(Date())
            )
            
            if (oldStatus != null) payload["oldStatus"] = oldStatus
            if (updates != null) payload["updates"] = updates

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

    suspend fun sendRegelrytterenPayload(context: Context, bikes: Int, cars: Int, vejman: Boolean, henstillinger: Boolean): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext "Ingen API-nøgle fundet"
            val payload = mapOf("queue_name" to "RegelRytteren", "status" to "NEW", "data" to mapOf("bikes" to bikes, "cars" to cars, "vejman" to vejman, "henstillinger" to henstillinger))
            val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("${getBaseUrl()}queue").post(requestBody).addHeader("X-API-Key", apiKey).addHeader("Content-Type", "application/json").build()
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
