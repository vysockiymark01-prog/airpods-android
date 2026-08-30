package io.github.vysockiymark01_prog.airpods.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fallback path for the equalizer on devices where [SystemEqualizerController] can't get a
 * global (session 0) effect at all — some OEM audio HALs simply refuse that, with no app-level
 * workaround (see its doc).
 *
 * Instead of asking for the whole device's audio mix, this attaches an Equalizer/BassBoost to
 * the specific audio session of whichever app is *actually playing something right now* —
 * [AudioSessionEffectReceiver] discovers those sessions via the standard system broadcasts most
 * media apps send ([android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION]),
 * the same mechanism real Play Store equalizer apps use. No known Android OEM blocks an app from
 * attaching an effect to a session another app explicitly opened for that purpose — it's a much
 * less privileged ask than a global effect — which is what makes this work more broadly.
 *
 * Honest trade-off: this only affects apps that send that broadcast. Most mainstream
 * music/streaming apps do (YouTube Music, Spotify, most ExoPlayer-based players, the platform
 * MediaPlayer); an app that doesn't will keep playing unmodified — this is that other app's own
 * choice, not something either Android or this app can force.
 */
object PerSessionEqualizerController {

    private const val TAG = "PerSessionEqualizer"

    private data class SessionEffects(val equalizer: Equalizer?, val bassBoost: BassBoost?)

    private val sessions = mutableMapOf<Int, SessionEffects>()

    @Volatile
    private var lastState: EqualizerState = EqualizerState()

    private val _activeSessionCount = MutableStateFlow(0)
    val activeSessionCount: StateFlow<Int> = _activeSessionCount.asStateFlow()

    /** Called by [AudioSessionEffectReceiver] when some app opens an audio session for effects. */
    fun attach(sessionId: Int) {
        if (sessionId == 0 || sessions.containsKey(sessionId)) return
        val eq = runCatching { Equalizer(0, sessionId).apply { enabled = false } }
            .onFailure { Log.w(TAG, "Per-session Equalizer failed for session $sessionId: ${it.message}") }
            .getOrNull()
        val bb = runCatching { BassBoost(0, sessionId).apply { enabled = false } }
            .onFailure { Log.w(TAG, "Per-session BassBoost failed for session $sessionId: ${it.message}") }
            .getOrNull()
        if (eq == null && bb == null) return // this session doesn't support effects either
        sessions[sessionId] = SessionEffects(eq, bb)
        applyToSession(sessionId, lastState)
        _activeSessionCount.value = sessions.size
    }

    /** Called by [AudioSessionEffectReceiver] when that app's session closes. */
    fun detach(sessionId: Int) {
        val effects = sessions.remove(sessionId) ?: return
        runCatching { effects.equalizer?.release() }
        runCatching { effects.bassBoost?.release() }
        _activeSessionCount.value = sessions.size
    }

    /** Applies [state] to every currently-open session, and remembers it for future ones. */
    fun applyToAll(state: EqualizerState) {
        lastState = state
        sessions.keys.toList().forEach { applyToSession(it, state) }
    }

    private fun applyToSession(sessionId: Int, state: EqualizerState) {
        val effects = sessions[sessionId] ?: return
        effects.equalizer?.let { eq ->
            runCatching {
                val bandCount = eq.numberOfBands.toInt()
                val curve = resampleCurve(
                    state.bandLevelsMb.ifEmpty { EqualizerPreset.IPHONE_LIKE.curve },
                    bandCount,
                )
                val range = eq.bandLevelRange
                eq.enabled = state.enabled
                curve.forEachIndexed { index, level ->
                    val clamped = level.coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(index.toShort(), clamped.toShort())
                }
            }.onFailure { Log.w(TAG, "Per-session EQ apply failed for $sessionId: ${it.message}") }
        }
        effects.bassBoost?.let { bb ->
            runCatching {
                bb.enabled = state.enabled && state.bassBoostStrength > 0
                bb.setStrength(state.bassBoostStrength.coerceIn(0, 1000).toShort())
            }.onFailure { Log.w(TAG, "Per-session BassBoost apply failed for $sessionId: ${it.message}") }
        }
    }
}
