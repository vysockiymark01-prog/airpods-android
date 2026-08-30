package io.github.vysockiymark01_prog.airpods.ble

/**
 * Turns a stream of raw BLE proximity-pairing packets into one stable [AirPodsStatus].
 *
 * Apple's proximity-pairing broadcast (see [ProximityPairingParser]) is passive and unencrypted —
 * ANY nearby Apple device broadcasting it is picked up, not just the user's own earbuds. Without
 * filtering, a second Apple device within range could instantly flash a completely different
 * model/battery reading and vanish.
 *
 * The filter here is deliberately simple: always trust whichever BLE address currently has the
 * STRONGEST signal among addresses heard in the last [freshWindowMs] — the assumption being that
 * the earbuds you're actually wearing are, by a wide margin, the closest Apple device to your
 * phone. This replaced an earlier design with a "locked primary + switch margin + must-repeat-
 * twice" scheme: that version was more resistant to single-reading flicker in theory, but in
 * practice it could get stuck refusing to update at all — e.g. across Apple's periodic BLE
 * private-address rotation, or whenever a genuinely-changing reading never happened to repeat
 * bit-for-bit twice in a row — which is a strictly worse failure mode (a frozen, wrong display,
 * and autopause/notifications going stale) than the occasional flicker it was guarding against.
 * Always-trust-the-strongest-signal has no such stuck state: there is nothing to time out of or
 * wait on, so a real change is reflected on the very next packet.
 *
 * The one thing still smoothed is a battery field reported as "нет data" — that never erases a
 * value already known for the CURRENT source device; switching to a different address resets it,
 * since a different physical device's old battery reading has nothing to do with the new one.
 */
class AirPodsStatusTracker(
    private val freshWindowMs: Long = FRESH_WINDOW_MS,
) {
    private data class SeenDevice(var rssi: Int, var lastSeenMs: Long)

    private val seenDevices = mutableMapOf<String, SeenDevice>()
    private var currentAddress: String? = null

    private val leftBattery = BatteryDebouncer()
    private val rightBattery = BatteryDebouncer()
    private val caseBattery = BatteryDebouncer()

    /** @return the status to display, or null if some other, currently-stronger device wins this packet. */
    fun onReading(address: String, rssi: Int, status: AirPodsStatus, nowElapsedMs: Long): AirPodsStatus? {
        seenDevices[address] = SeenDevice(rssi, nowElapsedMs)
        seenDevices.entries.removeAll { nowElapsedMs - it.value.lastSeenMs > freshWindowMs }

        val strongestAddress = seenDevices.maxByOrNull { it.value.rssi }?.key
        if (strongestAddress != address) return null

        if (address != currentAddress) {
            currentAddress = address
            leftBattery.reset()
            rightBattery.reset()
            caseBattery.reset()
        }

        return status.copy(
            leftBattery = leftBattery.accept(status.leftBattery),
            rightBattery = rightBattery.accept(status.rightBattery),
            caseBattery = caseBattery.accept(status.caseBattery),
        )
    }

    /** Only smooths "нет данных" into "keep the last known value" — never blocks a real change. */
    private class BatteryDebouncer {
        private var confirmed: BatteryLevel = BatteryLevel.Unavailable

        fun reset() {
            confirmed = BatteryLevel.Unavailable
        }

        fun accept(fresh: BatteryLevel): BatteryLevel {
            if (fresh == BatteryLevel.Unavailable) return confirmed
            confirmed = fresh
            return confirmed
        }
    }

    companion object {
        private const val FRESH_WINDOW_MS = 20_000L
    }
}
