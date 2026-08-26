package io.github.vysockiymark01_prog.airpods.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.vysockiymark01_prog.airpods.ble.BatteryLevel
import io.github.vysockiymark01_prog.airpods.ui.theme.BatteryColors

/**
 * Circular battery gauge with a smooth fill transition (~600ms, per spec's 500-800ms target) and
 * a gentle pulse while charging.
 */
@Composable
fun BatteryIndicator(
    label: String,
    level: BatteryLevel,
    charging: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 88.dp,
) {
    val percent = (level as? BatteryLevel.Percent)?.value ?: 0
    val animatedFraction by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(durationMillis = 650, easing = LinearEasing),
        label = "batteryFill",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "chargePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val ringColor = if (level is BatteryLevel.Unavailable) {
        MaterialTheme.colorScheme.surfaceVariant
    } else if (charging) {
        BatteryColors.charging
    } else {
        BatteryColors.forPercent(percent)
    }
    val effectiveAlpha = if (charging) pulseAlpha else 1f

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .width(size)
                .height(size)
                .clip(RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.width(size).height(size)) {
                val strokeWidth = size.toPx() * 0.12f
                drawArc(
                    color = ringColor.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                )
                if (level !is BatteryLevel.Unavailable) {
                    drawArc(
                        color = ringColor.copy(alpha = effectiveAlpha),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedFraction,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
            Text(
                text = if (level is BatteryLevel.Percent) "${level.value}%" else "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
