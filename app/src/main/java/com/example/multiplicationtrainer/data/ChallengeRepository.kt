package com.example.multiplicationtrainer.data

import com.example.multiplicationtrainer.domain.ChallengeInfo
import com.example.multiplicationtrainer.domain.RunStart
import org.json.JSONObject

class ChallengeRepository(private val api: ApiClient) {
    suspend fun listChallenges(): List<ChallengeInfo> {
        val json = api.get("/challenges")
        val array = json.getJSONArray("challenges")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val variants = buildList {
                    val variantsArray = item.getJSONArray("variants")
                    for (j in 0 until variantsArray.length()) add(variantsArray.getInt(j))
                }
                add(
                    ChallengeInfo(
                        code = item.getString("code"),
                        title = item.getString("title"),
                        variants = variants,
                        description = item.getString("description"),
                    )
                )
            }
        }
    }
}

class RunRepository(private val api: ApiClient) {
    suspend fun startRun(challengeCode: String, variant: Int): RunStart {
        val json = api.post(
            "/runs/start",
            JSONObject()
                .put("challengeCode", challengeCode)
                .put("variant", variant),
            auth = true,
        )
        return RunStart(
            runId = json.getLong("runId"),
            serverNonce = json.getString("serverNonce"),
            startedAt = json.getLong("startedAt"),
        )
    }

    suspend fun finishRun(
        runId: Long,
        mainTime: Long,
        totalTime: Long,
        errors: Int,
        attempts: Int,
    ) {
        api.post(
            "/runs/finish",
            JSONObject()
                .put("runId", runId)
                .put("mainTime", mainTime)
                .put("totalTime", totalTime)
                .put("errors", errors)
                .put("attempts", attempts),
            auth = true,
        )
    }
}
