package io.github.vysockiymark01_prog.airpods.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Sends AACP control commands (currently: noise-control mode) over the L2CAP channel AirPods
 * expose on their existing *classic* Bluetooth connection.
 *
 * This is NOT a GATT characteristic write. AirPods only run BLE for the advertisement broadcast
 * (battery, ear detection) — the actual control channel is an L2CAP connection-oriented channel
 * (PSM 0x1001 / 4097) carried over the classic-Bluetooth ACL link that's already established once
 * the phone is paired and connected for audio (A2DP/HFP). That means:
 *
 *  - The earbuds must already be paired & connected via Android's normal Bluetooth settings
 *    (audio device), not "connected" by this app — we ride the existing classic link.
 *  - `BluetoothDevice.createInsecureL2capChannel(psm)` requires API 29+; on older Android this
 *    feature is unavailable and ANC switching cannot work (see README).
 *  - This is best-effort: the exact handshake AirPods expect before accepting commands is not
 *    fully documented publicly. We send the raw control packet directly; if the device silently
 *    ignores it, [sendNoiseControlMode] still reports success at the socket level (the write
 *    didn't throw) but the mode may not actually change — this asymmetry is called out in the UI.
 */
class AacpController(private val device: BluetoothDevice) {

    private var socket: BluetoothSocket? = null

    companion object {
        private const val TAG = "AacpController"
        private const val AACP_L2CAP_PSM = 0x1001
    }

    @SuppressLint("MissingPermission") // caller is required to have checked BLUETOOTH_CONNECT
    private suspend fun ensureConnected(): BluetoothSocket? = withContext(Dispatchers.IO) {
        socket?.takeIf { it.isConnected }?.let { return@withContext it }
        try {
            val s = device.createInsecureL2capChannel(AACP_L2CAP_PSM)
            s.connect()
            socket = s
            s
        } catch (e: IOException) {
            Log.w(TAG, "L2CAP connect failed for ${device.address}: ${e.message}")
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", e)
            null
        }
    }

    /** @return true if the packet was written to the socket (NOT a confirmation the mode changed). */
    suspend fun sendNoiseControlMode(model: AirPodsModel, rawModelId: Int, mode: NoiseControlMode): Boolean {
        val packet = AacpCommandTable.packetFor(model, rawModelId, mode) ?: return false
        val s = ensureConnected() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                s.outputStream.write(packet)
                s.outputStream.flush()
                true
            } catch (e: IOException) {
                Log.w(TAG, "Write failed: ${e.message}")
                socket = null
                false
            }
        }
    }

    fun close() {
        try { socket?.close() } catch (_: IOException) { /* ignore */ }
        socket = null
    }
}
