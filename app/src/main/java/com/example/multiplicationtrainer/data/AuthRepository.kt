package com.example.multiplicationtrainer.data

import com.example.multiplicationtrainer.domain.AuthSession
import com.example.multiplicationtrainer.domain.UserProfile
import org.json.JSONObject

class AuthRepository(
    private val api: ApiClient,
    private val sessionStore: SessionStore,
) {
    fun hasSession(): Boolean = sessionStore.hasSession()

    suspend fun register(login: String, password: String, displayName: String): AuthSession {
        val json = api.post(
            "/auth/register",
            JSONObject()
                .put("login", login)
                .put("password", password)
                .put("displayName", displayName),
        )
        return saveAndParse(json)
    }

    suspend fun login(login: String, password: String): AuthSession {
        val json = api.post(
            "/auth/login",
            JSONObject()
                .put("login", login)
                .put("password", password),
        )
        return saveAndParse(json)
    }

    suspend fun refresh(): AuthSession? {
        val refresh = sessionStore.getRefreshToken() ?: return null
        return runCatching {
            val json = api.post("/auth/refresh", JSONObject().put("refreshToken", refresh))
            saveAndParse(json)
        }.getOrNull()
    }

    suspend fun logout() {
        val refresh = sessionStore.getRefreshToken()
        if (refresh != null) {
            runCatching { api.post("/auth/logout", JSONObject().put("refreshToken", refresh)) }
        }
        sessionStore.clear()
    }

    private fun saveAndParse(json: JSONObject): AuthSession {
        val userJson = json.getJSONObject("user")
        sessionStore.saveSession(
            json.getString("accessToken"),
            json.getString("refreshToken"),
        )
        return AuthSession(
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            user = UserProfile(
                id = userJson.getInt("id"),
                login = userJson.getString("login"),
                displayName = userJson.getString("displayName"),
            ),
        )
    }
}
