package io.github.vysockiymark01_prog.airpods.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.vysockiymark01_prog.airpods.EqualizerViewModel
import io.github.vysockiymark01_prog.airpods.audio.EqualizerPreset

/**
 * Global (system-level) audio boost, separate from the per-earbud ANC control on [HomeScreen] —
 * see [io.github.vysockiymark01_prog.airpods.audio.SystemEqualizerController] and
 * [io.github.vysockiymark01_prog.airpods.audio.PerSessionEqualizerController] for what this
 * actually does and, honestly, what it doesn't (it is not Apple's Adaptive Audio).
 *
 * The whole screen is one scrollable column, and the 10 band sliders are vertical (fader-style,
 * like a classic graphic-EQ app) laid out side by side in their own horizontally-scrollable strip
 * — closer to what people expect from an equalizer than a stack of horizontal sliders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit, viewModel: EqualizerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Эквалайзер") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Действует на весь звук телефона (не только в этом приложении). Это программный " +
                    "эквалайзер уровня Android, а не персонализированный звук с чипа наушников — " +
                    "приближает звучание к более яркой заводской подаче iPhone, но не более того.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            StatusBanner(
                globalAvailable = uiState.available || uiState.bassBoostAvailable,
                activeSessions = uiState.perSessionActiveCount,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Включить усиление звука", style = MaterialTheme.typography.titleMedium)
                Switch(checked = uiState.state.enabled, onCheckedChange = viewModel::setEnabled)
            }

            Spacer(Modifier.height(16.dp))
            Text("Пресеты", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EqualizerPreset.entries.forEach { preset ->
                    AssistChip(onClick = { viewModel.applyPreset(preset) }, label = { Text(preset.label) })
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Бас-буст", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = uiState.state.bassBoostStrength.toFloat(),
                onValueChange = { viewModel.setBassBoost(it.toInt()) },
                valueRange = 0f..1000f,
            )

            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Полосы эквалайзера (10)", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = viewModel::resetToFlat) { Text("Сбросить") }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                uiState.bandCenterFreqHz.forEachIndexed { index, freq ->
                    val level = uiState.state.bandLevelsMb.getOrElse(index) { 0 }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(56.dp),
                    ) {
                        Text(
                            text = formatDb(level),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        VerticalSlider(
                            value = level.toFloat(),
                            onValueChange = { viewModel.setBandLevel(index, it.toInt()) },
                            valueRange = uiState.bandLevelRangeMb.first.toFloat()..
                                uiState.bandLevelRangeMb.last.toFloat(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatFreq(freq),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * A fader-style vertical slider — Compose's `Slider` is horizontal-only, so this rotates one 270°
 * and swaps its measured width/height via a custom [Modifier.layout] so the rotated slider still
 * gets a correctly-sized (tall, narrow) box instead of the unrotated (wide, short) footprint
 * `graphicsLayer` alone would leave behind.
 */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier
            .width(150.dp)
            .height(28.dp)
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    ),
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = -(placeable.width / 2 - placeable.height / 2),
                        y = -(placeable.height / 2 - placeable.width / 2),
                    )
                }
            },
    )
}

@Composable
private fun StatusBanner(globalAvailable: Boolean, activeSessions: Int) {
    val (text, color) = when {
        globalAvailable -> "✓ Работает на уровне всей системы" to MaterialTheme.colorScheme.primary
        activeSessions > 0 -> "✓ Сейчас применяется к воспроизведению (активных приложений: $activeSessions)" to
            MaterialTheme.colorScheme.primary
        else -> (
            "Общесистемный режим недоступен на этом устройстве (ограничение прошивки телефона). " +
                "Настройте всё здесь как обычно — эффект автоматически включится, как только вы " +
                "начнёте воспроизведение в большинстве плееров (YouTube Music, Spotify, штатный " +
                "проигрыватель и т.п.) — просто откройте там музыку. Приложения, которые не " +
                "поддерживают эту системную функцию, звучать иначе не станут — это их " +
                "собственное ограничение, а не этого приложения."
            ) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "${hz / 1000} кГц" else "$hz Гц"

private fun formatDb(levelMb: Int): String {
    val db = levelMb / 100
    return if (db > 0) "+$db дБ" else "$db дБ"
}
