package com.iq200.heigui.utils.render

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class Corners(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
) {
    constructor(radius: Float) : this(radius, radius, radius, radius)

    companion object {
        val NONE = Corners(0f)
    }
}

/**
 * 26.1.2 compatibility facade for the ClickGUI renderer.
 *
 * Minecraft 26.3 can submit rounded rectangles as ordinary GUI elements.
 * In 26.1.2 the equivalent implementation is the existing PIP renderer, so
 * the UI-facing API stays the same while drawing through the older backend.
 */
object RoundedRectRenderer {

    fun submit(
        context: GuiGraphicsExtractor,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        topLeftColor: Int,
        topRightColor: Int,
        bottomRightColor: Int,
        bottomLeftColor: Int,
        corners: Corners,
        outlineWidth: Float,
    ) {
        RoundRectPIPRenderer.submit(
            context,
            floor(x0).toInt(),
            floor(y0).toInt(),
            ceil(x1).toInt(),
            ceil(y1).toInt(),
            topLeftColor,
            topRightColor,
            bottomRightColor,
            bottomLeftColor,
            corners.topLeft,
            corners.topRight,
            corners.bottomRight,
            corners.bottomLeft,
            topLeftColor,
            outlineWidth,
        )
    }

    fun submitShadow(
        context: GuiGraphicsExtractor,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Int,
        corners: Corners,
        blur: Float,
    ) {
        val layers = 4
        val originalAlpha = color ushr 24 and 0xFF
        for (layer in layers downTo 1) {
            val progress = layer / layers.toFloat()
            val expansion = blur * progress
            val alpha = (originalAlpha * (1f - progress * 0.7f) / layers).roundToInt().coerceIn(0, 255)
            val layerColor = color and 0x00FFFFFF or (alpha shl 24)
            submit(
                context,
                x0 - expansion,
                y0 - expansion,
                x1 + expansion,
                y1 + expansion,
                layerColor,
                layerColor,
                layerColor,
                layerColor,
                Corners(
                    corners.topLeft + expansion,
                    corners.topRight + expansion,
                    corners.bottomRight + expansion,
                    corners.bottomLeft + expansion,
                ),
                0f,
            )
        }
    }
}
