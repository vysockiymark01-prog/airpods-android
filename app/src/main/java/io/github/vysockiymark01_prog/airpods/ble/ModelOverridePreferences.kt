package io.github.vysockiymark01_prog.airpods.ble

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.modelOverrideDataStore by preferencesDataStore(name = "model_override_prefs")

/**
 * Lets the user manually pin the displayed [AirPodsModel] when automatic detection from the BLE
 * broadcast is unreliable (wrong/unknown model, or flicker between models — see
 * [AirPodsStatusTracker] for the detection-side fix, this is the user-facing escape hatch on top
 * of it). Persisted so the override survives app restarts, same pattern as [io.github.vysockiymark01_prog.airpods.audio.EqualizerPreferences].
 *
 * Battery/ear-detection/charging data always still comes from the real broadcast — only the
 * *model* (and therefore [AirPodsModel.supportsAnc] / the ANC command table lookup) is overridden.
 */
object ModelOverridePreferences {

    private val KEY_MODEL_ID = intPreferencesKey("override_model_id")
    private const val NONE = Int.MIN_VALUE

    /** Null means "auto" (no override) — use whatever the BLE broadcast reports. */
    fun flow(context: Context): Flow<AirPodsModel?> =
        context.modelOverrideDataStore.data.map { prefs ->
            val id = prefs[KEY_MODEL_ID] ?: NONE
            if (id == NONE) null else AirPodsModel.entries.firstOrNull { it.modelId == id }
        }

    suspend fun save(context: Context, model: AirPodsModel?) {
        context.modelOverrideDataStore.edit { prefs ->
            prefs[KEY_MODEL_ID] = model?.modelId ?: NONE
        }
    }
}
