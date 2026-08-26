package io.github.vysockiymark01_prog.airpods.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.vysockiymark01_prog.airpods.audio.EqualizerPreferences
import io.github.vysockiymark01_prog.airpods.audio.SystemEqualizerController
import kotlinx.coroutines.launch
import io.github.vysockiymark01_prog.airpods.MainActivity
import io.github.vysockiymark01_prog.airpods.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that keeps a passive BLE scan running so battery/ear-detection data keeps
 * updating even when the app UI isn't open. Required because Android kills background BLE
 * scanning aggressively otherwise (Doze / battery optimization).
 *
 * Scan interval note: firmware only re-broadcasts every ~1 minute or so (see README "Ограничения
 * точности") — [ScanSettings.SCAN_MODE_LOW_LATENCY] doesn't make readings arrive faster than the
 * earbuds themselves send them, it just avoids Android's own coalescing delay on top of that.
 */
class AirPodsScanService : LifecycleService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "airpods_monitor"
        private const val NOTIFICATION_ID = 1

        private val _latestStatus = MutableStateFlow<AirPodsStatus?>(null)
        val latestStatus: StateFlow<AirPodsStatus?> = _latestStatus.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AirPodsScanService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AirPodsScanService::class.java))
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(0x004C) ?: return
            val status = ProximityPairingParser.parse(manufacturerData, SystemClock.elapsedRealtime())
            if (status != null) {
                _latestStatus.value = status
            }
        }

        override fun onScanFailed(errorCode: Int) {
            // Surfaced to the UI via a distinct "нет данных" state rather than crashing the service.
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startScanIfPermitted()
        startEqualizer()
    }

    override fun onDestroy() {
        stopScan()
        SystemEqualizerController.release()
        super.onDestroy()
    }

    /**
     * Owns the global equalizer/bass-boost for as long as this service is alive, which is what
     * makes it keep applying with the app UI closed — see [SystemEqualizerController] doc. Reads
     * the saved setting itself (rather than waiting for the Activity to push it) so a reboot or a
     * process restart still restores whatever the user last chose the moment this service starts.
     */
    private fun startEqualizer() {
        SystemEqualizerController.ensureInitialized()
        lifecycleScope.launch {
            EqualizerPreferences.flow(applicationContext).collect { state ->
                SystemEqualizerController.apply(state)
            }
        }
    }

    private fun hasScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission") // guarded by hasScanPermission()
    private fun startScanIfPermitted() {
        if (!hasScanPermission()) return
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: return
        if (!adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // battery-friendly; data itself is slow-changing anyway
            .build()
        scanner.startScan(null, settings, scanCallback)
    }

    @Suppress("MissingPermission")
    private fun stopScan() {
        if (!hasScanPermission()) return
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: return
        adapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_monitoring_title))
            .setSmallIcon(R.drawable.ic_notification_earbud)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
