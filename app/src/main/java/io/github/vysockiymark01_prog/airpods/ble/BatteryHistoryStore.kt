package io.github.vysockiymark01_prog.airpods.ble

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A minimal on-disk log of battery readings over time, for the "Заряд со временем" history
 * screen. Deliberately a flat CSV file rather than a database — the data is tiny (one line per
 * sample, sampled coarsely) and this avoids pulling in a persistence library just for a simple
 * time series.
 *
 * Sampling is throttled to [MIN_INTERVAL_MS] regardless of how often BLE readings arrive, so the
 * file stays small and the resulting graph shows a real multi-day trend instead of noise; the
 * file is also hard-capped at [MAX_ENTRIES] lines, oldest dropped first.
 */
object BatteryHistoryStore {

    data class Entry(val timestampMs: Long, val left: Int?, val right: Int?, val case: Int?)

    private const val FILE_NAME = "battery_history.csv"
    private const val MIN_INTERVAL_MS = 5 * 60_000L
    private const val MAX_ENTRIES = 2000

    @Volatile
    private var lastRecordedAtMs = 0L

    suspend fun record(context: Context, status: AirPodsStatus) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastRecordedAtMs < MIN_INTERVAL_MS) return@withContext
        lastRecordedAtMs = now

        val line = "$now,${percentOrBlank(status.leftBattery)},${percentOrBlank(status.rightBattery)}," +
            "${percentOrBlank(status.caseBattery)}\n"
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            file.appendText(line)
            trimIfTooLong(file)
        }
    }

    suspend fun readAll(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> parseLine(line) }
        }.getOrDefault(emptyList())
    }

    private fun parseLine(line: String): Entry? {
        val parts = line.split(",")
        if (parts.size != 4) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        return Entry(
            timestampMs = timestamp,
            left = parts[1].toIntOrNull(),
            right = parts[2].toIntOrNull(),
            case = parts[3].toIntOrNull(),
        )
    }

    private fun percentOrBlank(level: BatteryLevel): String =
        (level as? BatteryLevel.Percent)?.value?.toString() ?: ""

    private fun trimIfTooLong(file: File) {
        val lines = file.readLines()
        if (lines.size > MAX_ENTRIES) {
            file.writeText(lines.takeLast(MAX_ENTRIES).joinToString("\n") + "\n")
        }
    }
}
