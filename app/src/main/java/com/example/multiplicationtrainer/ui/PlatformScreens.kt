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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multiplicationtrainer.BuildConfig
import com.example.multiplicationtrainer.domain.ChallengeInfo

@Composable
fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Загрузка...")
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

    AuthScaffold(title = "Вход") {
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

    AuthScaffold(title = "Регистрация") {
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
    AuthScaffold(title = "Привет, $displayName") {
        if (updateAvailable) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Доступно обновление", fontWeight = FontWeight.Bold)
                    Text(updateLabel)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text("Обновить")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        HubButton("Испытания", onChallenges)
        Spacer(Modifier.height(10.dp))
        HubButton("Результаты", onLeaderboards)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text("Профиль") }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onLogout) { Text("Выйти") }
        Spacer(Modifier.height(16.dp))
        Text("Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    AuthScaffold(title = "Профиль") {
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
    AuthScaffold(title = "Испытания") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(challenges) { challenge ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(challenge.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(challenge.description)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onOpen(challenge.code) }) { Text("Выбрать режим") }
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
    AuthScaffold(title = title) {
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
    AuthScaffold(title = "Результаты") {
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
    AuthScaffold(title = "Обновление") {
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

@Composable
private fun AuthScaffold(title: String, content: @Composable ColumnScope.() -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun HubButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text(text, fontSize = 18.sp) }
}

private fun formatTime(millis: Long): String {
    val minutes = millis / 60_000
    val seconds = (millis % 60_000) / 1_000
    val hundredths = (millis % 1_000) / 10
    return if (minutes > 0) "%d:%02d.%02d".format(minutes, seconds, hundredths)
    else "%d.%02d с".format(seconds, hundredths)
}
