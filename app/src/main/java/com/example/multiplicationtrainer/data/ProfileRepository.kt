package com.example.multiplicationtrainer.data

import com.example.multiplicationtrainer.domain.UserProfile
import org.json.JSONObject

class ProfileRepository(private val api: ApiClient) {
    suspend fun getProfile(): UserProfile {
        val json = api.get("/me", auth = true)
        return UserProfile(
            id = json.getInt("id"),
            login = json.getString("login"),
            displayName = json.getString("displayName"),
        )
    }

    suspend fun updateDisplayName(displayName: String): UserProfile {
        val json = api.patch("/me/profile", JSONObject().put("displayName", displayName))
        return UserProfile(
            id = json.getInt("id"),
            login = json.getString("login"),
            displayName = json.getString("displayName"),
        )
    }
}
