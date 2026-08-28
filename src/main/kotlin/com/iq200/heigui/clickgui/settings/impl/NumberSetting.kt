package com.iq200.heigui.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.clickgui.GuiTheme
import com.iq200.heigui.clickgui.settings.RenderableSetting
import com.iq200.heigui.clickgui.settings.Saving
import com.iq200.heigui.clickgui.widget.isOver
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.render.Corners
import com.iq200.heigui.utils.render.circle
import com.iq200.heigui.utils.render.roundedRect
import com.iq200.heigui.utils.render.roundedRectClipped
import com.iq200.heigui.utils.ui.animations.Fade
import com.iq200.heigui.utils.ui.animations.Tween
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Setting that lets you pick a number between a range, or edit it directly by clicking.
 */
@Suppress("UNCHECKED_CAST")
class NumberSetting<E>(
    name: String,
    override val default: E = 1.0 as E,
    range: ClosedFloatingPointRange<Double>,
    increment: Number = 1,
    desc: String,
    private val unit: String = ""
) : RenderableSetting<E>(name, desc, GuiTheme.ROW_HEIGHT + EXTRA_HEIGHT), Saving where E : Number, E : Comparable<E> {

    constructor(
        name: String,
        default: E,
        range: IntRange,
        increment: Number = 1,
        desc: String,
        unit: String = ""
    ) : this(name, default, range.first.toDouble()..range.last.toDouble(), increment, desc, unit)

    companion object {
        var activeSetting: NumberSetting<*>? = null
        private const val EXTRA_HEIGHT = 8
        private const val VALUE_PAD = 4
        private const val TRACK_OFFSET = 18
        private const val TRACK_HEIGHT = 6
        private const val TRACK_RADIUS = 3f
        private const val KNOB_RADIUS = 4f
        private const val KNOB_GROWTH = 1.5f
        private const val SLIDE_DURATION = 100L
        private const val GROW_DURATION = 150L
    }

    private val step = increment.toDouble()
    private val minimum = range.start
    private val maximum = range.endInclusive

    override var value: E = default
        set(value) {
            field = (round(value.toDouble() / step) * step).coerceIn(minimum, maximum) as E
            display = format(field)
        }

    var display: String = format(default)
        private set

    private val sliderAnim = Tween(SLIDE_DURATION)
    private val knobGrow = Fade(GROW_DURATION)

    private var dragging = false
    private var dragged = false

    // ===== 自訂的文字輸入狀態 =====
    var isEditing = false
    private var inputText = ""
    private var cursorIndex = 0

    init {
        value = default
        sliderAnim.snap(percent)
    }

    var percent: Float
        get() = ((value.toDouble() - minimum) / (maximum - minimum)).toFloat()
        set(percent) {
            value = (minimum + percent.coerceIn(0f, 1f) * (maximum - minimum)) as E
        }

    private fun format(value: E): String {
        val current = value.toDouble()
        return if (current % 1.0 == 0.0) "${current.toInt()}$unit"
        else "${(current * 100).roundToInt() / 100.0}$unit"
    }

    // 當進入編輯模式時，我們只需要純數字（不要把 % 或 px 等單位帶入編輯框）
    private fun getRawNumberDisplay(): String {
        val current = value.toDouble()
        return if (current % 1.0 == 0.0) "${current.toInt()}"
        else "${(current * 100).roundToInt() / 100.0}"
    }

    fun nudge(steps: Int) {
        value = (value.toDouble() + steps * step).coerceIn(minimum, maximum) as E
    }

    override fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        drawLabel(graphics)

        // 決定要渲染的文字（輸入中 vs 平常顯示）
        val currentText = if (isEditing) inputText else display
        val textWidth = mc.font.width(currentText)
        val textX = x + width - textWidth - VALUE_PAD
        val textY = GuiTheme.textY(y, GuiTheme.ROW_HEIGHT)

        if (isEditing) {
            // 畫出輸入框的深色背景
            val boxPad = 2
            graphics.roundedRect(
                textX - boxPad,
                y + 4,
                textX + textWidth + boxPad,
                y + GuiTheme.ROW_HEIGHT - 4,
                Colors.MINECRAFT_DARK_GRAY.rgba,
                3f
            )

            // 核心：動態計算閃爍游標的精準位置
            if ((System.currentTimeMillis() % 1000) > 500) {
                val textBeforeCursor = currentText.substring(0, cursorIndex.coerceIn(0, currentText.length))
                val cursorOffset = mc.font.width(textBeforeCursor)
                // 使用極細的 roundedRect 當作游標
                graphics.roundedRect(
                    textX + cursorOffset,
                    textY - 1,
                    textX + cursorOffset + 1,
                    textY + mc.font.lineHeight - 1,
                    Colors.WHITE.rgba,
                    0f
                )
            }
        }

        graphics.text(mc.font, currentText, textX, textY, Colors.WHITE.rgba, false)

        val trackX = x + GuiTheme.PADDING
        val trackY = y + TRACK_OFFSET
        val trackWidth = width - GuiTheme.PADDING * 2
        graphics.roundedRect(trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT, GuiTheme.surface.rgba, TRACK_RADIUS)

        if (dragged) sliderAnim.snap(percent) else sliderAnim.target(percent)
        val filled = (sliderAnim.value * trackWidth).roundToInt()
        if (filled > 0) {
            graphics.roundedRectClipped(
                trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT,
                trackX, trackY, trackX + filled, trackY + TRACK_HEIGHT,
                GuiTheme.accent.rgba, Corners(TRACK_RADIUS)
            )
        }

        val overSlider = dragging || isOver(mouseX, mouseY, x, y + height / 2, width, height / 2)
        val radius = knobGrow.lerp(overSlider, KNOB_RADIUS, KNOB_RADIUS + KNOB_GROWTH)
        graphics.circle(trackX + filled, trackY + TRACK_HEIGHT / 2, radius, Colors.WHITE.rgba)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()

        val currentText = if (isEditing) inputText else display
        val textWidth = mc.font.width(currentText)
        val textX = x + width - textWidth - VALUE_PAD

        // 判定滑鼠是否點擊在文字區域
        val isHoveringText = mouseX >= textX - 5 && mouseX <= x + width && mouseY >= y && mouseY <= y + GuiTheme.ROW_HEIGHT

        // 如果點擊文字以外的地方，儲存並退出編輯模式
        if (isEditing && (!isHoveringText || event.button() != 0)) {
            saveInput()
            isEditing = false
            if (activeSetting == this) activeSetting = null
        }

        if (event.button() == 0 && isHoveringText) {
            if (activeSetting != null && activeSetting != this) {
                activeSetting!!.saveInput()
                activeSetting!!.isEditing = false
            }
            activeSetting = this

            if (!isEditing) {
                isEditing = true
                inputText = getRawNumberDisplay()
                cursorIndex = inputText.length // 預設把游標放在最後面
            } else {
                // 核心：滑鼠點擊時，精準尋找離滑鼠最近的游標位置
                var bestIndex = 0
                var minDiff = Int.MAX_VALUE
                for (i in 0..inputText.length) {
                    val subWidth = mc.font.width(inputText.substring(0, i))
                    val cx = textX + subWidth
                    val diff = abs(mouseX - cx)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestIndex = i
                    }
                }
                cursorIndex = bestIndex
            }
            return // 提早 return 避免觸發拖拉邏輯
        }

        // Odin 原本的防護：點擊上半部不觸發拉桿
        if (mouseY < y + height / 2) return

        dragging = true
        dragged = false
        seek(mouseX)
    }

    override fun onDrag(event: MouseButtonEvent, dragX: Double, dragY: Double) {
        if (!dragging) return
        dragged = true
        seek(event.x().toInt())
    }

    override fun onRelease(event: MouseButtonEvent) {
        dragging = false
        dragged = false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (isEditing) {
            when (event.key) {
                GLFW.GLFW_KEY_LEFT -> if (cursorIndex > 0) cursorIndex--
                GLFW.GLFW_KEY_RIGHT -> if (cursorIndex < inputText.length) cursorIndex++
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursorIndex > 0) {
                        inputText = inputText.removeRange(cursorIndex - 1, cursorIndex)
                        cursorIndex--
                    }
                }
                GLFW.GLFW_KEY_DELETE -> {
                    if (cursorIndex < inputText.length) {
                        inputText = inputText.removeRange(cursorIndex, cursorIndex + 1)
                    }
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                    saveInput()
                    isEditing = false
                    if (activeSetting == this) activeSetting = null
                }
            }
            return true
        }

        val steps = when (event.key) {
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_EQUAL -> 1
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_MINUS -> -1
            else -> return false
        }
        nudge(steps)
        return true
    }

    // 需確保你的事件系統會將鍵盤輸入導向這裡 (對應 CharacterEvent)
    fun keyTyped(event: CharacterEvent): Boolean {
        if (isEditing) {
            val c = event.codepoint.toChar()
            if (c.isDigit() || c == '.' || c == '-') {
                inputText = inputText.substring(0, cursorIndex) + c + inputText.substring(cursorIndex)
                cursorIndex++
            }
            return true
        }
        return false
    }

    fun saveInput() {
        val parsed = inputText.toDoubleOrNull()
        if (parsed != null) {
            value = parsed.coerceIn(minimum, maximum) as E
        }
    }

    override fun release() {
        dragging = false
        dragged = false
    }

    private fun seek(mouseX: Int) {
        val trackX = x + GuiTheme.PADDING
        val trackWidth = width - GuiTheme.PADDING * 2
        percent = (mouseX - trackX).toFloat() / trackWidth
    }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement, gson: Gson) {
        element.asNumber?.let { value = it as E }
    }

}