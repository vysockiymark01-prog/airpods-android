package io.github.vysockiymark01_prog.airpods.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode { LIGHT, DARK, SYSTEM }

// Apple-adjacent neutral palette: near-black/near-white surfaces, a single accent, generous
// contrast in both modes rather than saturated brand colors everywhere ("много воздуха").
private val AccentBlue = Color(0xFF0A84FF)
private val AccentGreen = Color(0xFF30D158) // charging / good battery
private val AccentYellow = Color(0xFFFFD60A) // mid battery
private val AccentRed = Color(0xFFFF453A) // low battery

val LightColors = lightColorScheme(
    primary = AccentBlue,
    background = Color(0xFFF5F5F7),
    surface = Color.White,
    onBackground = Color(0xFF1D1D1F),
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFE8E8ED),
)

val DarkColors = darkColorScheme(
    primary = AccentBlue,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF2C2C2E),
)

object BatteryColors {
    fun forPercent(percent: Int): Color = when {
        percent <= 20 -> AccentRed
        percent <= 50 -> AccentYellow
        else -> AccentGreen
    }
    val charging = AccentGreen
}

@Composable
fun AirPodsTheme(
    themeMode: AppThemeMode,
    dynamicColor: Boolean = false, // off by default — Apple-style look relies on our own fixed palette
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AirPodsTypography,
        content = content,
    )
}
