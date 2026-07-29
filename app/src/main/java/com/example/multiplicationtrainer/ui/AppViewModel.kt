package com.example.multiplicationtrainer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multiplicationtrainer.data.ApiClient
import com.example.multiplicationtrainer.data.AuthRepository
import com.example.multiplicationtrainer.data.ChallengeRepository
import com.example.multiplicationtrainer.data.LeaderboardRepository
import com.example.multiplicationtrainer.data.ProfileRepository
import com.example.multiplicationtrainer.data.SessionStore
import com.example.multiplicationtrainer.data.UpdateRepository
import com.example.multiplicationtrainer.domain.ChallengeInfo
import com.example.multiplicationtrainer.domain.LeaderboardEntry
import com.example.multiplicationtrainer.domain.ReleaseInfo
import com.example.multiplicationtrainer.domain.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class AppUiState(
    val screen: AppScreen = AppScreen.Splash,
    val isLoading: Boolean = true,
    val user: UserProfile? = null,
    val authError: String? = null,
    val profileDraft: String = "",
    val profileError: String? = null,
    val profileMessage: String? = null,
    val challenges: List<ChallengeInfo> = emptyList(),
    val releaseInfo: ReleaseInfo = ReleaseInfo(available = false),
    val updateStatus: String = "",
    val leaderboardChallenge: String = "multiplication",
    val leaderboardVariant: Int = 10,
    val leaderboardPeriod: String = "day",
    val leaderboardEntries: List<LeaderboardEntry> = emptyList(),
    val leaderboardLoading: Boolean = false,
    val leaderboardError: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val api = ApiClient(sessionStore)
    val authRepository = AuthRepository(api, sessionStore)
    val profileRepository = ProfileRepository(api)
    val challengeRepository = ChallengeRepository(api)
    val leaderboardRepository = LeaderboardRepository(api)
    val updateRepository = UpdateRepository(api)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        bootstrap()
        startUpdatePolling()
    }

    private fun startUpdatePolling() {
        viewModelScope.launch {
            while (coroutineContext.isActive) {
                runCatching { updateRepository.checkLatest() }
                    .onSuccess { release ->
                        _uiState.value = _uiState.value.copy(releaseInfo = release)
                    }
                delay(UPDATE_CHECK_INTERVAL_MS)
            }
        }
    }

    fun bootstrap() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, authError = null)
            val session = runCatching { authRepository.refresh() }.getOrNull()
            if (session != null) {
                enterHub(session.user)
            } else if (authRepository.hasSession()) {
                authRepository.logout()
                _uiState.value = AppUiState(screen = AppScreen.Login, isLoading = false)
            } else {
                _uiState.value = AppUiState(screen = AppScreen.Login, isLoading = false)
            }
        }
    }

    fun login(login: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, authError = null)
            runCatching { authRepository.login(login, password) }
                .onSuccess { session -> enterHub(session.user) }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        authError = error.message ?: "Ошибка входа",
                    )
                }
        }
    }

    fun register(login: String, password: String, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, authError = null)
            runCatching { authRepository.register(login, password, displayName) }
                .onSuccess { session -> enterHub(session.user) }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        authError = error.message ?: "Ошибка регистрации",
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AppUiState(screen = AppScreen.Login, isLoading = false)
        }
    }

    fun navigate(screen: AppScreen) {
        _uiState.value = _uiState.value.copy(screen = screen, authError = null, profileError = null)
        if (screen == AppScreen.Profile) {
            _uiState.value = _uiState.value.copy(profileDraft = _uiState.value.user?.displayName.orEmpty())
        }
        if (screen == AppScreen.Leaderboards) {
            loadLeaderboard()
        }
    }

    fun saveProfile() {
        val draft = _uiState.value.profileDraft.trim()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(profileError = null, profileMessage = null, isLoading = true)
            runCatching { profileRepository.updateDisplayName(draft) }
                .onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(
                        user = profile,
                        profileDraft = profile.displayName,
                        profileMessage = "Имя обновлено",
                        isLoading = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        profileError = error.message,
                        isLoading = false,
                    )
                }
        }
    }

    fun updateProfileDraft(value: String) {
        _uiState.value = _uiState.value.copy(profileDraft = value.take(20), profileError = null)
    }

    fun loadChallenges() {
        viewModelScope.launch {
            runCatching { challengeRepository.listChallenges() }
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(challenges = list)
                }
        }
    }

    fun checkUpdates() {
        viewModelScope.launch {
            runCatching { updateRepository.checkLatest() }
                .onSuccess { release ->
                    _uiState.value = _uiState.value.copy(releaseInfo = release)
                }
        }
    }

    companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 5 * 60 * 1000L
    }

    fun setLeaderboardFilters(challenge: String? = null, variant: Int? = null, period: String? = null) {
        val current = _uiState.value
        _uiState.value = current.copy(
            leaderboardChallenge = challenge ?: current.leaderboardChallenge,
            leaderboardVariant = variant ?: current.leaderboardVariant,
            leaderboardPeriod = period ?: current.leaderboardPeriod,
        )
        loadLeaderboard()
    }

    fun loadLeaderboard() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.value = snapshot.copy(leaderboardLoading = true, leaderboardError = null)
            runCatching {
                leaderboardRepository.fetch(
                    snapshot.leaderboardChallenge,
                    snapshot.leaderboardVariant,
                    snapshot.leaderboardPeriod,
                )
            }.onSuccess { entries ->
                _uiState.value = _uiState.value.copy(
                    leaderboardEntries = entries,
                    leaderboardLoading = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    leaderboardEntries = emptyList(),
                    leaderboardLoading = false,
                    leaderboardError = error.message,
                )
            }
        }
    }

    fun setUpdateStatus(message: String) {
        _uiState.value = _uiState.value.copy(updateStatus = message)
    }

    private suspend fun enterHub(user: UserProfile) {
        val previousRelease = _uiState.value.releaseInfo
        _uiState.value = AppUiState(
            screen = AppScreen.Hub,
            isLoading = false,
            user = user,
            profileDraft = user.displayName,
            releaseInfo = previousRelease,
        )
        loadChallenges()
        checkUpdates()
    }
}
