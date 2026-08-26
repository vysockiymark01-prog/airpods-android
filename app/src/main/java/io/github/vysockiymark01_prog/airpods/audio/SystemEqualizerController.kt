package io.github.vysockiymark01_prog.airpods.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log

/**
 * Global (system-wide) audio effects attached to Android's output mix (audioSession = 0) instead
 * of to a specific app's playback — this is what lets it boost/shape whatever is currently
 * playing on the phone, regardless of which app, closer to the punchier factory tuning iPhones
 * apply on top of AirPods.
 *
 * This is NOT Apple's Adaptive EQ/Personalized Volume (those run on the AirPods' own H2 chip on
 * data only iOS has access to, see README "Точно недоступно") — it's a standard Android-OS-level
 * software equalizer + bass boost. Whether a session-0 effect reaches Bluetooth (A2DP) output
 * depends on the phone's audio HAL: most stock Android builds apply it before Bluetooth routing,
 * some heavily customized OEM skins do not — [isAvailable] only reflects whether the platform
 * accepted creating the effect at all, not whether it's audible on this specific phone.
 *
 * A singleton (not tied to any one Activity/Service instance) so the Activity's settings UI and
 * the foreground service that keeps it alive in the background both talk to the same effect
 * objects instead of fighting over two separate instances on the same audio session.
 */
object SystemEqualizerController {

    private const val TAG = "SystemEqualizer"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    /** True once the platform accepted global (session 0) audio effects on this device. */
    val isAvailable: Boolean
        get() = equalizer != null

    data class BandInfo(val index: Int, val centerFreqHz: Int, val minLevelMb: Int, val maxLevelMb: Int)

    /** Safe to call repeatedly (from both the Activity and the service) — only creates once. */
    fun ensureInitialized() {
        if (equalizer != null) return
        runCatching {
            val eq = Equalizer(0, 0).apply { enabled = false }
            val bb = BassBoost(0, 0).apply { enabled = false }
            equalizer = eq
            bassBoost = bb
        }.onFailure {
            Log.w(TAG, "Global audio effects unavailable on this device: ${it.message}")
            equalizer = null
            bassBoost = null
        }
    }

    fun bands(): List<BandInfo> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            val range = eq.bandLevelRange
            (0 until eq.numberOfBands.toInt()).map { i ->
                BandInfo(
                    index = i,
                    centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000,
                    minLevelMb = range[0].toInt(),
                    maxLevelMb = range[1].toInt(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Resamples [state] onto the real device's band count before writing it to the platform. */
    fun apply(state: EqualizerState) {
        val eq = equalizer
        if (eq != null) {
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
            }.onFailure { Log.w(TAG, "EQ apply failed: ${it.message}") }
        }
        val bb = bassBoost
        if (bb != null) {
            runCatching {
                bb.enabled = state.enabled && state.bassBoostStrength > 0
                bb.setStrength(state.bassBoostStrength.coerceIn(0, 1000).toShort())
            }.onFailure { Log.w(TAG, "BassBoost apply failed: ${it.message}") }
        }
    }

    /** Call when the foreground service that owns this controller's lifetime is destroyed. */
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }
}
