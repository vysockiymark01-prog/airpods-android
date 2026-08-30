package io.github.vysockiymark01_prog.airpods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vysockiymark01_prog.airpods.audio.EqualizerPreferences
import io.github.vysockiymark01_prog.airpods.audio.EqualizerPreset
import io.github.vysockiymark01_prog.airpods.audio.EqualizerState
import io.github.vysockiymark01_prog.airpods.audio.PerSessionEqualizerController
import io.github.vysockiymark01_prog.airpods.audio.SystemEqualizerController
import io.github.vysockiymark01_prog.airpods.audio.VIRTUAL_BAND_COUNT
import io.github.vysockiymark01_prog.airpods.audio.VIRTUAL_BAND_FREQS_HZ
import io.github.vysockiymark01_prog.airpods.audio.resampleCurve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EqualizerUiState(
    val available: Boolean = false,
    val bassBoostAvailable: Boolean = false,
    val perSessionActiveCount: Int = 0,
    val bandCenterFreqHz: List<Int> = VIRTUAL_BAND_FREQS_HZ,
    val bandLevelRangeMb: IntRange = -1500..1500,
    val state: EqualizerState = EqualizerState(),
)

/**
 * The UI always works in the app's own 10-band virtual space ([VIRTUAL_BAND_COUNT]) regardless of
 * what the real device's platform Equalizer supports — [resampleCurve] maps it down (or up) onto
 * the real band count only at the moment a value is actually written to hardware, inside
 * [SystemEqualizerController.apply] / [PerSessionEqualizerController]. The real device's
 * reported gain range ([bandLevelRangeMb]) is still used when available, since that's a genuine
 * hardware limit worth respecting rather than a count worth hiding.
 */
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
            bandLevelRangeMb = if (bands.isNotEmpty()) {
                bands.first().minLevelMb..bands.first().maxLevelMb
            } else {
                _uiState.value.bandLevelRangeMb
            },
        )
        viewModelScope.launch {
            EqualizerPreferences.flow(application).collect { saved ->
                val normalized = saved.copy(
                    bandLevelsMb = resampleCurve(
                        saved.bandLevelsMb.ifEmpty { EqualizerPreset.IPHONE_LIKE.curve },
                        VIRTUAL_BAND_COUNT,
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
        update {
            it.copy(
                bassBoostStrength = preset.bassBoostStrength,
                bandLevelsMb = resampleCurve(preset.curve, VIRTUAL_BAND_COUNT),
            )
        }
    }

    fun resetToFlat() = applyPreset(EqualizerPreset.FLAT)

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
