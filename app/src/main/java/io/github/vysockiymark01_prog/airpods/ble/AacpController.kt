package io.github.vysockiymark01_prog.airpods.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Why a noise-control command did or didn't go out — surfaced in the UI so a failure is
 *  diagnosable without needing `adb logcat` on the user's own phone. */
sealed class AacpSendResult {
    object Success : AacpSendResult()
    data class Failure(val reason: String) : AacpSendResult()
}

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
 *    ignores it, [sendNoiseControlMode] still reports [AacpSendResult.Success] (the write didn't
 *    throw) but the mode may not actually change — this asymmetry is called out in the UI.
 *
 * Connection mode: we try an *insecure* L2CAP channel first (no pairing/encryption renegotiation,
 * fastest), and if that fails, retry with a *secure* channel. This matters because Android's
 * insecure-L2CAP client path is known to fail on some OEM Bluetooth stacks with a generic
 * "read failed, socket might closed or timeout, read ret: -1" error even though the remote device
 * is reachable and willing to accept a connection — that message comes from Android's own BT
 * stack refusing/dropping the socket, not from the earbuds. The secure path exercises a different
 * code path in the stack and succeeds on some of the devices where insecure fails.
 */
class AacpController(private val device: BluetoothDevice) {

    private var socket: BluetoothSocket? = null

    companion object {
        private const val TAG = "AacpController"
        private const val AACP_L2CAP_PSM = 0x1001
    }

    @SuppressLint("MissingPermission") // caller is required to have checked BLUETOOTH_CONNECT
    private fun openSocket(secure: Boolean): BluetoothSocket =
        if (secure) device.createL2capChannel(AACP_L2CAP_PSM) else device.createInsecureL2capChannel(AACP_L2CAP_PSM)

    @SuppressLint("MissingPermission")
    private suspend fun tryConnect(secure: Boolean): Result<BluetoothSocket> = withContext(Dispatchers.IO) {
        try {
            val s = openSocket(secure)
            s.connect()
            Result.success(s)
        } catch (e: IOException) {
            Log.w(TAG, "${if (secure) "Secure" else "Insecure"} L2CAP connect failed for ${device.address}: ${e.message}")
            Result.failure(IOException(e.message ?: e.javaClass.simpleName, e))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureConnected(): Result<BluetoothSocket> = withContext(Dispatchers.IO) {
        socket?.takeIf { it.isConnected }?.let { return@withContext Result.success(it) }
        try {
            val insecureResult = tryConnect(secure = false)
            insecureResult.getOrNull()?.let {
                socket = it
                return@withContext Result.success(it)
            }
            val secureResult = tryConnect(secure = true)
            secureResult.getOrNull()?.let {
                socket = it
                return@withContext Result.success(it)
            }
            val insecureMsg = insecureResult.exceptionOrNull()?.message ?: "?"
            val secureMsg = secureResult.exceptionOrNull()?.message ?: "?"
            val reason = "не удалось открыть L2CAP-канал ни в незащищённом (\"$insecureMsg\"), " +
                "ни в защищённом режиме (\"$secureMsg\") — проверьте, что наушники сопряжены и " +
                "подключены как аудиоустройство в системных настройках Bluetooth"
            Result.failure(IOException(reason))
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", e)
            Result.failure(SecurityException("нет разрешения BLUETOOTH_CONNECT", e))
        }
    }

    /** Does NOT confirm the mode actually changed on the hardware — only that the packet was sent. */
    suspend fun sendNoiseControlMode(model: AirPodsModel, rawModelId: Int, mode: NoiseControlMode): AacpSendResult {
        val packet = AacpCommandTable.packetFor(model, rawModelId, mode)
            ?: return AacpSendResult.Failure("для этой модели нет известной команды ANC")
        val socketResult = ensureConnected()
        val s = socketResult.getOrNull()
            ?: return AacpSendResult.Failure(socketResult.exceptionOrNull()?.message ?: "не удалось подключиться")
        return withContext(Dispatchers.IO) {
            try {
                s.outputStream.write(packet)
                s.outputStream.flush()
                AacpSendResult.Success
            } catch (e: IOException) {
                Log.w(TAG, "Write failed: ${e.message}")
                socket = null
                AacpSendResult.Failure("не удалось записать в сокет (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    fun close() {
        try { socket?.close() } catch (_: IOException) { /* ignore */ }
        socket = null
    }
}
