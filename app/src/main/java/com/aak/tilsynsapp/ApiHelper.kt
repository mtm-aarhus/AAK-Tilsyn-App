package com.aak.tilsynsapp

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
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun getRowsByStatus(status: String): List<VejmanKassenRow>? = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(mapOf("status" to status))
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${Secrets.API_URL}vejmankassen")
                .post(requestBody)
                .addHeader("X-API-Key", Secrets.API_KEY)
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
        bikes: Int,
        cars: Int,
        vejman: Boolean,
        henstillinger: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
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
                .url("https://pyorchestrator.aarhuskommune.dk/api/queue")
                .post(requestBody)
                .addHeader("X-API-Key", Secrets.API_KEY)
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



    suspend fun updateRow(id: Int, updates: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        try {
            val bodyData = updates.toMutableMap().apply { put("id", id) }
            val requestBody = gson.toJson(bodyData).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${Secrets.API_URL}vejmankassen/update")
                .post(requestBody)
                .addHeader("X-API-Key", Secrets.API_KEY)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            return@withContext response.isSuccessful

        } catch (e: Exception) {
            Log.e("ApiHelper", "Update Exception: ${e.message}", e)
            return@withContext false
        }
    }

    suspend fun sendLoginRequest(email: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("email" to email))
            Log.d("ApiHelper", "Sending login to: ${Secrets.API_URL}auth/request-link with payload: $json")

            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${Secrets.API_URL}auth/request-link")
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

    suspend fun pollAuth(token: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("token" to token))
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${Secrets.API_URL}auth/check")
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

                if (authorized && !keyFromServer.isNullOrBlank()) {
                    Log.d("ApiHelper", "Received API key from server: $keyFromServer")
                    return@withContext keyFromServer
                }

                Log.d("ApiHelper", "Token not authorized or key missing")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e("ApiHelper", "Exception during pollAuth: ${e.message}", e)
            return@withContext null
        }
    }


}
