package io.github.vysockiymark01_prog.airpods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vysockiymark01_prog.airpods.audio.EqualizerPreferences
import io.github.vysockiymark01_prog.airpods.audio.EqualizerPreset
import io.github.vysockiymark01_prog.airpods.audio.EqualizerState
import io.github.vysockiymark01_prog.airpods.audio.PerSessionEqualizerController
import io.github.vysockiymark01_prog.airpods.audio.SystemEqualizerController
import io.github.vysockiymark01_prog.airpods.audio.resampleCurve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Standard AOSP 5-band frequencies — used for the slider labels only when the platform refuses a
 *  global effect and there's no real device to query band metadata from yet (see [perSessionActiveCount]). */
private val FALLBACK_BAND_FREQS_HZ = listOf(60, 230, 910, 3600, 14000)

data class EqualizerUiState(
    val available: Boolean = false,
    val bassBoostAvailable: Boolean = false,
    val perSessionActiveCount: Int = 0,
    val bandCenterFreqHz: List<Int> = emptyList(),
    val bandLevelRangeMb: IntRange = -1500..1500,
    val state: EqualizerState = EqualizerState(),
)

class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        // Safe even if AirPodsScanService already did this — see ensureInitialized() doc.
        SystemEqualizerController.ensureInitialized()
        val bands = SystemEqualizerController.bands()
        _uiState.value = _uiState.value.copy(
            available = SystemEqualizerController.isAvailable,
            bassBoostAvailable = SystemEqualizerController.isBassBoostAvailable,
            bandCenterFreqHz = bands.map { it.centerFreqHz }.ifEmpty { FALLBACK_BAND_FREQS_HZ },
            bandLevelRangeMb = if (bands.isNotEmpty()) {
                bands.first().minLevelMb..bands.first().maxLevelMb
            } else {
                _uiState.value.bandLevelRangeMb
            },
        )
        viewModelScope.launch {
            EqualizerPreferences.flow(application).collect { saved ->
                val bandCount = bands.size.takeIf { it > 0 } ?: saved.bandLevelsMb.size
                val normalized = saved.copy(
                    bandLevelsMb = resampleCurve(
                        saved.bandLevelsMb.ifEmpty { EqualizerPreset.IPHONE_LIKE.curve },
                        bandCount,
                    ),
                )
                _uiState.value = _uiState.value.copy(state = normalized)
            }
        }
        viewModelScope.launch {
            PerSessionEqualizerController.activeSessionCount.collect { count ->
                _uiState.value = _uiState.value.copy(perSessionActiveCount = count)
            }
        }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setBassBoost(strength: Int) = update { it.copy(bassBoostStrength = strength) }

    fun setBandLevel(index: Int, levelMb: Int) = update { current ->
        current.copy(
            bandLevelsMb = current.bandLevelsMb.toMutableList().also {
                if (index in it.indices) it[index] = levelMb
            },
        )
    }

    fun applyPreset(preset: EqualizerPreset) {
        val bandCount = _uiState.value.bandCenterFreqHz.size.takeIf { it > 0 } ?: preset.curve.size
        update {
            it.copy(
                bassBoostStrength = preset.bassBoostStrength,
                bandLevelsMb = resampleCurve(preset.curve, bandCount),
            )
        }
    }

    /**
     * Applies immediately to whatever effects are already live in THIS process — the global one
     * (if the platform allows it) and every currently-open per-app session — and persists so the
     * foreground service (and any session that opens later) picks it up too.
     */
    private fun update(transform: (EqualizerState) -> EqualizerState) {
        val next = transform(_uiState.value.state)
        _uiState.value = _uiState.value.copy(state = next)
        SystemEqualizerController.apply(next)
        PerSessionEqualizerController.applyToAll(next)
        viewModelScope.launch { EqualizerPreferences.save(getApplication(), next) }
    }
}
