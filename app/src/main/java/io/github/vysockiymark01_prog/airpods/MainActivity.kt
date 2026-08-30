package io.github.vysockiymark01_prog.airpods

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.vysockiymark01_prog.airpods.ble.AirPodsScanService
import io.github.vysockiymark01_prog.airpods.ui.BatteryHistoryScreen
import io.github.vysockiymark01_prog.airpods.ui.EqualizerScreen
import io.github.vysockiymark01_prog.airpods.ui.HomeScreen
import io.github.vysockiymark01_prog.airpods.ui.theme.AirPodsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            AirPodsScanService.start(this)
            attachPairedAirPodsIfAny()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasAllPermissions()) {
            AirPodsScanService.start(this)
            attachPairedAirPodsIfAny()
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            AirPodsTheme(themeMode = uiState.themeMode) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onRequestPermissions = { requestPermissions() },
                            onOpenEqualizer = { navController.navigate("equalizer") },
                            onOpenHistory = { navController.navigate("history") },
                        )
                    }
                    composable("equalizer") {
                        EqualizerScreen(onBack = { navController.popBackStack() })
                    }
                    composable("history") {
                        BatteryHistoryScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions())
    }

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions().all {
        checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * ANC control needs the *classic* BluetoothDevice for the already-paired earbuds (see
     * [io.github.vysockiymark01_prog.airpods.ble.AacpController]). We find it among bonded
     * devices by matching audio-capable devices — a name-based heuristic ("airpods"/"beats"),
     * since Android doesn't expose a "this is the currently connected A2DP device" API without
     * an extra BluetoothProfile.ServiceListener round-trip, which is wired up here.
     */
    private fun attachPairedAirPodsIfAny() {
        val hasConnectPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasConnectPermission) return
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        val candidate = runCatching { adapter.bondedDevices }.getOrNull()
            ?.firstOrNull { device ->
                val name = runCatching { device.name }.getOrNull()?.lowercase() ?: return@firstOrNull false
                "airpods" in name || "beats" in name
            }
        candidate?.let { viewModel.attachDevice(it) }
    }
}
