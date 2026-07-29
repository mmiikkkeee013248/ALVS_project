package com.example.multiplicationtrainer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multiplicationtrainer.model.Feedback
import com.example.multiplicationtrainer.model.GamePhase
import com.example.multiplicationtrainer.model.GameResult
import com.example.multiplicationtrainer.model.SpellingCatalog
import com.example.multiplicationtrainer.ui.components.SpellingSlotInput
import com.example.multiplicationtrainer.ui.components.WordPictureCard
import com.example.multiplicationtrainer.ui.multiplication.MultiplicationUiState
import com.example.multiplicationtrainer.ui.multiplication.MultiplicationViewModel
import com.example.multiplicationtrainer.ui.spelling.SpellingUiState
import com.example.multiplicationtrainer.ui.spelling.SpellingViewModel
import com.example.multiplicationtrainer.update.UpdateInstaller
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrainerApp(
    appViewModel: AppViewModel = viewModel(),
    multiplicationViewModel: MultiplicationViewModel = viewModel(),
    spellingViewModel: SpellingViewModel = viewModel(),
) {
    val appState by appViewModel.uiState.collectAsState()
    val multState by multiplicationViewModel.uiState.collectAsState()
    val spellState by spellingViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateInstaller = remember { UpdateInstaller(context) }

    var multVariant by remember { mutableIntStateOf(10) }
    var spellVariant by remember { mutableIntStateOf(10) }
    var spellGrade by remember { mutableStateOf<Int?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    BackHandler(
        enabled = appState.screen !in setOf(AppScreen.Splash, AppScreen.Login, AppScreen.Hub),
    ) {
        when (appState.screen) {
            AppScreen.Register -> appViewModel.navigate(AppScreen.Login)
            AppScreen.Profile,
            AppScreen.Challenges,
            AppScreen.Leaderboards,
            AppScreen.Update,
            AppScreen.MultiplicationSetup,
            AppScreen.SpellingSetup,
            -> appViewModel.navigate(AppScreen.Hub)
            AppScreen.MultiplicationGame -> multiplicationViewModel.reset()
            AppScreen.SpellingGame -> spellingViewModel.reset()
            AppScreen.MultiplicationResult -> multiplicationViewModel.reset()
            AppScreen.SpellingResult -> spellingViewModel.reset()
            else -> appViewModel.navigate(AppScreen.Hub)
        }
    }

    when (appState.screen) {
        AppScreen.Splash -> SplashScreen()

        AppScreen.Login -> LoginScreen(
            isLoading = appState.isLoading,
            error = appState.authError,
            onLogin = appViewModel::login,
            onGoRegister = { appViewModel.navigate(AppScreen.Register) },
        )

        AppScreen.Register -> RegisterScreen(
            isLoading = appState.isLoading,
            error = appState.authError,
            onRegister = appViewModel::register,
            onBack = { appViewModel.navigate(AppScreen.Login) },
        )

        AppScreen.Hub -> HubScreen(
            displayName = appState.user?.displayName.orEmpty(),
            updateAvailable = appState.releaseInfo.available &&
                appState.releaseInfo.versionCode > com.example.multiplicationtrainer.BuildConfig.VERSION_CODE,
            updateLabel = appState.releaseInfo.versionName,
            onProfile = { appViewModel.navigate(AppScreen.Profile) },
            onChallenges = { appViewModel.navigate(AppScreen.Challenges) },
            onLeaderboards = { appViewModel.navigate(AppScreen.Leaderboards) },
            onUpdate = { appViewModel.navigate(AppScreen.Update) },
            onLogout = appViewModel::logout,
        )

        AppScreen.Profile -> ProfileScreen(
            login = appState.user?.login.orEmpty(),
            draft = appState.profileDraft,
            error = appState.profileError,
            message = appState.profileMessage,
            onDraftChange = appViewModel::updateProfileDraft,
            onSave = appViewModel::saveProfile,
            onBack = { appViewModel.navigate(AppScreen.Hub) },
        )

        AppScreen.Challenges -> ChallengeListScreen(
            challenges = appState.challenges,
            onOpen = { code ->
                if (code == "multiplication") {
                    multiplicationViewModel.prepare(multVariant)
                    appViewModel.navigate(AppScreen.MultiplicationSetup)
                } else {
                    spellingViewModel.prepare(spellVariant, spellGrade)
                    appViewModel.navigate(AppScreen.SpellingSetup)
                }
            },
            onBack = { appViewModel.navigate(AppScreen.Hub) },
        )

        AppScreen.Leaderboards -> LeaderboardHubScreen(
            challenge = appState.leaderboardChallenge,
            variant = appState.leaderboardVariant,
            period = appState.leaderboardPeriod,
            entries = appState.leaderboardEntries,
            loading = appState.leaderboardLoading,
            error = appState.leaderboardError,
            onChallengeChange = { appViewModel.setLeaderboardFilters(challenge = it) },
            onVariantChange = { appViewModel.setLeaderboardFilters(variant = it) },
            onPeriodChange = { appViewModel.setLeaderboardFilters(period = it) },
            onBack = { appViewModel.navigate(AppScreen.Hub) },
        )

        AppScreen.Update -> UpdateScreen(
            releaseName = appState.releaseInfo.versionName,
            changelog = appState.releaseInfo.changelog,
            status = appState.updateStatus,
            onDownload = {
                scope.launch {
                    appViewModel.setUpdateStatus("Скачиваю обновление...")
                    runCatching {
                        val file = updateInstaller.downloadApk(
                            appState.releaseInfo.apkUrl,
                            appState.releaseInfo.sha256,
                        )
                        updateInstaller.installApk(file)
                        appViewModel.setUpdateStatus("Откройте установщик на экране")
                    }.onFailure { error ->
                        appViewModel.setUpdateStatus(error.message ?: "Ошибка обновления")
                    }
                }
            },
            onBack = { appViewModel.navigate(AppScreen.Hub) },
        )

        AppScreen.MultiplicationSetup -> {
            LaunchedEffect(multState.currentProblem) {
                if (multState.currentProblem != null) {
                    appViewModel.navigate(AppScreen.MultiplicationGame)
                }
            }
            ChallengeSetupScreen(
                title = "Таблица умножения",
                selectedVariant = multVariant,
                onSelectVariant = {
                    multVariant = it
                    multiplicationViewModel.prepare(it)
                },
                onStart = { multiplicationViewModel.startGame() },
                onBack = { appViewModel.navigate(AppScreen.Challenges) },
                error = multState.startError,
            )
        }

        AppScreen.MultiplicationGame -> {
            LaunchedEffect(multState.finalResult) {
                if (multState.finalResult != null) appViewModel.navigate(AppScreen.MultiplicationResult)
            }
            MultiplicationGameScreen(
                state = multState,
                onDigit = multiplicationViewModel::enterDigit,
                onExit = {
                    multiplicationViewModel.reset()
                    appViewModel.navigate(AppScreen.Hub)
                },
            )
        }

        AppScreen.MultiplicationResult -> ResultScreen(
            result = multState.finalResult,
            syncStatus = multState.syncStatus,
            onAgain = {
                multiplicationViewModel.reset()
                multiplicationViewModel.prepare(multVariant)
                appViewModel.navigate(AppScreen.MultiplicationSetup)
            },
            onHome = {
                multiplicationViewModel.reset()
                appViewModel.navigate(AppScreen.Hub)
            },
        )

        AppScreen.SpellingSetup -> {
            LaunchedEffect(spellState.currentWord) {
                if (spellState.currentWord != null) {
                    appViewModel.navigate(AppScreen.SpellingGame)
                }
            }
            SpellingSetupScreen(
                selectedVariant = spellVariant,
                selectedGrade = spellGrade,
                onSelectVariant = {
                    spellVariant = it
                    spellingViewModel.prepare(it, spellGrade)
                },
                onSelectGrade = {
                    spellGrade = it
                    spellingViewModel.setGrade(it)
                },
                onStart = { spellingViewModel.startGame() },
                onBack = { appViewModel.navigate(AppScreen.Challenges) },
                error = spellState.startError,
            )
        }

        AppScreen.SpellingGame -> {
            LaunchedEffect(spellState.finalResult) {
                if (spellState.finalResult != null) appViewModel.navigate(AppScreen.SpellingResult)
            }
            SpellingGameScreen(
                state = spellState,
                onAnswerChange = spellingViewModel::updateAnswer,
                onSubmit = spellingViewModel::submitCurrentAnswer,
                onExit = {
                    spellingViewModel.reset()
                    appViewModel.navigate(AppScreen.Hub)
                },
            )
        }

        AppScreen.SpellingResult -> ResultScreen(
            result = spellState.finalResult,
            syncStatus = spellState.syncStatus,
            onAgain = {
                spellingViewModel.reset()
                spellingViewModel.prepare(spellVariant, spellGrade)
                appViewModel.navigate(AppScreen.SpellingSetup)
            },
            onHome = {
                spellingViewModel.reset()
                appViewModel.navigate(AppScreen.Hub)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiplicationGameScreen(
    state: MultiplicationUiState,
    onDigit: (Int) -> Unit,
    onExit: () -> Unit,
) {
    var displayedTime by remember { mutableLongStateOf(state.accumulatedTimeMillis) }
    LaunchedEffect(state.accumulatedTimeMillis, state.runningQuestionStartedAt) {
        while (true) {
            displayedTime = state.accumulatedTimeMillis +
                (state.runningQuestionStartedAt?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L)
            delay(40L)
        }
    }

    val background = when (state.feedback) {
        Feedback.CORRECT -> Color(0xFFD8F5DE)
        Feedback.WRONG -> Color(0xFFFFDAD6)
        Feedback.NONE -> MaterialTheme.colorScheme.background
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.phase == GamePhase.MAIN) "Основной круг" else "Повтор ошибок")
                },
                actions = { TextButton(onClick = onExit) { Text("Выйти") } },
            )
        },
        containerColor = background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(formatTime(displayedTime), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(0.5f))
            Text(
                state.currentProblem?.let { "${it.left} × ${it.right}" }.orEmpty(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(16.dp))
            AnswerBoxes(answer = state.typedAnswer, feedback = state.feedback)
            if (state.feedback == Feedback.WRONG && state.correctAnswer != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Правильно: ${state.correctAnswer}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB71C1C),
                )
            }
            Spacer(Modifier.weight(0.5f))
            NumericKeypad(enabled = state.inputEnabled, onDigit = onDigit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpellingGameScreen(
    state: SpellingUiState,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onExit: () -> Unit,
) {
    var displayedTime by remember { mutableLongStateOf(state.accumulatedTimeMillis) }
    LaunchedEffect(state.accumulatedTimeMillis, state.runningQuestionStartedAt) {
        while (true) {
            displayedTime = state.accumulatedTimeMillis +
                (state.runningQuestionStartedAt?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L)
            delay(40L)
        }
    }

    val background = when (state.feedback) {
        Feedback.CORRECT -> Color(0xFFD8F5DE)
        Feedback.WRONG -> Color(0xFFFFDAD6)
        Feedback.NONE -> MaterialTheme.colorScheme.background
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.phase == GamePhase.MAIN) "Словарные слова" else "Повтор ошибок") },
                actions = { TextButton(onClick = onExit) { Text("Выйти") } },
            )
        },
        containerColor = background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(formatTime(displayedTime), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            val word = state.currentWord
            if (word != null) {
                WordPictureCard(emoji = word.emoji, imagePath = word.image)
                Spacer(Modifier.height(20.dp))
                SpellingSlotInput(
                    word = word.word,
                    typedAnswer = state.typedAnswer,
                    onAnswerChange = onAnswerChange,
                    onSubmit = onSubmit,
                    enabled = state.inputEnabled,
                    feedback = state.feedback,
                )
            }
            if (state.feedback == Feedback.WRONG && state.revealedAnswer != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Правильно: ${state.revealedAnswer}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB71C1C),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSubmit, enabled = state.inputEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Проверить")
            }
        }
    }
}

