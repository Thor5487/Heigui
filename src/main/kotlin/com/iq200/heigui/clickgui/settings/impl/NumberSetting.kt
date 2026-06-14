package com.iq200.heigui.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.iq200.heigui.clickgui.ClickGUI.gray38
import com.iq200.heigui.clickgui.Panel
import com.iq200.heigui.clickgui.settings.RenderableSetting
import com.iq200.heigui.clickgui.settings.Saving
import com.iq200.heigui.features.impl.render.ClickGUIModule
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.ui.HoverHandler
import com.iq200.heigui.utils.ui.animations.LinearAnimation
import com.iq200.heigui.utils.ui.isAreaHovered
import com.iq200.heigui.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

@Suppress("UNCHECKED_CAST")
class NumberSetting<E>(
    name: String,
    override val default: E = 1.0 as E,
    min: Number,
    max: Number,
    increment: Number = 1,
    desc: String,
    private val unit: String = ""
) : RenderableSetting<E>(name, desc), Saving where E : Number, E : Comparable<E> {

    companion object {
        var activeSetting: NumberSetting<*>? = null
    }

    private val incrementDouble = increment.toDouble()
    private val minDouble = min.toDouble()
    private var maxDouble = max.toDouble()

    private val sliderAnim = LinearAnimation<Float>(100)
    private val handler = HoverHandler(150)

    private var prevLocation = 0f
    private var isDragging = false

    var isEditing = false
    private var inputText = ""

    // ===== 自己維護的游標位置 =====
    private var cursorIndex = 0

    private var textBoundsLeftX = 0f
    private var textBoundsRightX = 0f
    private var textBoundsY = 0f
    private var textBoundsHeight = 16f

    private var sliderPercentage = 0f
        set(value) {
            if (sliderPercentage != value) {
                if (!isDragging) {
                    prevLocation = sliderAnim.get(prevLocation, sliderPercentage, false)
                    sliderAnim.start()
                }
            }
            field = value
        }

    override var value: E = default
        set(value) {
            field = roundToIncrement(value).coerceIn(minDouble, maxDouble) as E
            sliderPercentage = ((field.toDouble() - minDouble) / (maxDouble - minDouble)).toFloat()
        }

    init {
        value = default
    }

    private var valueDouble
        get() = value.toDouble()
        set(value) {
            this.value = value as E
        }

    private var valueInt
        get() = value.toInt()
        set(value) {
            this.value = value as E
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        handler.handle(x, y + height / 2, width, height / 2, true)

        if (listening && !isEditing) {
            val newPercentage = ((mouseX - (x + 6f)) / (width - 12f)).coerceIn(0f, 1f)
            valueDouble = minDouble + newPercentage * (maxDouble - minDouble)
            sliderPercentage = newPercentage
        }

        val font = NVGRenderer.defaultFont
        val fontSize = 16f
        textBoundsY = y + height / 2f - 17f

        val currentNumberText = if (isEditing) inputText else getNumberDisplay()

        val textWidth = NVGRenderer.textWidth(currentNumberText, fontSize, font)
        val unitWidth = if (unit.isNotEmpty()) NVGRenderer.textWidth(unit, fontSize, font) else 0f
        val padding = if (unit.isNotEmpty()) 6f else 0f

        val rightBoundsX = x + width - 8f
        val unitX = rightBoundsX - unitWidth
        textBoundsRightX = if (unit.isNotEmpty()) unitX - padding else rightBoundsX
        textBoundsLeftX = textBoundsRightX - textWidth

        NVGRenderer.text(name, x + 6f, textBoundsY, fontSize, Colors.WHITE.rgba, font)

        if (isEditing) {
            val darkBgColor = Color(30, 30, 30, 255).rgb
            val borderColor = ClickGUIModule.clickGUIColor.rgba

            val paddingLeft = 4f     // 左邊界距離文字多遠
            val paddingRight = 4f    // 👉 右邊界距離文字多遠 (調整這個數字！)
            val paddingTop = 3f      // 上邊界
            val paddingBottom = 1f

            val minBoxWidth = 20f
            val actualBoxWidth = maxOf(textWidth, minBoxWidth) + paddingLeft + paddingRight
            val boxRightX = textBoundsRightX + paddingRight
            val boxLeftX = boxRightX - actualBoxWidth

            val boxY = textBoundsY - paddingTop
            val boxHeight = fontSize + paddingTop + paddingBottom

            NVGRenderer.rect(boxLeftX, boxY, actualBoxWidth, boxHeight, darkBgColor, 3f)
            NVGRenderer.hollowRect(boxLeftX, boxY, actualBoxWidth, boxHeight, 1.5f, borderColor, 3f)

            NVGRenderer.text(currentNumberText, textBoundsLeftX, textBoundsY, fontSize, Colors.WHITE.rgba, font)

            // ===== 核心：動態計算閃爍游標的精準位置 =====
            if ((System.currentTimeMillis() % 1000) > 500) {
                // 算出「游標左邊的文字」有多寬，就能知道游標應該畫在哪個 X 座標上
                val textBeforeCursor = currentNumberText.substring(0, cursorIndex.coerceIn(0, currentNumberText.length))
                val cursorOffset = NVGRenderer.textWidth(textBeforeCursor, fontSize, font)

                // 將游標精準畫在那個字元間隙
                val cursorX = textBoundsLeftX + cursorOffset
                NVGRenderer.rect(cursorX, textBoundsY - 1f, 1.2f, fontSize + 2f, Colors.WHITE.rgba, 0f)
            }
        } else {
            NVGRenderer.text(currentNumberText, textBoundsLeftX, textBoundsY, fontSize, Colors.WHITE.rgba, font)
        }

        if (unit.isNotEmpty()) {
            NVGRenderer.text(unit, unitX, textBoundsY, fontSize, Colors.WHITE.rgba, font)
        }

        NVGRenderer.rect(x + 6f, y + 24f, width - 12f, 8f, gray38.rgba, 3f)

        if (x + sliderPercentage * (width - 12f) > x + 6)
            NVGRenderer.rect(x + 6f, y + 24f, sliderAnim.get(prevLocation, sliderPercentage, false) * (width - 12f), 8f, ClickGUIModule.clickGUIColor.rgba, 3f)

        NVGRenderer.circle(x + 6f + sliderAnim.get(prevLocation, sliderPercentage, false) * (width - 12f), y + 28f, handler.anim.get(7f, 9f, !isHovered), Colors.WHITE.rgba)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        val isHoveringText = mouseX >= textBoundsLeftX - 5f && mouseX <= (lastX + width) &&
                mouseY >= textBoundsY - 5f && mouseY <= textBoundsY + textBoundsHeight + 5f

        if (isEditing && (!isHoveringText || click.button() != 0)) {
            saveInput()
            isEditing = false
            if (activeSetting == this) activeSetting = null
        }

        if (click.button() == 0) {
            if (isHoveringText) {
                if (activeSetting != null && activeSetting != this) {
                    activeSetting!!.saveInput()
                    activeSetting!!.isEditing = false
                }

                activeSetting = this
                if (!isEditing) {
                    isEditing = true
                    inputText = getNumberDisplay()
                    cursorIndex = inputText.length // 預設把游標放在最後面
                } else {
                    // ===== 核心：滑鼠點擊時，精準尋找離滑鼠最近的游標位置 =====
                    var bestIndex = 0
                    var minDiff = Float.MAX_VALUE
                    for (i in 0..inputText.length) {
                        val subText = inputText.substring(0, i)
                        val subWidth = NVGRenderer.textWidth(subText, 16f, NVGRenderer.defaultFont)
                        val cx = textBoundsLeftX + subWidth
                        val diff = abs(mouseX - cx)
                        if (diff < minDiff) {
                            minDiff = diff
                            bestIndex = i
                        }
                    }
                    cursorIndex = bestIndex
                }
                return true
            }

            if (isHovered && !isHoveringText) {
                listening = true
                isDragging = true
                prevLocation = sliderPercentage
                sliderAnim.start()
                return true
            }
        }
        return false
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        listening = false
        if (isDragging) {
            isDragging = false
            prevLocation = sliderAnim.get(prevLocation, sliderPercentage, false)
            sliderAnim.start()
        }
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (isEditing) {
            // ===== 核心：自己處理方向鍵與刪除鍵 =====
            when (input.key) {
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

        if (!isHovered) return false

        val amount = when (input.key) {
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_EQUAL -> incrementDouble
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_MINUS -> -incrementDouble
            else -> return false
        }

        if (valueDouble !in minDouble..maxDouble) return false
        valueDouble = (valueDouble + amount).coerceIn(minDouble, maxDouble)
        sliderPercentage = ((valueDouble - minDouble) / (maxDouble - minDouble)).toFloat()
        return true
    }

    override fun keyTyped(input: CharacterEvent): Boolean {
        if (isEditing) {
            // 注意：如果你的 CharacterEvent 屬性不叫 character (例如叫 char)，請自行修改下面這行
            val c = input.codepoint.toChar()

            // ===== 核心：字元精準插入游標位置 =====
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
            valueDouble = parsed.coerceIn(minDouble, maxDouble)
        }
        inputText = getNumberDisplay()
    }

    private fun getNumberDisplay(): String =
        if (valueDouble - floor(valueDouble) == 0.0)
            "${(valueInt * 100.0).roundToInt() / 100}"
        else
            "${(valueDouble * 100.0).roundToInt() / 100.0}"

    override val isHovered: Boolean
        get() = isAreaHovered(lastX, lastY + getHeight() / 2, width, getHeight() / 2, true)

    override fun getHeight(): Float = Panel.HEIGHT + 8f

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement, gson: Gson) {
        element.asNumber?.let { value = it as E }
    }

    private fun roundToIncrement(x: Number): Double =
        round((x.toDouble() / incrementDouble)) * incrementDouble
}