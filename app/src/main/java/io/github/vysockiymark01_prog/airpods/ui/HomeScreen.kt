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
import androidx.compose.foundation.layout.size
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.vysockiymark01_prog.airpods.MainViewModel
import io.github.vysockiymark01_prog.airpods.ble.AirPodsModel
import io.github.vysockiymark01_prog.airpods.ble.AirPodsStatus
import io.github.vysockiymark01_prog.airpods.ble.BatteryLevel
import io.github.vysockiymark01_prog.airpods.ble.NoiseControlMode
import android.os.SystemClock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AirPods для Android") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Text("📈")
                    }
                    IconButton(onClick = onOpenEqualizer) {
                        Text("🎚️")
                    }
                },
            )
        },
    ) { padding ->
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
                ModelPicker(
                    model = status.model,
                    manualOverride = uiState.manualModelOverride,
                    onPick = viewModel::setManualModelOverride,
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

                if (!status.leftInCase && !status.rightInCase) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Наушники сейчас не в кейсе — его заряд сам кейс передаёт им только " +
                            "через контакты внутри, поэтому цифра может быть устаревшей до тех пор, " +
                            "пока наушники снова не окажутся в кейсе",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
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

            Spacer(Modifier.height(24.dp))
            ReportProblemButton()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(model: AirPodsModel, manualOverride: AirPodsModel?, onPick: (AirPodsModel?) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { expanded = true },
        ) {
            AirPodsModelIcon(model = model, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(text = model.displayName, style = MaterialTheme.typography.headlineLarge)
            Text(
                text = if (manualOverride != null) "Модель выбрана вручную · нажмите, чтобы изменить" else "Определено автоматически · нажмите, чтобы изменить",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (manualOverride == null) "✓ Авто (определять самостоятельно)" else "Авто (определять самостоятельно)") },
                onClick = {
                    onPick(null)
                    expanded = false
                },
            )
            AirPodsModel.entries.filter { it != AirPodsModel.UNKNOWN }.forEach { entry ->
                DropdownMenuItem(
                    leadingIcon = { AirPodsModelIcon(model = entry, modifier = Modifier.size(28.dp)) },
                    text = { Text(if (manualOverride == entry) "✓ ${entry.displayName}" else entry.displayName) },
                    onClick = {
                        onPick(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Required in-app reporting path for the Play Console "Child Safety Standards" declaration —
 * even though this app has no user-to-user communication, Google requires a way to report
 * problems without leaving the app. See docs/privacy.html "Child Safety Standards" section.
 */
@Composable
private fun ReportProblemButton() {
    val context = LocalContext.current
    TextButton(onClick = {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:vysockiymark01@gmail.com")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "AirPods для Android — сообщение о проблеме")
        }
        runCatching { context.startActivity(intent) }
    }) {
        Text("✉️ Сообщить о проблеме")
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
