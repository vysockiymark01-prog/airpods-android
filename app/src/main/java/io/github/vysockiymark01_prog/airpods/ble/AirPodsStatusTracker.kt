package io.github.vysockiymark01_prog.airpods.ble

/**
 * Turns a stream of raw BLE proximity-pairing packets into one stable, as-accurate-as-possible
 * [AirPodsStatus] — in particular the three battery readings (left/right pod + case), which is
 * what actually flickers/misreports in practice.
 *
 * Apple's proximity-pairing broadcast (see [ProximityPairingParser]) is passive and unencrypted —
 * ANY nearby Apple device broadcasting it is picked up, not just the user's own earbuds. Without
 * filtering, [AirPodsScanService] used to overwrite the displayed status on every single packet,
 * so a second Apple device within range (someone else's AirPods, a colleague's Beats, a device in
 * the next room) could instantly flash a completely different model/battery reading and vanish.
 *
 * Two layers of filtering, in order:
 *
 * 1. **Primary-device lock** — picks one BLE address as "the" source by RSSI and requires a real
 *    signal margin ([switchRssiMargin]) before switching away from it, so a single momentarily-
 *    stronger stray packet can't steal the display. The lock is only dropped for real once
 *    nothing has been heard from that address for [staleTimeoutMs] — set well above the ~1 minute
 *    re-broadcast interval (see [AirPodsScanService] doc) so the lock survives normal gaps between
 *    readings; a real device swap (or Apple's periodic random-address rotation) is picked up once
 *    the old address genuinely goes quiet.
 *
 * 2. **Per-field battery debounce** — even from the *correct* device, a single BLE packet is not
 *    proof: a bit misread, a half-decoded advertisement, or a genuinely transient firmware report
 *    can produce one outlier value. Each of the three battery fields only adopts a *changed*
 *    value once it's been seen [requiredConfirmations] times in a row; the very first reading for
 *    a field is shown immediately (so the UI isn't stuck on "нет данных" waiting to double-check
 *    the initial value), and a field reported as "нет данных" never erases a value already known
 *    — it just means that particular broadcast didn't include it (normal for the case, which
 *    reports least often of the three).
 */
class AirPodsStatusTracker(
    private val staleTimeoutMs: Long = STALE_TIMEOUT_MS,
    private val switchRssiMargin: Int = SWITCH_RSSI_MARGIN,
    private val requiredConfirmations: Int = REQUIRED_CONFIRMATIONS,
) {
    private var primaryAddress: String? = null
    private var primaryRssi: Int = Int.MIN_VALUE
    private var primaryLastSeenMs: Long = 0L

    private val leftBattery = BatteryDebouncer(requiredConfirmations)
    private val rightBattery = BatteryDebouncer(requiredConfirmations)
    private val caseBattery = BatteryDebouncer(requiredConfirmations)

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
        // invalidates all debounce state carried over from whatever the old primary was — a new
        // source's battery levels have nothing to do with the previous one's.
        if (address != currentPrimary) {
            leftBattery.reset()
            rightBattery.reset()
            caseBattery.reset()
        }

        primaryAddress = address
        primaryRssi = rssi
        primaryLastSeenMs = nowElapsedMs

        return status.copy(
            leftBattery = leftBattery.accept(status.leftBattery),
            rightBattery = rightBattery.accept(status.rightBattery),
            caseBattery = caseBattery.accept(status.caseBattery),
        )
    }

    /** Debounces one battery field so a single outlier packet can't change what's displayed. */
    private class BatteryDebouncer(private val requiredConfirmations: Int) {
        private var confirmed: BatteryLevel = BatteryLevel.Unavailable
        private var pending: BatteryLevel? = null
        private var pendingCount = 0

        fun reset() {
            confirmed = BatteryLevel.Unavailable
            pending = null
            pendingCount = 0
        }

        fun accept(fresh: BatteryLevel): BatteryLevel {
            if (fresh == BatteryLevel.Unavailable) {
                // No new info this time — keep whatever we already knew, don't drop to "n/a".
                pending = null
                pendingCount = 0
                return confirmed
            }
            if (confirmed == BatteryLevel.Unavailable) {
                // First real reading for this field on this device: show it immediately rather
                // than making the user wait through requiredConfirmations for an initial value.
                confirmed = fresh
                pending = null
                pendingCount = 0
                return confirmed
            }
            if (fresh == confirmed) {
                pending = null
                pendingCount = 0
                return confirmed
            }
            if (fresh == pending) {
                pendingCount++
            } else {
                pending = fresh
                pendingCount = 1
            }
            if (pendingCount >= requiredConfirmations) {
                confirmed = fresh
                pending = null
                pendingCount = 0
            }
            return confirmed
        }
    }

    companion object {
        private const val STALE_TIMEOUT_MS = 120_000L
        private const val SWITCH_RSSI_MARGIN = 8
        private const val REQUIRED_CONFIRMATIONS = 2
    }
}
