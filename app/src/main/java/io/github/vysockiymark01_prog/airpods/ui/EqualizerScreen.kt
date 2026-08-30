package io.github.vysockiymark01_prog.airpods.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * The controls below are always shown and always usable — even on a device where the platform
 * refuses a global effect, adjusting anything here still works the moment a compatible app starts
 * playing (see [StatusBanner]), so there's no dead end.
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
                .padding(horizontal = 24.dp),
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

            Spacer(Modifier.height(16.dp))
            Text("Полосы эквалайзера", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.bandCenterFreqHz.size) { index ->
                    val freq = uiState.bandCenterFreqHz.getOrNull(index) ?: 0
                    val level = uiState.state.bandLevelsMb.getOrElse(index) { 0 }
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "${formatFreq(freq)} · ${level / 100} дБ",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { viewModel.setBandLevel(index, it.toInt()) },
                            valueRange = uiState.bandLevelRangeMb.first.toFloat()..
                                uiState.bandLevelRangeMb.last.toFloat(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
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
