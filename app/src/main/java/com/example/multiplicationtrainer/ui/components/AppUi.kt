package com.example.multiplicationtrainer.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multiplicationtrainer.model.Feedback

@Composable
fun AppGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun FormScaffold(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppGradientBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

enum class HubCardStyle {
    Filled,
    Surface,
    Outlined,
}

@Composable
fun HubMenuCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    style: HubCardStyle = HubCardStyle.Surface,
) {
    when (style) {
        HubCardStyle.Filled -> {
            Card(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                HubMenuCardContent(emoji, title, subtitle, subtitleMuted = false)
            }
        }

        HubCardStyle.Outlined -> {
            OutlinedCard(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                HubMenuCardContent(emoji, title, subtitle, subtitleMuted = true)
            }
        }

        HubCardStyle.Surface -> {
            Card(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                HubMenuCardContent(emoji, title, subtitle, subtitleMuted = true)
            }
        }
    }
}

@Composable
private fun HubMenuCardContent(
    emoji: String,
    title: String,
    subtitle: String,
    subtitleMuted: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = emoji, fontSize = 34.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (subtitleMuted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                },
            )
        }
    }
}

@Composable
fun WordPictureCard(
    emoji: String,
    imagePath: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageBitmap = remember(imagePath) {
        if (!imagePath.isNullOrEmpty()) {
            val assetPath = if (imagePath.startsWith("spelling/")) imagePath else "spelling/$imagePath"
            runCatching {
                context.assets.open(assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        } else {
            null
        }
    }

    Card(
        modifier = modifier.size(width = 280.dp, height = 220.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(2.dp, Color(0xFFE8D8B8)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(text = emoji, fontSize = 96.sp)
            }
        }
    }
}

@Composable
fun SpellingSlotInput(
    word: String,
    typedAnswer: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    feedback: Feedback,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val cleanWord = word.trim()
    val targetLength = cleanWord.length

    LaunchedEffect(enabled, word) {
        if (enabled) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { focusRequester.requestFocus() },
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = typedAnswer,
            onValueChange = { input ->
                if (input.length <= targetLength) {
                    onAnswerChange(input)
                }
            },
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until targetLength) {
                val isFirstHint = (i == 0 && typedAnswer.isEmpty())
                val isFilled = i < typedAnswer.length || isFirstHint
                val charDisplay = when {
                    i < typedAnswer.length -> typedAnswer[i].uppercaseChar().toString()
                    isFirstHint -> cleanWord.first().uppercaseChar().toString()
                    else -> "_"
                }
                val isActive = enabled && (i == typedAnswer.length || (i == 0 && typedAnswer.isEmpty()))

                val containerColor = when (feedback) {
                    Feedback.CORRECT -> Color(0xFFC8E6C9)
                    Feedback.WRONG -> Color(0xFFFFCDD2)
                    Feedback.NONE -> when {
                        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        isFilled -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    }
                }

                val borderColor = when (feedback) {
                    Feedback.CORRECT -> Color(0xFF2E7D32)
                    Feedback.WRONG -> Color(0xFFC62828)
                    Feedback.NONE -> when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isFilled -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                }

                val textColor = when (feedback) {
                    Feedback.CORRECT -> Color(0xFF1B5E20)
                    Feedback.WRONG -> Color(0xFFB71C1C)
                    Feedback.NONE -> when {
                        isFirstHint -> MaterialTheme.colorScheme.primary
                        isFilled -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                }

                Card(
                    modifier = Modifier.size(width = 46.dp, height = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    border = BorderStroke(if (isActive) 2.5.dp else 1.5.dp, borderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 1.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = charDisplay,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}
