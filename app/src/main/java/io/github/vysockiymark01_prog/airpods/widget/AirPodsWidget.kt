package io.github.vysockiymark01_prog.airpods.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import io.github.vysockiymark01_prog.airpods.ble.AirPodsScanService
import io.github.vysockiymark01_prog.airpods.ble.AirPodsStatus
import io.github.vysockiymark01_prog.airpods.ble.BatteryLevel

/**
 * Home-screen widget showing the latest battery reading without opening the app.
 *
 * Reads [AirPodsScanService.latestStatus] directly — same process, in-memory [kotlinx.coroutines.flow.StateFlow] — rather than
 * any separate storage. [AirPodsScanService] calls [androidx.glance.appwidget.updateAll] on this
 * widget every time a new reading comes in, so it updates live while the service is running;
 * Android's own widget-provider `updatePeriodMillis` (30 minutes, the platform-enforced minimum)
 * is only a fallback in case that push is ever missed.
 */
class AirPodsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val status = AirPodsScanService.latestStatus.value
        provideContent {
            WidgetContent(status)
        }
    }
}

class AirPodsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AirPodsWidget()
}

@Composable
private fun WidgetContent(status: AirPodsStatus?) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        if (status == null) {
            Text("Наушники не найдены")
        } else {
            Text(status.model.displayName)
            Text(
                "Л ${formatBattery(status.leftBattery)} · П ${formatBattery(status.rightBattery)} · " +
                    "Кейс ${formatBattery(status.caseBattery)}",
            )
        }
    }
}

private fun formatBattery(level: BatteryLevel): String = when (level) {
    is BatteryLevel.Percent -> "${level.value}%"
    BatteryLevel.Unavailable -> "н/д"
}
