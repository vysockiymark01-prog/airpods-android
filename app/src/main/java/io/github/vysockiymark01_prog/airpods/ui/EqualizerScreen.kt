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
 * see [io.github.vysockiymark01_prog.airpods.audio.SystemEqualizerController] for what this
 * actually does and, honestly, what it doesn't (it is not Apple's Adaptive Audio).
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
        if (!uiState.available && !uiState.bassBoostAvailable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Системный эквалайзер недоступен на этом устройстве",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Прошивка вашего телефона не разрешает приложениям создавать общесистемные " +
                        "звуковые эффекты — это ограничение производителя устройства, а не этого " +
                        "приложения, и обойти его нельзя.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Действует на весь звук телефона (не только в этом приложении) и продолжает " +
                    "работать, пока приложение хотя бы раз открывалось после перезагрузки — " +
                    "компенсирует тусклый звук AirPods без фирменной обработки Apple. Это " +
                    "программный эквалайзер уровня Android, а не персонализированный звук с " +
                    "чипа наушников — стоит воспринимать его именно так.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
            if (uiState.bassBoostAvailable) {
                Text("Бас-буст", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = uiState.state.bassBoostStrength.toFloat(),
                    onValueChange = { viewModel.setBassBoost(it.toInt()) },
                    valueRange = 0f..1000f,
                )
            } else {
                Text(
                    "Бас-буст недоступен на этом устройстве (эквалайзер по полосам ниже всё равно работает)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            Spacer(Modifier.height(16.dp))
            if (uiState.available) {
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
            } else {
                Text(
                    "Эквалайзер по полосам недоступен на этом устройстве (бас-буст выше всё равно работает)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "${hz / 1000} кГц" else "$hz Гц"
