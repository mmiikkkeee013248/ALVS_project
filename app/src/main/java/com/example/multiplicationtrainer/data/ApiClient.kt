package com.example.multiplicationtrainer.data

import com.example.multiplicationtrainer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(
    private val sessionStore: SessionStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun get(path: String, auth: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}$path")
            .get()
            .apply { if (auth) addAuth(this) }
            .build()
        execute(request)
    }

    suspend fun post(path: String, body: JSONObject, auth: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}$path")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .apply { if (auth) addAuth(this) }
                .build()
            execute(request)
        }

    suspend fun patch(path: String, body: JSONObject, auth: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}$path")
                .patch(body.toString().toRequestBody(JSON_MEDIA))
                .apply { if (auth) addAuth(this) }
                .build()
            execute(request)
        }

    private fun addAuth(builder: Request.Builder) {
        sessionStore.getAccessToken()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.code == 401 && request.header("Authorization") != null) {
                if (refreshAccessToken()) {
                    val retryBuilder = request.newBuilder()
                    sessionStore.getAccessToken()?.let { retryBuilder.header("Authorization", "Bearer $it") }
                    client.newCall(retryBuilder.build()).execute().use { retry ->
                        val retryBody = retry.body.string()
                        if (!retry.isSuccessful) error(parseError(retryBody, retry.code))
                        return JSONObject(retryBody)
                    }
                }
            }
            if (!response.isSuccessful) error(parseError(body, response.code))
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    private fun refreshAccessToken(): Boolean {
        val refresh = sessionStore.getRefreshToken() ?: return false
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/auth/refresh")
            .post(JSONObject().put("refreshToken", refresh).toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                sessionStore.clear()
                return false
            }
            val json = JSONObject(response.body.string())
            sessionStore.saveSession(
                json.getString("accessToken"),
                json.getString("refreshToken"),
            )
            return true
        }
    }

    private fun parseError(body: String, code: Int): String {
        return runCatching { JSONObject(body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "HTTP $code"
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
