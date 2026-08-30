package io.github.vysockiymark01_prog.airpods.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import io.github.vysockiymark01_prog.airpods.ble.AirPodsModel

/**
 * Small line-art silhouette for [model].
 *
 * This is deliberately NOT a reproduction of Apple's product photography or renders — those are
 * copyrighted and this app has no license to redistribute them. Instead this draws a simplified
 * illustration that distinguishes the three shapes Apple actually ships, using the same two flags
 * the rest of the app already keys off of to tell the families apart:
 *  - [AirPodsModel.hasStem] == false → over-ear headphones (AirPods Max).
 *  - hasStem && [AirPodsModel.supportsAnc] → stemmed earbud WITH a silicone ear tip (the Pro line
 *    and AirPods 4 ANC — these are the only stemmed models with a tip).
 *  - hasStem && !supportsAnc → plain stemmed earbud, no tip (AirPods 1st-4th gen, and the
 *    generic/unrecognized-model fallback).
 */
@Composable
fun AirPodsModelIcon(
    model: AirPodsModel,
    modifier: Modifier = Modifier.size(48.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.07f)
        when {
            !model.hasStem -> drawMaxHeadphones(tint, stroke)
            model.supportsAnc -> drawProEarbud(tint, stroke)
            else -> drawRegularEarbud(tint, stroke)
        }
    }
}

private fun DrawScope.drawRegularEarbud(tint: Color, stroke: Stroke) {
    val podCenter = Offset(size.width * 0.42f, size.height * 0.34f)
    val podRadius = Size(size.width * 0.22f, size.height * 0.20f)
    drawOval(
        color = tint,
        topLeft = Offset(podCenter.x - podRadius.width, podCenter.y - podRadius.height),
        size = Size(podRadius.width * 2, podRadius.height * 2),
        style = stroke,
    )
    rotate(degrees = 25f, pivot = podCenter) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(podCenter.x - size.width * 0.05f, podCenter.y),
            size = Size(size.width * 0.10f, size.height * 0.55f),
            cornerRadius = CornerRadius(size.width * 0.05f, size.width * 0.05f),
            style = stroke,
        )
    }
}

private fun DrawScope.drawProEarbud(tint: Color, stroke: Stroke) {
    drawRegularEarbud(tint, stroke)
    // Silicone ear tip: a small bump below/left of the pod head — the one visual detail that
    // actually separates the Pro line from plain AirPods.
    val tipCenter = Offset(size.width * 0.33f, size.height * 0.50f)
    drawOval(
        color = tint,
        topLeft = Offset(tipCenter.x - size.width * 0.11f, tipCenter.y - size.height * 0.09f),
        size = Size(size.width * 0.22f, size.height * 0.18f),
        style = stroke,
    )
}

private fun DrawScope.drawMaxHeadphones(tint: Color, stroke: Stroke) {
    val cupRadius = Size(size.width * 0.16f, size.height * 0.22f)
    val leftCenter = Offset(size.width * 0.24f, size.height * 0.62f)
    val rightCenter = Offset(size.width * 0.76f, size.height * 0.62f)

    // Headband
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.14f, size.height * 0.12f),
        size = Size(size.width * 0.72f, size.height * 0.62f),
        style = stroke,
    )
    // Connectors from headband down to each ear cup
    drawLine(
        color = tint,
        start = Offset(leftCenter.x, size.height * 0.44f),
        end = Offset(leftCenter.x, leftCenter.y - cupRadius.height),
        strokeWidth = stroke.width,
    )
    drawLine(
        color = tint,
        start = Offset(rightCenter.x, size.height * 0.44f),
        end = Offset(rightCenter.x, rightCenter.y - cupRadius.height),
        strokeWidth = stroke.width,
    )
    // Ear cups
    drawOval(
        color = tint,
        topLeft = Offset(leftCenter.x - cupRadius.width, leftCenter.y - cupRadius.height),
        size = Size(cupRadius.width * 2, cupRadius.height * 2),
        style = stroke,
    )
    drawOval(
        color = tint,
        topLeft = Offset(rightCenter.x - cupRadius.width, rightCenter.y - cupRadius.height),
        size = Size(cupRadius.width * 2, cupRadius.height * 2),
        style = stroke,
    )
}
