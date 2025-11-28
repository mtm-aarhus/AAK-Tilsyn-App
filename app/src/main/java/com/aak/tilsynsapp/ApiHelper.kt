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

object ApiHelper {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun getRowsByStatus(context: Context, status: String): List<VejmanKassenRow>? = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext null

            val jsonBody = gson.toJson(mapOf("status" to status))
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.API_URL}tilsynapp")
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ApiHelper", "HTTP error: ${response.code} - ${response.message}")
                    return@withContext null
                }

                val rawJson = response.body?.string()
                Log.d("ApiHelper", "Raw response: $rawJson")

                val type = object : TypeToken<List<VejmanKassenRow>>() {}.type
                return@withContext gson.fromJson(rawJson, type)
            }

        } catch (e: Exception) {
            Log.e("ApiHelper", "Exception during fetch: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun sendRegelrytterenPayload(
        context: Context,
        bikes: Int,
        cars: Int,
        vejman: Boolean,
        henstillinger: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext "Ingen API-nøgle fundet"

            val payload = mapOf(
                "queue_name" to "RegelRytteren",
                "status" to "NEW",
                "data" to mapOf(
                    "bikes" to bikes,
                    "cars" to cars,
                    "vejman" to vejman,
                    "henstillinger" to henstillinger
                )
            )

            val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${BuildConfig.API_URL}queue")
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext if (response.isSuccessful) {
                    "Ruteoptimering igangsat, du får en mail med den nye rute snarest"
                } else {
                    "Fejl: ${response.code} - ${response.message}"
                }
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Regelrytteren error: ${e.message}", e)
            return@withContext "Netværksfejl: ${e.message}"
        }
    }

    suspend fun updateRow(
        context: Context,
        id: String,
        updates: Map<String, Any?>,
        oldStatus: String?,
        newStatus: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(context) ?: return@withContext false

            val bodyData = mutableMapOf<String, Any?>().apply {
                put("id", id)
                put("oldStatus", oldStatus ?: "Ny")

                // The email should NOT go into updates (which become document fields)
                SecurePrefs.getEmail(context)?.let { put("userEmail", it) }

                // Apply changed fields
                putAll(updates)

                newStatus?.let { put("fakturaStatus", it) }
            }

            // Optional debug log
            Log.d("ApiHelper", "Sending update payload: ${gson.toJson(bodyData)}")

            val requestBody = gson.toJson(bodyData).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.API_URL}tilsynapp/update")
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    Log.e("ApiHelper", "Update failed (${response.code}): ${response.message}")
                    Log.e("ApiHelper", "Response body: $responseBody")
                    return@withContext false
                }

                Log.d("ApiHelper", "Update successful. Response: $responseBody")
                return@withContext true
            }

        } catch (e: Exception) {
            Log.e("ApiHelper", "Update Exception: ${e.message}", e)
            return@withContext false
        }
    }



    suspend fun sendLoginRequest(email: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("email" to email))
            Log.d("ApiHelper", "Sending login to: ${BuildConfig.API_URL}auth/request-link with payload: $json")

            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.API_URL}auth/request-link")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ApiHelper", "Login email failed: ${response.code} - ${response.message}")
                    return@withContext null
                }
                val jsonResp = response.body?.string()
                val type = object : TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String> = gson.fromJson(jsonResp, type)
                return@withContext map["token"]
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Exception during sendLoginRequest: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun pollAuth(token: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("token" to token))
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.API_URL}auth/check")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ApiHelper", "Poll failed: ${response.code} - ${response.message}")
                    return@withContext null
                }

                val jsonResp = response.body?.string()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(jsonResp, type)

                val authorized = map["authorized"] == true
                val keyFromServer = map["api_key"] as? String
                val emailFromServer = map["email"] as? String

                if (authorized && !keyFromServer.isNullOrBlank()) {
                    Log.d("ApiHelper", "Received API key from server")
                    return@withContext Pair(keyFromServer, emailFromServer)
                }

                Log.d("ApiHelper", "Token not authorized or key missing")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e("ApiHelper", "Exception during pollAuth: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun fetchVersionInfo(): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${BuildConfig.API_URL}tilsynapp/version")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val json = response.body?.string() ?: return@withContext null

                val type = object : TypeToken<Map<String, Any>>() {}.type
                return@withContext gson.fromJson(json, type)
            }
        } catch (e: Exception) {
            Log.e("ApiHelper", "Version check failed", e)
            return@withContext null
        }
    }


}
