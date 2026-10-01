package com.iq200.heigui.utils.ui.rendering

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.Color.Companion.alpha
import com.iq200.heigui.utils.render.Corners
import com.iq200.heigui.utils.render.RoundedRectRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix3x2f
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * All drawing is submitted through Minecraft's GUI render state, so it works
 * with both the OpenGL and Vulkan RenderPearl backends.
 */
enum class GuiIcon {
    HUE_GRADIENT,
    CHEVRON,
    MOVEMENT
}

object GuiRenderer {

    private const val MINECRAFT_FONT_SIZE = 9f

    private var context: GuiGraphicsExtractor? = null
    private var globalAlpha = 1f

    inline fun render(context: GuiGraphicsExtractor, block: () -> Unit) {
        begin(context)
        try {
            block()
        } finally {
            end()
        }
    }

    fun begin(context: GuiGraphicsExtractor) {
        check(this.context == null) { "[GuiRenderer] Already rendering" }
        this.context = context
        globalAlpha = 1f
        context.pose().pushMatrix()
    }

    fun end() {
        val context = requireContext()
        context.pose().popMatrix()
        this.context = null
        globalAlpha = 1f
    }

    fun devicePixelRatio(): Float {
        val window = mc.window
        return if (window.screenWidth == 0) 1f else window.width.toFloat() / window.screenWidth.toFloat()
    }

    fun push() = requireContext().pose().pushMatrix()

    fun pop() = requireContext().pose().popMatrix()

    fun scale(x: Float, y: Float) = requireContext().pose().scale(x, y)

    fun translate(x: Float, y: Float) = requireContext().pose().translate(x, y)

    fun rotate(amount: Float) = requireContext().pose().rotate(amount)

    fun globalAlpha(amount: Float) {
        globalAlpha = amount.coerceIn(0f, 1f)
    }

    fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        val context = requireContext()
        val left = floor(x).toInt()
        val top = floor(y).toInt()
        val right = ceil(x + w).toInt()
        val bottom = ceil(y + h).toInt()
        context.scissorStack.push(
            ScreenRectangle(left, top, right - left, bottom - top).transformMaxBounds(context.pose())
        )
    }

    fun popScissor() = requireContext().disableScissor()

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int) {
        val context = requireContext()
        val dx = x2 - x1
        val dy = y2 - y1
        val length = hypot(dx, dy)
        if (length <= 0f || thickness <= 0f) return

        context.pose().pushMatrix()
        context.pose().translate(x1, y1)
        context.pose().mul(Matrix3x2f().identity().rotate(atan2(dy, dx)))
        val actual = applyAlpha(color)
        RoundedRectRenderer.submit(
            context, 0f, -thickness / 2f, length, thickness / 2f,
            actual, actual, actual, actual,
            Corners(thickness / 2f), 0f
        )
        context.pose().popMatrix()
    }

    fun drawHalfRoundedRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        radius: Float,
        roundTop: Boolean
    ) {
        val corners = if (roundTop) Corners(radius, radius, 0f, 0f) else Corners(0f, 0f, radius, radius)
        roundedRect(x, y, w, h, color, corners)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float) {
        roundedRect(x, y, w, h, color, Corners(radius))
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        roundedRect(x, y, w, h, color, Corners.NONE)
    }

    fun hollowRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int, radius: Float) {
        val actual = applyAlpha(color)
        RoundedRectRenderer.submit(
            requireContext(), x, y, x + w, y + h,
            actual, actual, actual, actual,
            Corners(radius), thickness
        )
    }

    fun gradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color1: Int,
        color2: Int,
        gradient: Gradient,
        radius: Float
    ) {
        val first = applyAlpha(color1)
        val second = applyAlpha(color2)
        val colors = when (gradient) {
            Gradient.LeftToRight -> intArrayOf(first, second, second, first)
            Gradient.TopToBottom -> intArrayOf(first, first, second, second)
        }
        RoundedRectRenderer.submit(
            requireContext(), x, y, x + w, y + h,
            colors[0], colors[1], colors[2], colors[3],
            Corners(radius), 0f
        )
    }

    fun dropShadow(x: Float, y: Float, width: Float, height: Float, blur: Float, spread: Float, radius: Float) {
        RoundedRectRenderer.submitShadow(
            requireContext(),
            x - spread, y - spread, x + width + spread, y + height + spread,
            applyAlpha(0x7D000000), Corners(radius + spread), blur
        )
    }

    fun circle(x: Float, y: Float, radius: Float, color: Int) {
        roundedRect(x - radius, y - radius, radius * 2f, radius * 2f, color, Corners(radius))
    }

    fun text(text: String, x: Float, y: Float, size: Float, color: Int) {
        drawText(text, x, y, size, color, false)
    }

    fun textShadow(text: String, x: Float, y: Float, size: Float, color: Int) {
        drawText(text, x, y, size, color, true)
    }

    fun verticallyCenteredText(
        text: String,
        x: Float,
        top: Float,
        height: Float,
        size: Float,
        color: Int
    ) {
        val y = if (StbFontRenderer.supports(text) && StbFontRenderer.isAvailable()) {
            val (glyphTop, glyphBottom) = StbFontRenderer.verticalBounds(text, size)
            top + (height - (glyphBottom - glyphTop)) / 2f - glyphTop
        } else {
            top + (height - size) / 2f
        }
        text(text, x, y, size, color)
    }

    fun textWidth(text: String, size: Float): Float {
        if (StbFontRenderer.supports(text) && StbFontRenderer.isAvailable()) {
            return StbFontRenderer.width(text, size)
        }
        return mc.font.width(text) * (size / MINECRAFT_FONT_SIZE)
    }

    fun drawWrappedString(
        text: String,
        x: Float,
        y: Float,
        w: Float,
        size: Float,
        color: Int,
        lineHeight: Float = 1f
    ) {
        if (StbFontRenderer.supports(text) && StbFontRenderer.isAvailable()) {
            val lines = StbFontRenderer.wrap(text, w, size)
            val advance = size * lineHeight
            lines.forEachIndexed { index, line ->
                StbFontRenderer.draw(requireContext(), line, x, y + index * advance, size, applyAlpha(color), false)
            }
            return
        }
        val scale = size / MINECRAFT_FONT_SIZE
        val lines = wrappedLines(text, w, scale)
        val advance = size * lineHeight
        lines.forEachIndexed { index, line ->
            drawSequence(line, x, y + index * advance, scale, color, false)
        }
    }

    fun wrappedTextBounds(
        text: String,
        w: Float,
        size: Float,
        lineHeight: Float = 1f
    ): FloatArray {
        if (StbFontRenderer.supports(text) && StbFontRenderer.isAvailable()) {
            val lines = StbFontRenderer.wrap(text, w, size)
            val width = lines.maxOfOrNull { StbFontRenderer.width(it, size) } ?: 0f
            val height = if (lines.isEmpty()) 0f else size + (lines.size - 1) * size * lineHeight
            return floatArrayOf(0f, 0f, width, height)
        }
        val scale = size / MINECRAFT_FONT_SIZE
        val lines = wrappedLines(text, w, scale)
        val width = lines.maxOfOrNull { mc.font.width(it) * scale } ?: 0f
        val height = if (lines.isEmpty()) 0f else size + (lines.size - 1) * size * lineHeight
        return floatArrayOf(0f, 0f, width, height)
    }

    fun image(icon: GuiIcon, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        when (icon) {
            GuiIcon.HUE_GRADIENT -> drawHueGradient(x, y, w, h, radius)
            GuiIcon.CHEVRON -> drawChevron(x, y, w, h)
            GuiIcon.MOVEMENT -> drawMovementIcon(x, y, w, h)
        }
    }

    fun image(icon: GuiIcon, x: Float, y: Float, w: Float, h: Float) = image(icon, x, y, w, h, 0f)

    private fun roundedRect(x: Float, y: Float, w: Float, h: Float, color: Int, corners: Corners) {
        val actual = applyAlpha(color)
        RoundedRectRenderer.submit(
            requireContext(), x, y, x + w, y + h,
            actual, actual, actual, actual,
            corners, 0f
        )
    }

    private fun drawText(text: String, x: Float, y: Float, size: Float, color: Int, shadow: Boolean) {
        if (StbFontRenderer.draw(requireContext(), text, x, y, size, applyAlpha(color), shadow)) return
        val scale = size / MINECRAFT_FONT_SIZE
        drawSequence(Component.literal(text).visualOrderText, x, y, scale, color, shadow)
    }

    private fun drawSequence(
        text: FormattedCharSequence,
        x: Float,
        y: Float,
        scale: Float,
        color: Int,
        shadow: Boolean
    ) {
        val context = requireContext()
        context.pose().pushMatrix()
        context.pose().translate(x, y)
        context.pose().scale(scale, scale)
        context.text(mc.font, text, 0, 0, applyAlpha(color), shadow)
        context.pose().popMatrix()
    }

    private fun wrappedLines(text: String, width: Float, scale: Float) =
        mc.font.split(Component.literal(text), max(1, floor(width / scale).toInt()))

    private fun drawHueGradient(x: Float, y: Float, w: Float, h: Float, radius: Float) {
        val stops = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
            0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
        )
        val segmentWidth = w / (stops.size - 1)
        for (index in 0 until stops.lastIndex) {
            val left = x + segmentWidth * index
            val right = if (index == stops.lastIndex - 1) x + w else left + segmentWidth
            val corners = Corners(
                if (index == 0) radius else 0f,
                if (index == stops.lastIndex - 1) radius else 0f,
                if (index == stops.lastIndex - 1) radius else 0f,
                if (index == 0) radius else 0f
            )
            RoundedRectRenderer.submit(
                requireContext(), left, y, right, y + h,
                applyAlpha(stops[index]), applyAlpha(stops[index + 1]),
                applyAlpha(stops[index + 1]), applyAlpha(stops[index]),
                corners, 0f
            )
        }
    }

    private fun drawChevron(x: Float, y: Float, w: Float, h: Float) {
        val thickness = max(1f, minOf(w, h) * 0.12f)
        line(x + w * 0.32f, y + h * 0.18f, x + w * 0.68f, y + h * 0.5f, thickness, -1)
        line(x + w * 0.68f, y + h * 0.5f, x + w * 0.32f, y + h * 0.82f, thickness, -1)
    }

    private fun drawMovementIcon(x: Float, y: Float, w: Float, h: Float) {
        val centerX = x + w / 2f
        val centerY = y + h / 2f
        val size = minOf(w, h)
        val inset = size * 0.12f
        val headDepth = size * 0.18f
        val headHalfWidth = size * 0.15f
        val thickness = max(1f, size * 0.07f)
        val color = 0xECFFFFFF.toInt()

        val top = y + inset
        val bottom = y + h - inset
        val left = x + inset
        val right = x + w - inset

        line(centerX, top, centerX, bottom, thickness, color)
        line(left, centerY, right, centerY, thickness, color)

        line(centerX, top, centerX - headHalfWidth, top + headDepth, thickness, color)
        line(centerX, top, centerX + headHalfWidth, top + headDepth, thickness, color)
        line(centerX, bottom, centerX - headHalfWidth, bottom - headDepth, thickness, color)
        line(centerX, bottom, centerX + headHalfWidth, bottom - headDepth, thickness, color)

        line(left, centerY, left + headDepth, centerY - headHalfWidth, thickness, color)
        line(left, centerY, left + headDepth, centerY + headHalfWidth, thickness, color)
        line(right, centerY, right - headDepth, centerY - headHalfWidth, thickness, color)
        line(right, centerY, right - headDepth, centerY + headHalfWidth, thickness, color)
    }

    private fun applyAlpha(color: Int): Int {
        if (globalAlpha >= 1f) return color
        val alpha = (color.alpha * globalAlpha).roundToInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun requireContext(): GuiGraphicsExtractor =
        context ?: error("[GuiRenderer] Drawing outside GuiRenderer.render")
}
