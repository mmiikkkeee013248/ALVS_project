package com.example.multiplicationtrainer.data

import com.example.multiplicationtrainer.domain.LeaderboardEntry
import com.example.multiplicationtrainer.domain.ReleaseInfo
import org.json.JSONObject

class LeaderboardRepository(private val api: ApiClient) {
    suspend fun fetch(
        challengeCode: String,
        variant: Int,
        period: String,
    ): List<LeaderboardEntry> {
        val json = api.get("/leaderboards/$challengeCode/$variant/$period")
        val array = json.getJSONArray("entries")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    LeaderboardEntry(
                        rank = item.getInt("rank"),
                        userId = item.getInt("userId"),
                        displayName = item.getString("displayName"),
                        totalTime = item.getLong("totalTime"),
                        mainTime = item.getLong("mainTime"),
                        errors = item.getInt("errors"),
                        finishedAt = item.getLong("finishedAt"),
                    )
                )
            }
        }
    }

    suspend fun fetchPersonal(
        challengeCode: String,
        variant: Int,
        period: String,
    ): LeaderboardEntry? {
        val json = api.get("/leaderboards/$challengeCode/$variant/$period/me", auth = true)
        val entry = json.optJSONObject("entry") ?: return null
        return LeaderboardEntry(
            rank = entry.getInt("rank"),
            userId = entry.getInt("userId"),
            displayName = entry.getString("displayName"),
            totalTime = entry.getLong("totalTime"),
            mainTime = entry.getLong("mainTime"),
            errors = entry.getInt("errors"),
            finishedAt = entry.getLong("finishedAt"),
        )
    }
}

class UpdateRepository(private val api: ApiClient) {
    suspend fun checkLatest(): ReleaseInfo {
        val json = api.get("/releases/latest")
        if (!json.optBoolean("available", false)) {
            return ReleaseInfo(available = false)
        }
        return ReleaseInfo(
            available = true,
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            apkUrl = json.getString("apkUrl"),
            sha256 = json.getString("sha256"),
            size = json.getLong("size"),
            changelog = json.optString("changelog", ""),
        )
    }
}
