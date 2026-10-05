package com.iq200.heigui.utils.render

import com.iq200.heigui.utils.render.CustomRenderPipelines.PIPELINE_ROUND_RECT
import com.iq200.heigui.utils.render.CustomRenderPipelines.PIPELINE_ROUND_RECT_SHADOW
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

/** Draws rounded rectangles directly into Minecraft's GUI element batch. */
object RoundedRectRenderer {

    private const val AA_PADDING = 2f
    private const val HALF_EXTENT_SCALE = 8f
    private const val RADIUS_SCALE = 4f
    private const val MAX_RADIUS = 255f / RADIUS_SCALE
    private const val ALPHA_MASK = 0xFF shl 24

    val FORMAT: VertexFormat = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1)
        .add("UV2", VertexFormatElement.UV2)
        .add("LineWidth", VertexFormatElement.LINE_WIDTH)
        .build()

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
    ) = emit(
        context, x0, y0, x1, y1,
        topLeftColor, topRightColor, bottomRightColor, bottomLeftColor,
        corners, outlineWidth, AA_PADDING, PIPELINE_ROUND_RECT,
    )

    fun submitShadow(
        context: GuiGraphicsExtractor,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Int,
        corners: Corners,
        blur: Float,
    ) = emit(
        context, x0, y0, x1, y1,
        color, color, color, color,
        corners, blur, AA_PADDING + blur, PIPELINE_ROUND_RECT_SHADOW,
    )

    private fun emit(
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
        edgeWidth: Float,
        padding: Float,
        pipeline: RenderPipeline,
    ) {
        if ((topLeftColor or topRightColor or bottomRightColor or bottomLeftColor) and ALPHA_MASK == 0) return

        val left = min(x0, x1)
        val top = min(y0, y1)
        val right = max(x0, x1)
        val bottom = max(y0, y1)
        val halfWidth = (right - left) * 0.5f
        val halfHeight = (bottom - top) * 0.5f
        if (halfWidth <= 0f || halfHeight <= 0f) return

        val quadX0 = floor(left - padding).toInt()
        val quadY0 = floor(top - padding).toInt()
        val quadX1 = ceil(right + padding).toInt()
        val quadY1 = ceil(bottom + padding).toInt()
        if (quadX0 >= quadX1 || quadY0 >= quadY1) return

        val pose = Matrix3x2f(context.pose())
        val scissor = context.scissorStack.peek()
        val quad = ScreenRectangle(quadX0, quadY0, quadX1 - quadX0, quadY1 - quadY0)
            .transformMaxBounds(pose)
        val bounds = if (scissor != null) scissor.intersection(quad) ?: return else quad
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        context.guiRenderState.addGuiElement(
            Element(
                pose,
                quadX0.toFloat(), quadY0.toFloat(), quadX1.toFloat(), quadY1.toFloat(),
                (left + right) * 0.5f, (top + bottom) * 0.5f,
                topLeftColor, topRightColor, bottomRightColor, bottomLeftColor,
                fixedPoint(halfWidth), fixedPoint(halfHeight),
                packRadius(corners.topLeft) or (packRadius(corners.topRight) shl 8),
                packRadius(corners.bottomRight) or (packRadius(corners.bottomLeft) shl 8),
                edgeWidth, pipeline, scissor, bounds,
            )
        )
    }

    private fun fixedPoint(value: Float): Int =
        (value * HALF_EXTENT_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

    private fun packRadius(radius: Float): Int =
        (radius.coerceIn(0f, MAX_RADIUS) * RADIUS_SCALE).roundToInt().coerceIn(0, 255)

    private class Element(
        private val pose: Matrix3x2fc,
        private val x0: Float,
        private val y0: Float,
        private val x1: Float,
        private val y1: Float,
        private val centerX: Float,
        private val centerY: Float,
        private val topLeftColor: Int,
        private val topRightColor: Int,
        private val bottomRightColor: Int,
        private val bottomLeftColor: Int,
        private val packedHalfWidth: Int,
        private val packedHalfHeight: Int,
        private val packedRadiiTop: Int,
        private val packedRadiiBottom: Int,
        private val edgeWidth: Float,
        private val pipeline: RenderPipeline,
        private val scissorArea: ScreenRectangle?,
        private val bounds: ScreenRectangle,
    ) : GuiElementRenderState {

        override fun pipeline() = pipeline
        override fun textureSetup() = TextureSetup.noTexture()
        override fun scissorArea() = scissorArea
        override fun bounds() = bounds

        override fun buildVertices(consumer: VertexConsumer) {
            vertex(consumer, x0, y0, topLeftColor)
            vertex(consumer, x0, y1, bottomLeftColor)
            vertex(consumer, x1, y1, bottomRightColor)
            vertex(consumer, x1, y0, topRightColor)
        }

        private fun vertex(consumer: VertexConsumer, x: Float, y: Float, color: Int) {
            consumer.addVertexWith2DPose(pose, x, y)
                .setColor(color)
                .setUv(x - centerX, y - centerY)
                .setUv1(packedHalfWidth, packedHalfHeight)
                .setUv2(packedRadiiTop, packedRadiiBottom)
                .setLineWidth(edgeWidth)
        }
    }
}
