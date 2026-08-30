package io.github.vysockiymark01_prog.airpods.audio

/**
 * @param bandLevelsMb per-band gain in millibels (100 mB = 1 dB), always in the app's own
 * [VIRTUAL_BAND_COUNT]-band virtual space (see [VIRTUAL_BAND_FREQS_HZ]) — the UI and persisted
 * state never deal in the real device's band count directly. [resampleCurve] maps this down (or
 * up) onto whatever the real target (a global or per-session platform Equalizer) actually reports
 * at the moment a value is written to it.
 * @param bassBoostStrength 0..1000, per [android.media.audiofx.BassBoost.setStrength].
 */
data class EqualizerState(
    val enabled: Boolean = false,
    val bandLevelsMb: List<Int> = emptyList(),
    val bassBoostStrength: Int = 0,
)

/**
 * The app presents a 10-band graphic EQ regardless of what the real hardware supports — most
 * Android phones only expose 5 platform EQ bands, so honestly, two adjacent virtual bands here
 * often end up nudging the same real band together. That's still strictly more useful than
 * hard-limiting the UI to 5 sliders: it gives finer control over which part of the spectrum a
 * boost centers on, and [resampleCurve] (nearest-index) picks a sensible mapping automatically.
 * Frequencies are the standard ISO 10-band graphic-EQ centers.
 */
const val VIRTUAL_BAND_COUNT = 10
val VIRTUAL_BAND_FREQS_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

/** Named starting points, authored directly in the 10-band virtual space. */
enum class EqualizerPreset(val label: String, val bassBoostStrength: Int, val curve: List<Int>) {
    FLAT("Плоский", bassBoostStrength = 0, curve = List(VIRTUAL_BAND_COUNT) { 0 }),

    /**
     * Slight bass/treble lift with a gentle mid dip — approximates the punchier factory curve
     * iPhones apply on top of AirPods audio that Android's flat default does not. This is a
     * software EQ guess at "sounds like iPhone", NOT Apple's actual per-track Adaptive Audio
     * processing (that runs on the AirPods' own H2 chip using data Android has no access to).
     */
    IPHONE_LIKE(
        "Как на iPhone",
        bassBoostStrength = 400,
        curve = listOf(350, 300, 200, 50, -50, -50, 50, 150, 250, 300),
    ),
    MORE_BASS(
        "Больше баса",
        bassBoostStrength = 700,
        curve = listOf(600, 550, 400, 200, 50, 0, 0, 0, 0, 0),
    ),
    VOCAL_BOOST(
        "Голос чётче",
        bassBoostStrength = 100,
        curve = listOf(-150, -100, -50, 100, 300, 350, 300, 100, 0, 0),
    ),
}

/** Proportionally maps [curve] onto [targetBandCount] entries (nearest-index resampling). */
fun resampleCurve(curve: List<Int>, targetBandCount: Int): List<Int> {
    if (targetBandCount <= 0) return emptyList()
    if (curve.isEmpty()) return List(targetBandCount) { 0 }
    if (curve.size == targetBandCount) return curve
    val lastSrcIndex = (curve.size - 1).coerceAtLeast(1)
    val lastDstIndex = (targetBandCount - 1).coerceAtLeast(1)
    return (0 until targetBandCount).map { i ->
        val srcIndex = (i * lastSrcIndex.toFloat() / lastDstIndex).toInt().coerceIn(curve.indices)
        curve[srcIndex]
    }
}
