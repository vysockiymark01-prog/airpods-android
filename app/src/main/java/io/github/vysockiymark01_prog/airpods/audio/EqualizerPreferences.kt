package io.github.vysockiymark01_prog.airpods.audio

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.equalizerDataStore by preferencesDataStore(name = "equalizer_prefs")

/**
 * Persists the user's global-EQ choice so [SystemEqualizerController] can be restored from
 * [io.github.vysockiymark01_prog.airpods.ble.AirPodsScanService] on its own — the foreground
 * service is what keeps the effect alive with the app UI closed, so it needs to read the saved
 * setting itself rather than relying on the Activity having run first in this process lifetime.
 */
object EqualizerPreferences {

    private val KEY_ENABLED = booleanPreferencesKey("enabled")
    private val KEY_BASS = intPreferencesKey("bass_boost_strength")
    private val KEY_BANDS = stringPreferencesKey("band_levels_mb") // comma-separated ints

    fun flow(context: Context): Flow<EqualizerState> =
        context.equalizerDataStore.data.map { prefs ->
            EqualizerState(
                enabled = prefs[KEY_ENABLED] ?: false,
                bassBoostStrength = prefs[KEY_BASS] ?: EqualizerPreset.IPHONE_LIKE.bassBoostStrength,
                bandLevelsMb = prefs[KEY_BANDS]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }
                    ?: emptyList(),
            )
        }

    suspend fun save(context: Context, state: EqualizerState) {
        context.equalizerDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = state.enabled
            prefs[KEY_BASS] = state.bassBoostStrength
            prefs[KEY_BANDS] = state.bandLevelsMb.joinToString(",")
        }
    }
}
