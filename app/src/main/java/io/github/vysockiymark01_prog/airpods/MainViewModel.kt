package io.github.vysockiymark01_prog.airpods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vysockiymark01_prog.airpods.ble.AacpController
import io.github.vysockiymark01_prog.airpods.ble.AirPodsScanService
import io.github.vysockiymark01_prog.airpods.ble.AirPodsStatus
import io.github.vysockiymark01_prog.airpods.ble.AutoPauseController
import io.github.vysockiymark01_prog.airpods.ble.NoiseControlMode
import io.github.vysockiymark01_prog.airpods.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val status: AirPodsStatus? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val sendingCommand: Boolean = false,
    val lastAncCommandFailed: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val autoPauseController = AutoPauseController(application)
    private var aacpController: AacpController? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            AirPodsScanService.latestStatus.collect { status ->
                _uiState.value = _uiState.value.copy(status = status)
                status?.let { autoPauseController.onStatusUpdate(it) }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun requestNoiseControlMode(mode: NoiseControlMode) {
        val status = _uiState.value.status ?: return
        val controller = aacpController ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sendingCommand = true, lastAncCommandFailed = false)
            val ok = controller.sendNoiseControlMode(status.model, status.rawModelId, mode)
            _uiState.value = _uiState.value.copy(sendingCommand = false, lastAncCommandFailed = !ok)
        }
    }

    /** Wire up once the paired classic-Bluetooth [android.bluetooth.BluetoothDevice] is known. */
    fun attachDevice(device: android.bluetooth.BluetoothDevice) {
        aacpController?.close()
        aacpController = AacpController(device)
    }

    override fun onCleared() {
        aacpController?.close()
        super.onCleared()
    }
}
