package com.example.multiplicationtrainer.ui.spelling

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multiplicationtrainer.data.ApiClient
import com.example.multiplicationtrainer.data.RunRepository
import com.example.multiplicationtrainer.data.SessionStore
import com.example.multiplicationtrainer.data.SpellingContentRepository
import com.example.multiplicationtrainer.model.Feedback
import com.example.multiplicationtrainer.model.GamePhase
import com.example.multiplicationtrainer.model.GameResult
import com.example.multiplicationtrainer.model.SpellingCatalog
import com.example.multiplicationtrainer.model.SpellingWord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class SpellingUiState(
    val variant: Int = 10,
    val grade: Int? = null,
    val runId: Long = 0L,
    val words: List<SpellingWord> = emptyList(),
    val mainIndex: Int = 0,
    val currentWord: SpellingWord? = null,
    val typedAnswer: String = "",
    val phase: GamePhase = GamePhase.MAIN,
    val feedback: Feedback = Feedback.NONE,
    val revealedAnswer: String? = null,
    val inputEnabled: Boolean = true,
    val accumulatedTimeMillis: Long = 0L,
    val runningQuestionStartedAt: Long? = null,
    val mainTimeMillis: Long = 0L,
    val errors: Int = 0,
    val attempts: Int = 0,
    val retrySolved: Int = 0,
    val retryInitialCount: Int = 0,
    val finalResult: GameResult? = null,
    val syncStatus: String = "",
    val isStarting: Boolean = false,
    val startError: String? = null,
)

class SpellingViewModel(application: Application) : AndroidViewModel(application) {
    private val contentRepository = SpellingContentRepository(application)
    private val runRepository = RunRepository(ApiClient(SessionStore(application)))
    private val retryQueue = ArrayDeque<SpellingWord>()
    private var feedbackJob: Job? = null

    private val _uiState = MutableStateFlow(SpellingUiState())
    val uiState: StateFlow<SpellingUiState> = _uiState.asStateFlow()

    fun prepare(variant: Int, grade: Int? = null) {
        _uiState.value = SpellingUiState(variant = variant, grade = grade)
    }

    fun setGrade(grade: Int?) {
        _uiState.value = _uiState.value.copy(grade = grade)
    }

    fun startGame() {
        val variant = _uiState.value.variant
        val grade = _uiState.value.grade
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStarting = true, startError = null)
            runCatching { runRepository.startRun("spelling_ru", variant) }
                .onSuccess { run ->
                    feedbackJob?.cancel()
                    retryQueue.clear()
                    val words = contentRepository.generateSession(variant, grade)
                    val now = SystemClock.elapsedRealtime()
                    _uiState.value = SpellingUiState(
                        variant = variant,
                        grade = grade,
                        runId = run.runId,
                        words = words,
                        currentWord = words.first(),
                        runningQuestionStartedAt = now,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isStarting = false,
                        startError = error.message ?: "Не удалось начать игру",
                    )
                }
        }
    }

    fun updateAnswer(value: String) {
        val state = _uiState.value
        if (!state.inputEnabled || state.currentWord == null) return
        _uiState.value = state.copy(typedAnswer = value.take(24))
    }

    fun submitCurrentAnswer() {
        val state = _uiState.value
        val word = state.currentWord ?: return
        if (!state.inputEnabled || state.typedAnswer.isBlank()) return

        val now = SystemClock.elapsedRealtime()
        val responseTime = state.runningQuestionStartedAt?.let { now - it } ?: 0L
        val correct = SpellingCatalog.isCorrect(word.word, state.typedAnswer)
        if (!correct) retryQueue.addLast(word)

        _uiState.value = state.copy(
            inputEnabled = false,
            feedback = if (correct) Feedback.CORRECT else Feedback.WRONG,
            revealedAnswer = if (correct) null else word.word,
            accumulatedTimeMillis = state.accumulatedTimeMillis + responseTime,
            runningQuestionStartedAt = null,
            errors = state.errors + if (correct) 0 else 1,
            attempts = state.attempts + 1,
        )

        feedbackJob = viewModelScope.launch {
            delay(if (correct) 200L else 1400L)
            advance(correct)
        }
    }

    private fun advance(wasCorrect: Boolean) {
        val state = _uiState.value
        when (state.phase) {
            GamePhase.MAIN -> {
                val nextIndex = state.mainIndex + 1
                if (nextIndex < state.words.size) {
                    beginNextWord(state.copy(mainIndex = nextIndex), state.words[nextIndex])
                } else {
                    val mainTime = state.accumulatedTimeMillis
                    if (retryQueue.isEmpty()) {
                        finishGame(state.copy(mainTimeMillis = mainTime))
                    } else {
                        beginNextWord(
                            state.copy(
                                phase = GamePhase.RETRY,
                                mainTimeMillis = mainTime,
                                retryInitialCount = retryQueue.size,
                                retrySolved = 0,
                            ),
                            retryQueue.removeFirst(),
                        )
                    }
                }
            }

            GamePhase.RETRY -> {
                val solved = state.retrySolved + if (wasCorrect) 1 else 0
                if (retryQueue.isEmpty()) {
                    finishGame(state.copy(retrySolved = solved))
                } else {
                    beginNextWord(state.copy(retrySolved = solved), retryQueue.removeFirst())
                }
            }
        }
    }

    private fun beginNextWord(base: SpellingUiState, word: SpellingWord) {
        _uiState.value = base.copy(
            currentWord = word,
            typedAnswer = "",
            feedback = Feedback.NONE,
            revealedAnswer = null,
            inputEnabled = true,
            runningQuestionStartedAt = SystemClock.elapsedRealtime(),
        )
    }

    private fun finishGame(state: SpellingUiState) {
        val result = GameResult(
            challengeCode = "spelling_ru",
            variant = state.variant,
            runId = state.runId,
            mainTimeMillis = if (state.mainTimeMillis > 0L) state.mainTimeMillis else state.accumulatedTimeMillis,
            totalTimeMillis = state.accumulatedTimeMillis,
            errors = state.errors,
            attempts = state.attempts,
        )

        _uiState.value = state.copy(
            currentWord = null,
            typedAnswer = "",
            feedback = Feedback.NONE,
            inputEnabled = false,
            runningQuestionStartedAt = null,
            finalResult = result,
            syncStatus = "Сохраняю результат...",
        )

        viewModelScope.launch {
            runCatching {
                runRepository.finishRun(
                    runId = result.runId,
                    mainTime = result.mainTimeMillis,
                    totalTime = result.totalTimeMillis,
                    errors = result.errors,
                    attempts = result.attempts,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(syncStatus = "Результат сохранён")
            }.onFailure {
                _uiState.value = _uiState.value.copy(syncStatus = "Не удалось сохранить результат")
            }
        }
    }

    fun reset() {
        feedbackJob?.cancel()
        retryQueue.clear()
        _uiState.value = SpellingUiState(
            variant = _uiState.value.variant,
            grade = _uiState.value.grade,
        )
    }
}
