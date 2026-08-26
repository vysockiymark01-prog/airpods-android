package io.github.vysockiymark01_prog.airpods.audio

/**
 * @param bandLevelsMb per-band gain in millibels (100 mB = 1 dB). Indices match whatever bands
 * [SystemEqualizerController.bands] reports for the current device — always resample with
 * [resampleCurve] before applying a value list of a different size.
 * @param bassBoostStrength 0..1000, per [android.media.audiofx.BassBoost.setStrength].
 */
data class EqualizerState(
    val enabled: Boolean = false,
    val bandLevelsMb: List<Int> = emptyList(),
    val bassBoostStrength: Int = 0,
)

/**
 * Named starting points. Curves are authored for a "typical" 5-band equalizer and are
 * proportionally resampled onto however many bands the real device actually reports — Android
 * phones commonly expose 5 bands, but this is not guaranteed by the platform.
 */
enum class EqualizerPreset(val label: String, val bassBoostStrength: Int, val curve: List<Int>) {
    FLAT("Плоский", bassBoostStrength = 0, curve = listOf(0, 0, 0, 0, 0)),

    /**
     * Slight bass/treble lift with a gentle mid dip — approximates the punchier factory curve
     * iPhones apply on top of AirPods audio that Android's flat default does not. This is a
     * software EQ guess at "sounds like iPhone", NOT Apple's actual per-track Adaptive Audio
     * processing (that runs on the AirPods' own H2 chip using data Android has no access to).
     */
    IPHONE_LIKE(
        "Как на iPhone",
        bassBoostStrength = 400,
        curve = listOf(300, 150, -50, 100, 250),
    ),
    MORE_BASS("Больше баса", bassBoostStrength = 700, curve = listOf(500, 300, 0, 0, 0)),
    VOCAL_BOOST("Голос чётче", bassBoostStrength = 100, curve = listOf(-100, 0, 300, 300, 100)),
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
