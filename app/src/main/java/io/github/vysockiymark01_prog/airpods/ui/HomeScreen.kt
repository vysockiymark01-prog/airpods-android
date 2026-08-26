package io.github.vysockiymark01_prog.airpods.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import io.github.vysockiymark01_prog.airpods.MainViewModel
import io.github.vysockiymark01_prog.airpods.ble.AirPodsStatus
import io.github.vysockiymark01_prog.airpods.ble.BatteryLevel
import io.github.vysockiymark01_prog.airpods.ble.NoiseControlMode
import android.os.SystemClock

@Composable
fun HomeScreen(viewModel: MainViewModel, onRequestPermissions: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            val status = uiState.status
            if (status == null) {
                EmptyState(onRequestPermissions)
            } else {
                Text(
                    text = status.model.displayName,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = staleness(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BatteryIndicator("Левый", status.leftBattery, status.leftCharging)
                    BatteryIndicator("Правый", status.rightBattery, status.rightCharging)
                    BatteryIndicator("Кейс", status.caseBattery, status.caseCharging)
                }

                Spacer(Modifier.height(40.dp))

                if (status.model.supportsAnc) {
                    NoiseControlSelector(
                        sending = uiState.sendingCommand,
                        onSelect = viewModel::requestNoiseControlMode,
                    )
                    AnimatedVisibility(visible = uiState.lastAncCommandFailed, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            text = "Не удалось отправить команду — проверьте, что наушники подключены как аудио-устройство",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                } else {
                    Text(
                        text = "Переключение шумоподавления недоступно для этой модели",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onRequestPermissions: () -> Unit) {
    Text("Наушники не найдены", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "Убедитесь, что Bluetooth включён, а разрешение на поиск устройств — предоставлено",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRequestPermissions) { Text("Предоставить разрешения") }
}

@Composable
private fun NoiseControlSelector(sending: Boolean, onSelect: (NoiseControlMode) -> Unit) {
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(NoiseControlMode.ANC) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = listOf(
            NoiseControlMode.OFF to "Выкл",
            NoiseControlMode.TRANSPARENCY to "Прозрачность",
            NoiseControlMode.ANC to "Шумодав",
            NoiseControlMode.ADAPTIVE to "Авто",
        )
        options.forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                enabled = !sending,
                onClick = {
                    selected = mode
                    onSelect(mode)
                },
                label = { Text(label) },
            )
        }
    }
}

private fun staleness(status: AirPodsStatus): String {
    val ageMs = SystemClock.elapsedRealtime() - status.observedAtElapsedRealtimeMs
    val ageMin = ageMs / 60000
    return when {
        ageMin < 1 -> "Обновлено только что"
        ageMin == 1L -> "Обновлено 1 мин назад"
        else -> "Обновлено $ageMin мин назад"
    }
}
