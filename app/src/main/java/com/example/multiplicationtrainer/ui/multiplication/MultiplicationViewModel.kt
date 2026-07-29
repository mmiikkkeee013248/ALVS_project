package com.example.multiplicationtrainer.ui.multiplication

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multiplicationtrainer.data.ApiClient
import com.example.multiplicationtrainer.data.RunRepository
import com.example.multiplicationtrainer.data.SessionStore
import com.example.multiplicationtrainer.model.Feedback
import com.example.multiplicationtrainer.model.GamePhase
import com.example.multiplicationtrainer.model.GameResult
import com.example.multiplicationtrainer.model.MultiplicationProblem
import com.example.multiplicationtrainer.model.ProblemGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class MultiplicationUiState(
    val variant: Int = 10,
    val runId: Long = 0L,
    val mainProblems: List<MultiplicationProblem> = emptyList(),
    val mainIndex: Int = 0,
    val currentProblem: MultiplicationProblem? = null,
    val phase: GamePhase = GamePhase.MAIN,
    val typedAnswer: String = "",
    val feedback: Feedback = Feedback.NONE,
    val correctAnswer: Int? = null,
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

class MultiplicationViewModel(application: Application) : AndroidViewModel(application) {
    private val runRepository = RunRepository(ApiClient(SessionStore(application)))
    private val retryQueue = ArrayDeque<MultiplicationProblem>()
    private var feedbackJob: Job? = null

    private val _uiState = MutableStateFlow(MultiplicationUiState())
    val uiState: StateFlow<MultiplicationUiState> = _uiState.asStateFlow()

    fun prepare(variant: Int) {
        _uiState.value = MultiplicationUiState(variant = variant)
    }

    fun startGame() {
        val variant = _uiState.value.variant
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStarting = true, startError = null)
            runCatching { runRepository.startRun("multiplication", variant) }
                .onSuccess { run ->
                    feedbackJob?.cancel()
                    retryQueue.clear()
                    val problems = ProblemGenerator.generate(variant)
                    val now = SystemClock.elapsedRealtime()
                    _uiState.value = MultiplicationUiState(
                        variant = variant,
                        runId = run.runId,
                        mainProblems = problems,
                        currentProblem = problems.first(),
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

    fun enterDigit(digit: Int) {
        val state = _uiState.value
        if (digit !in 0..9 || !state.inputEnabled || state.currentProblem == null) return
        if (state.typedAnswer.length >= 2) return

        val updated = state.typedAnswer + digit
        _uiState.value = state.copy(typedAnswer = updated)
        if (updated.length == 2) submitAnswer(updated.toInt())
    }

    private fun submitAnswer(answer: Int) {
        val state = _uiState.value
        val problem = state.currentProblem ?: return
        if (!state.inputEnabled) return

        val now = SystemClock.elapsedRealtime()
        val responseTime = state.runningQuestionStartedAt?.let { now - it } ?: 0L
        val correct = answer == problem.answer
        if (!correct) retryQueue.addLast(problem)

        _uiState.value = state.copy(
            inputEnabled = false,
            feedback = if (correct) Feedback.CORRECT else Feedback.WRONG,
            correctAnswer = if (correct) null else problem.answer,
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
                if (nextIndex < state.mainProblems.size) {
                    beginNextProblem(state.copy(mainIndex = nextIndex), state.mainProblems[nextIndex])
                } else {
                    val mainTime = state.accumulatedTimeMillis
                    if (retryQueue.isEmpty()) {
                        finishGame(state.copy(mainTimeMillis = mainTime))
                    } else {
                        val next = retryQueue.removeFirst()
                        beginNextProblem(
                            state.copy(
                                phase = GamePhase.RETRY,
                                mainTimeMillis = mainTime,
                                retryInitialCount = retryQueue.size,
                                retrySolved = 0,
                            ),
                            next,
                        )
                    }
                }
            }

            GamePhase.RETRY -> {
                val solved = state.retrySolved + if (wasCorrect) 1 else 0
                if (retryQueue.isEmpty()) {
                    finishGame(state.copy(retrySolved = solved))
                } else {
                    beginNextProblem(state.copy(retrySolved = solved), retryQueue.removeFirst())
                }
            }
        }
    }

    private fun beginNextProblem(baseState: MultiplicationUiState, problem: MultiplicationProblem) {
        _uiState.value = baseState.copy(
            currentProblem = problem,
            typedAnswer = "",
            feedback = Feedback.NONE,
            correctAnswer = null,
            inputEnabled = true,
            runningQuestionStartedAt = SystemClock.elapsedRealtime(),
        )
    }

    private fun finishGame(state: MultiplicationUiState) {
        val result = GameResult(
            challengeCode = "multiplication",
            variant = state.variant,
            runId = state.runId,
            mainTimeMillis = if (state.mainTimeMillis > 0L) state.mainTimeMillis else state.accumulatedTimeMillis,
            totalTimeMillis = state.accumulatedTimeMillis,
            errors = state.errors,
            attempts = state.attempts,
        )

        _uiState.value = state.copy(
            currentProblem = null,
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
        _uiState.value = MultiplicationUiState(variant = _uiState.value.variant)
    }
}
