package com.example.multiplicationtrainer.domain

data class UserProfile(
    val id: Int,
    val login: String,
    val displayName: String,
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: UserProfile,
)

data class ChallengeInfo(
    val code: String,
    val title: String,
    val variants: List<Int>,
    val description: String,
)

data class RunStart(
    val runId: Long,
    val serverNonce: String,
    val startedAt: Long,
)

data class RunFinish(
    val runId: Long,
    val challengeCode: String,
    val variant: Int,
    val mainTime: Long,
    val totalTime: Long,
    val errors: Int,
    val attempts: Int,
)

data class LeaderboardEntry(
    val rank: Int,
    val userId: Int,
    val displayName: String,
    val totalTime: Long,
    val mainTime: Long,
    val errors: Int,
    val finishedAt: Long,
)

data class ReleaseInfo(
    val available: Boolean,
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val size: Long = 0,
    val changelog: String = "",
)