@Composable
private fun ResultScreen(
    result: GameResult?,
    syncStatus: String,
    onAgain: () -> Unit,
    onHome: () -> Unit,
) {
    if (result == null) return
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Готово!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        Text("Время: ${formatTime(result.totalTimeMillis)}")
        Text("Ошибки: ${result.errors}")
        Text(syncStatus, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("Ещё раз") }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("В хаб") }
    }
}

@Composable
private fun AnswerBoxes(answer: String, feedback: Feedback = Feedback.NONE) {
    val boxColor = when (feedback) {
        Feedback.CORRECT -> Color(0xFF2E7D32)
        Feedback.WRONG -> Color(0xFFC62828)
        Feedback.NONE -> Color(0xFF424242)
    }
    val textColor = Color.White
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) { index ->
            Card(
                modifier = Modifier.size(width = 70.dp, height = 82.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = boxColor),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        answer.getOrNull(index)?.toString().orEmpty(),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun NumericKeypad(enabled: Boolean, onDigit: (Int) -> Unit) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    Button(
                        onClick = { onDigit(digit) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).height(58.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        ),
                    ) { Text("$digit", fontSize = 24.sp) }
                }
            }
        }
        Row {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onDigit(0) },
                enabled = enabled,
                modifier = Modifier.weight(1f).height(58.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                ),
            ) { Text("0", fontSize = 24.sp) }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun formatTime(millis: Long): String {
    val minutes = millis / 60_000
    val seconds = (millis % 60_000) / 1_000
    val hundredths = (millis % 1_000) / 10
    return if (minutes > 0) "%d:%02d.%02d".format(minutes, seconds, hundredths)
    else "%d.%02d с".format(seconds, hundredths)
}
