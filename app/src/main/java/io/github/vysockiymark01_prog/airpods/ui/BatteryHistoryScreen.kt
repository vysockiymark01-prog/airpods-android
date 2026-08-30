package io.github.vysockiymark01_prog.airpods.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.vysockiymark01_prog.airpods.ble.BatteryHistoryStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple "заряд со временем" trend for the last few days — a self-drawn line chart (Canvas), no
 * charting library, since three lines on a percentage axis is about as far as this needs to go.
 * Backed by [BatteryHistoryStore], which the foreground service appends to every ~5 minutes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<BatteryHistoryStore.Entry>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            entries = BatteryHistoryStore.readAll(context)
            delay(30_000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История заряда") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
        ) {
            if (entries.size < 2) {
                Text(
                    "Пока недостаточно данных для графика — новая точка записывается примерно " +
                        "раз в 5 минут, пока наушники рядом. Загляните сюда чуть позже.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                return@Scaffold
            }

            Legend()
            Spacer(Modifier.height(16.dp))
            HistoryChart(entries, modifier = Modifier.fillMaxWidth().height(240.dp))
            Spacer(Modifier.height(8.dp))

            val formatter = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatter.format(Date(entries.first().timestampMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    formatter.format(Date(entries.last().timestampMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem("Левый", LEFT_COLOR)
        LegendItem("Правый", RIGHT_COLOR)
        LegendItem("Кейс", CASE_COLOR)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HistoryChart(entries: List<BatteryHistoryStore.Entry>, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val gridStroke = Stroke(width = 1.dp.toPx())
        // Horizontal gridlines at 0/25/50/75/100%
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val y = size.height * (1f - fraction)
            drawLine(
                color = onSurface.copy(alpha = 0.12f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = gridStroke.width,
            )
        }

        fun plot(color: Color, valueOf: (BatteryHistoryStore.Entry) -> Int?) {
            val points = entries.mapIndexedNotNull { index, entry ->
                val value = valueOf(entry) ?: return@mapIndexedNotNull null
                val x = size.width * index / (entries.size - 1).coerceAtLeast(1)
                val y = size.height * (1f - value.coerceIn(0, 100) / 100f)
                Offset(x, y)
            }
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = color,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2.5.dp.toPx(),
                )
            }
        }

        plot(LEFT_COLOR) { it.left }
        plot(RIGHT_COLOR) { it.right }
        plot(CASE_COLOR) { it.case }
    }
}

private val LEFT_COLOR = Color(0xFF4CAF50)
private val RIGHT_COLOR = Color(0xFF2196F3)
private val CASE_COLOR = Color(0xFFFF9800)
