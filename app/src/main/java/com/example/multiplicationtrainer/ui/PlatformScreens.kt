package com.example.multiplicationtrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.multiplicationtrainer.BuildConfig
import com.example.multiplicationtrainer.R
import com.example.multiplicationtrainer.domain.ChallengeInfo
import com.example.multiplicationtrainer.ui.components.AppGradientBackground
import com.example.multiplicationtrainer.ui.components.FormScaffold
import com.example.multiplicationtrainer.ui.components.HubCardStyle
import com.example.multiplicationtrainer.ui.components.HubMenuCard

@Composable
fun SplashScreen() {
    AppGradientBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Школьные испытания на время",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Загрузка...")
        }
    }
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onGoRegister: () -> Unit,
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    FormScaffold(title = "Вход") {
        OutlinedTextField(login, { login = it }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onLogin(login, password) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Войти") }
        TextButton(onClick = onGoRegister) { Text("Создать аккаунт") }
    }
}

@Composable
fun RegisterScreen(
    isLoading: Boolean,
    error: String?,
    onRegister: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    FormScaffold(title = "Регистрация") {
        OutlinedTextField(login, { login = it }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            displayName,
            { displayName = it },
            label = { Text("Имя в рейтинге") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onRegister(login, password, displayName) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Зарегистрироваться") }
        TextButton(onClick = onBack) { Text("Назад ко входу") }
    }
}

@Composable
fun HubScreen(
    displayName: String,
    updateAvailable: Boolean,
    updateLabel: String,
    onProfile: () -> Unit,
    onChallenges: () -> Unit,
    onLeaderboards: () -> Unit,
    onUpdate: () -> Unit,
    onLogout: () -> Unit,
) {
    AppGradientBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Привет, $displayName",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(24.dp))

                    HubMenuCard(
                        emoji = "🎯",
                        title = "Испытания",
                        subtitle = "Умножение и словарные слова",
                        onClick = onChallenges,
                        style = HubCardStyle.Filled,
                    )
                    Spacer(Modifier.height(12.dp))
                    HubMenuCard(
                        emoji = "🏆",
                        title = "Результаты",
                        subtitle = "Рейтинг за день, неделю и сезон",
                        onClick = onLeaderboards,
                    )
                    Spacer(Modifier.height(12.dp))
                    HubMenuCard(
                        emoji = "👤",
                        title = "Профиль",
                        subtitle = "Имя в таблице лидеров",
                        onClick = onProfile,
                        style = HubCardStyle.Outlined,
                    )
                    if (updateAvailable) {
                        Spacer(Modifier.height(12.dp))
                        HubMenuCard(
                            emoji = "⬇️",
                            title = "Обновить приложение",
                            subtitle = "Доступна новая версия $updateLabel",
                            onClick = onUpdate,
                            style = HubCardStyle.Filled,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextButton(onClick = onLogout) { Text("Выйти") }
                    Text(
                        text = "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    login: String,
    draft: String,
    error: String?,
    message: String?,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    FormScaffold(title = "Профиль") {
        Text("Логин: $login")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("Имя в рейтинге") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        if (message != null) Text(message, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

@Composable
fun ChallengeListScreen(
    challenges: List<ChallengeInfo>,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    FormScaffold(title = "Испытания") {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(challenges) { challenge ->
                val emoji = when (challenge.code) {
                    "multiplication" -> "✖️"
                    "spelling_ru" -> "📖"
                    else -> "🎯"
                }
                Card(
                    onClick = { onOpen(challenge.code) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(emoji, fontSize = 32.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(challenge.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                challenge.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("→", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

@Composable
fun ChallengeSetupScreen(
    title: String,
    selectedVariant: Int,
    onSelectVariant: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    error: String?,
) {
    FormScaffold(title = title) {
        Text("Выберите количество заданий")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30).forEach { variant ->
                FilterChip(
                    selected = selectedVariant == variant,
                    onClick = { onSelectVariant(variant) },
                    label = { Text(variant.toString()) },
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Старт") }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

@Composable
fun SpellingSetupScreen(
    selectedVariant: Int,
    selectedGrade: Int?,
    onSelectVariant: (Int) -> Unit,
    onSelectGrade: (Int?) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    error: String?,
) {
    FormScaffold(title = "Словарные слова") {
        Text("Выберите класс", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val grades = listOf(
                null to "Все",
                1 to "1 кл",
                2 to "2 кл",
                3 to "3 кл",
                4 to "4 кл",
            )
            grades.forEach { (grade, label) ->
                FilterChip(
                    selected = selectedGrade == grade,
                    onClick = { onSelectGrade(grade) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Выберите количество заданий", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30).forEach { variant ->
                FilterChip(
                    selected = selectedVariant == variant,
                    onClick = { onSelectVariant(variant) },
                    label = { Text(variant.toString()) },
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Старт") }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

@Composable
fun LeaderboardHubScreen(
    challenge: String,
    variant: Int,
    period: String,
    entries: List<com.example.multiplicationtrainer.domain.LeaderboardEntry>,
    loading: Boolean,
    error: String?,
    onChallengeChange: (String) -> Unit,
    onVariantChange: (Int) -> Unit,
    onPeriodChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    FormScaffold(title = "Результаты") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(challenge == "multiplication", { onChallengeChange("multiplication") }, label = { Text("×") })
            FilterChip(challenge == "spelling_ru", { onChallengeChange("spelling_ru") }, label = { Text("Слова") })
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 20, 30).forEach { v ->
                FilterChip(variant == v, { onVariantChange(v) }, label = { Text("$v") })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("day" to "День", "week" to "Неделя", "season" to "Сезон", "all" to "Всё").forEach { (code, label) ->
                FilterChip(period == code, { onPeriodChange(code) }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            entries.isEmpty() -> Text("Пока нет результатов")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("#${entry.rank} ${entry.displayName}", fontWeight = FontWeight.SemiBold)
                            Text(formatTime(entry.totalTime))
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

@Composable
fun UpdateScreen(
    releaseName: String,
    changelog: String,
    status: String,
    onDownload: () -> Unit,
    onBack: () -> Unit,
) {
    FormScaffold(title = "Обновление") {
        Text("Новая версия: $releaseName", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(changelog.ifBlank { "Исправления и улучшения" })
        Spacer(Modifier.height(12.dp))
        if (status.isNotBlank()) Text(status)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Скачать и установить") }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

private fun formatTime(millis: Long): String {
    val minutes = millis / 60_000
    val seconds = (millis % 60_000) / 1_000
    val hundredths = (millis % 1_000) / 10
    return if (minutes > 0) "%d:%02d.%02d".format(minutes, seconds, hundredths)
    else "%d.%02d с".format(seconds, hundredths)
}
