package io.github.vysockiymark01_prog.airpods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vysockiymark01_prog.airpods.ble.AacpController
import io.github.vysockiymark01_prog.airpods.ble.AacpSendResult
import io.github.vysockiymark01_prog.airpods.ble.AirPodsModel
import io.github.vysockiymark01_prog.airpods.ble.AirPodsScanService
import io.github.vysockiymark01_prog.airpods.ble.AirPodsStatus
import io.github.vysockiymark01_prog.airpods.ble.ModelOverridePreferences
import io.github.vysockiymark01_prog.airpods.ble.NoiseControlMode
import io.github.vysockiymark01_prog.airpods.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val status: AirPodsStatus? = null,
    val manualModelOverride: AirPodsModel? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val sendingCommand: Boolean = false,
    val lastAncCommandFailed: Boolean = false,
    /** Why the last ANC command failed, in plain Russian — null when it succeeded or nothing has been sent yet. */
    val lastAncErrorMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Pause-on-removal itself now lives in AirPodsScanService (see its doc) so it keeps working
    // with this ViewModel/Activity fully torn down — this class only mirrors status for the UI.
    private var aacpController: AacpController? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var rawStatus: AirPodsStatus? = null
    private var manualOverride: AirPodsModel? = null

    init {
        viewModelScope.launch {
            AirPodsScanService.latestStatus.collect { status ->
                rawStatus = status
                recomputeStatus()
            }
        }
        viewModelScope.launch {
            ModelOverridePreferences.flow(application).collect { override ->
                manualOverride = override
                _uiState.value = _uiState.value.copy(manualModelOverride = override)
                recomputeStatus()
            }
        }
    }

    /**
     * Merges the manually-picked model (if any — see [ModelOverridePreferences]) on top of the
     * auto-detected reading. Battery/ear-detection/charging fields always come from the real BLE
     * broadcast; only [AirPodsStatus.model] and [AirPodsStatus.rawModelId] are substituted, since
     * those are what drive the ANC-support check and the AACP command lookup.
     */
    private fun recomputeStatus() {
        val raw = rawStatus
        val override = manualOverride
        val displayed = if (raw != null && override != null) {
            raw.copy(model = override, rawModelId = override.modelId)
        } else {
            raw
        }
        _uiState.value = _uiState.value.copy(status = displayed)
    }

    /** Pass null to go back to automatic detection. */
    fun setManualModelOverride(model: AirPodsModel?) {
        viewModelScope.launch { ModelOverridePreferences.save(getApplication(), model) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun requestNoiseControlMode(mode: NoiseControlMode) {
        val status = _uiState.value.status ?: return
        val controller = aacpController
        if (controller == null) {
            // Previously this silently did nothing — no error, no feedback, just a button that
            // appeared to do nothing at all. Now it's always visible why.
            _uiState.value = _uiState.value.copy(
                lastAncCommandFailed = true,
                lastAncErrorMessage = "не найдено сопряжённое Bluetooth-устройство с именем, " +
                    "содержащим «AirPods»/«Beats» — проверьте в системных настройках Bluetooth, " +
                    "что наушники сопряжены и подключены как аудиоустройство, затем откройте " +
                    "приложение заново",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sendingCommand = true, lastAncCommandFailed = false, lastAncErrorMessage = null)
            when (val result = controller.sendNoiseControlMode(status.model, status.rawModelId, mode)) {
                is AacpSendResult.Success -> _uiState.value = _uiState.value.copy(sendingCommand = false)
                is AacpSendResult.Failure -> _uiState.value = _uiState.value.copy(
                    sendingCommand = false,
                    lastAncCommandFailed = true,
                    lastAncErrorMessage = result.reason,
                )
            }
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
