package io.github.vysockiymark01_prog.airpods.ble

/**
 * Turns a stream of raw BLE proximity-pairing packets into one stable [AirPodsStatus].
 *
 * Apple's proximity-pairing broadcast (see [ProximityPairingParser]) is passive and unencrypted —
 * ANY nearby Apple device broadcasting it is picked up, not just the user's own earbuds. Before
 * this tracker existed, [AirPodsScanService] overwrote the displayed status on every single
 * packet it saw, so a second Apple device within range (someone else's AirPods, a colleague's
 * Beats, a device in the next room) could instantly flash a completely different model/battery
 * reading on screen for one update and then vanish — exactly the "то показывает верно, то
 * мелькает и пропадает" symptom this fixes.
 *
 * Two things this does:
 *  - Picks one "primary" source device by BLE address and requires a real RSSI margin (closer =
 *    more likely to be the ones actually worn) before switching away from the current primary, so
 *    a single stronger stray packet can't steal the display for one reading. A primary is only
 *    dropped for real once nothing has been heard from it for [staleTimeoutMs].
 *  - Keeps the last known case-battery/charging fields when a fresh packet reports them as
 *    "нет данных" for a while — the case is the least frequent broadcaster of the three
 *    components (only advertises reliably right after the lid opens/closes), so it's the field
 *    most likely to visibly flicker to "n/a" without this.
 */
class AirPodsStatusTracker(
    private val staleTimeoutMs: Long = STALE_TIMEOUT_MS,
    private val switchRssiMargin: Int = SWITCH_RSSI_MARGIN,
    private val fieldStickyMs: Long = FIELD_STICKY_MS,
) {
    private var primaryAddress: String? = null
    private var primaryRssi: Int = Int.MIN_VALUE
    private var primaryLastSeenMs: Long = 0L
    private var lastEmitted: AirPodsStatus? = null
    private var lastEmittedAtMs: Long = 0L

    /** @return the status to display, or null if this packet was rejected (not the primary device). */
    fun onReading(address: String, rssi: Int, status: AirPodsStatus, nowElapsedMs: Long): AirPodsStatus? {
        val currentPrimary = primaryAddress
        val primaryIsStale = currentPrimary == null || (nowElapsedMs - primaryLastSeenMs) > staleTimeoutMs

        val acceptAsPrimary = when {
            currentPrimary == null -> true
            address == currentPrimary -> true
            primaryIsStale -> true
            rssi >= primaryRssi + switchRssiMargin -> true
            else -> false
        }
        if (!acceptAsPrimary) return null

        // Switching to a genuinely new device (not just a re-confirmation of the current one)
        // invalidates any sticky fields carried over from whatever the old primary was.
        if (address != currentPrimary) {
            lastEmitted = null
        }

        primaryAddress = address
        primaryRssi = rssi
        primaryLastSeenMs = nowElapsedMs

        val merged = mergeSticky(status, nowElapsedMs)
        lastEmitted = merged
        lastEmittedAtMs = nowElapsedMs
        return merged
    }

    private fun mergeSticky(fresh: AirPodsStatus, nowElapsedMs: Long): AirPodsStatus {
        val prev = lastEmitted ?: return fresh
        if (nowElapsedMs - lastEmittedAtMs > fieldStickyMs) return fresh
        if (fresh.caseBattery != BatteryLevel.Unavailable) return fresh
        return fresh.copy(
            caseBattery = prev.caseBattery,
            caseCharging = prev.caseCharging,
        )
    }

    companion object {
        private const val STALE_TIMEOUT_MS = 10_000L
        private const val SWITCH_RSSI_MARGIN = 8
        private const val FIELD_STICKY_MS = 120_000L
    }
}
