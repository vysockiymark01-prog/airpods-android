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
import io.github.vysockiymark01_prog.airpods.audio.PerSessionEqualizerController
import io.github.vysockiymark01_prog.airpods.audio.SystemEqualizerController
import kotlinx.coroutines.launch
import io.github.vysockiymark01_prog.airpods.MainActivity
import io.github.vysockiymark01_prog.airpods.R
import io.github.vysockiymark01_prog.airpods.widget.AirPodsWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that keeps a passive BLE scan running so battery/ear-detection data keeps
 * updating even when the app UI isn't open. Required because Android kills background BLE
 * scanning aggressively otherwise (Doze / battery optimization).
 *
 * Also owns everything else that needs to keep running with the app UI closed: the global
 * equalizer ([startEqualizer]), pause-on-removal ([autoPauseController]), the live-battery
 * notification, low-battery alerts, and periodic battery-history logging.
 *
 * Scan interval note: firmware only re-broadcasts every ~1 minute or so (see README "Ограничения
 * точности") — [ScanSettings.SCAN_MODE_LOW_LATENCY] doesn't make readings arrive faster than the
 * earbuds themselves send them, it just avoids Android's own coalescing delay on top of that.
 */
class AirPodsScanService : LifecycleService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "airpods_monitor"
        private const val ALERT_CHANNEL_ID = "airpods_battery_alerts"
        private const val NOTIFICATION_ID = 1
        private const val LEFT_ALERT_NOTIFICATION_ID = 10
        private const val RIGHT_ALERT_NOTIFICATION_ID = 11
        private const val CASE_ALERT_NOTIFICATION_ID = 12

        // Alert once at/under this level, don't alert again until it's back above the recovery
        // threshold (or charging) — otherwise every single reading near the cutoff would re-fire.
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val LOW_BATTERY_RECOVERY_THRESHOLD = 30

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

    // Not tied to lifecycle — one tracker for the service's whole lifetime, same reasoning as
    // SystemEqualizerController being a singleton: we want one continuous view of "who's primary"
    // rather than resetting it every time the service restarts.
    private val statusTracker = AirPodsStatusTracker()

    // Lives here (not in MainViewModel) specifically so pause-on-removal keeps working with the
    // app UI fully closed — the service is what stays alive, the Activity/ViewModel does not.
    private val autoPauseController = AutoPauseController(this)

    // Which components we've already sent a low-battery alert for, so we don't re-alert on every
    // single reading while sitting under the threshold — cleared once a component recovers.
    private val lowBatteryAlerted = mutableSetOf<String>()

    private val scanCallback = object : ScanCallback() {
        @Suppress("MissingPermission") // guarded by hasScanPermission() before the scan is started
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(0x004C) ?: return
            val status = ProximityPairingParser.parse(manufacturerData, SystemClock.elapsedRealtime())
                ?: return
            val address = result.device?.address ?: return
            val merged = statusTracker.onReading(address, result.rssi, status, SystemClock.elapsedRealtime())
            if (merged != null) {
                _latestStatus.value = merged
                autoPauseController.onStatusUpdate(merged)
                updateOngoingNotification(merged)
                checkLowBattery(merged)
                lifecycleScope.launch { BatteryHistoryStore.record(applicationContext, merged) }
                lifecycleScope.launch { AirPodsWidget().updateAll(applicationContext) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            // Surfaced to the UI via a distinct "нет данных" state rather than crashing the service.
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildNotification(null))
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
            EqualizerPreferences.applyDefaultPresetIfFirstRun(applicationContext)
            EqualizerPreferences.flow(applicationContext).collect { state ->
                SystemEqualizerController.apply(state)
                PerSessionEqualizerController.applyToAll(state)
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

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.notification_alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /** Keeps the persistent monitoring notification's battery numbers current. */
    private fun updateOngoingNotification(status: AirPodsStatus) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: AirPodsStatus?): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = status?.model?.displayName ?: getString(R.string.notification_monitoring_title)
        val text = status?.let { formatBatterySummary(it) }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text) }
            .setSmallIcon(R.drawable.ic_notification_earbud)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun formatBatterySummary(status: AirPodsStatus): String {
        fun fmt(level: BatteryLevel) = when (level) {
            is BatteryLevel.Percent -> "${level.value}%"
            BatteryLevel.Unavailable -> "н/д"
        }
        return "Л ${fmt(status.leftBattery)} · П ${fmt(status.rightBattery)} · Кейс ${fmt(status.caseBattery)}"
    }

    private fun checkLowBattery(status: AirPodsStatus) {
        checkComponent("left", LEFT_ALERT_NOTIFICATION_ID, "Левый наушник", status.leftBattery, status.leftCharging)
        checkComponent("right", RIGHT_ALERT_NOTIFICATION_ID, "Правый наушник", status.rightBattery, status.rightCharging)
        checkComponent("case", CASE_ALERT_NOTIFICATION_ID, "Кейс", status.caseBattery, status.caseCharging)
    }

    private fun checkComponent(key: String, notificationId: Int, label: String, level: BatteryLevel, charging: Boolean) {
        val percent = (level as? BatteryLevel.Percent)?.value ?: return
        if (charging || percent > LOW_BATTERY_RECOVERY_THRESHOLD) {
            lowBatteryAlerted.remove(key)
            return
        }
        if (percent <= LOW_BATTERY_THRESHOLD && lowBatteryAlerted.add(key)) {
            sendLowBatteryAlert(notificationId, label, percent)
        }
    }

    private fun sendLowBatteryAlert(notificationId: Int, label: String, percent: Int) {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("$label — заряд $percent%")
            .setContentText("Скоро разрядится — стоит зарядить")
            .setSmallIcon(R.drawable.ic_notification_earbud)
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }
}
