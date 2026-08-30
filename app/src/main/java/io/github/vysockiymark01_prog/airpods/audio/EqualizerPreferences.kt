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
    private val KEY_DEFAULT_APPLIED = booleanPreferencesKey("default_preset_applied")

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
            // Any explicit save (including one made by this same call) counts as the user having
            // an opinion now — don't let a later first-run check stomp on it.
            prefs[KEY_DEFAULT_APPLIED] = true
        }
    }

    /**
     * Turns the EQ on with the "как на iPhone" preset the very first time this ever runs — this
     * is what makes the original ask ("хочу, чтобы громкость была как на айфоне") actually happen
     * automatically instead of requiring a trip into the equalizer screen first. Safe to call on
     * every service start: after the very first run it's a no-op, and it never overwrites a
     * choice the user (or a previous call to [save]) already made.
     */
    suspend fun applyDefaultPresetIfFirstRun(context: Context) {
        context.equalizerDataStore.edit { prefs ->
            if (prefs[KEY_DEFAULT_APPLIED] == true) return@edit
            prefs[KEY_DEFAULT_APPLIED] = true
            prefs[KEY_ENABLED] = true
            prefs[KEY_BASS] = EqualizerPreset.IPHONE_LIKE.bassBoostStrength
            prefs[KEY_BANDS] = EqualizerPreset.IPHONE_LIKE.curve.joinToString(",")
        }
    }
}
