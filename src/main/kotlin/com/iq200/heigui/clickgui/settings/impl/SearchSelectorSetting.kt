package com.iq200.heigui.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.iq200.heigui.clickgui.ClickGUI.gray26
import com.iq200.heigui.clickgui.ClickGUI.gray38
import com.iq200.heigui.clickgui.Panel
import com.iq200.heigui.clickgui.settings.RenderableSetting
import com.iq200.heigui.clickgui.settings.Saving
import com.iq200.heigui.features.impl.render.ClickGUIModule
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.ui.TextInputHandler
import com.iq200.heigui.utils.ui.animations.EaseInOutAnimation
import com.iq200.heigui.utils.ui.isAreaHovered
import com.iq200.heigui.utils.ui.rendering.GuiRenderer
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.roundToInt

class SearchSelectorSetting(
    name: String,
    default: String,
    private val options: List<String>,
    desc: String
) : RenderableSetting<Int>(name, desc), Saving {

    init {
        require(options.isNotEmpty()) { "SearchSelectorSetting requires at least one option." }
    }

    override val default: Int = optionIndex(default)

    override var value: Int
        get() = index
        set(value) {
            index = value
        }

    private var index = optionIndex(default)
        set(value) {
            field = value.mod(options.size)
        }

    private var selected: String
        get() = options[index]
        set(value) {
            index = optionIndex(value)
        }

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String = selected

    private var firstVisibleOption = 0
    private var search = ""
        set(value) {
            field = value.take(MAX_SEARCH_LENGTH)
            firstVisibleOption = 0
        }

    private val filteredOptionIndices: List<Int>
        get() = options.indices.filter { options[it].contains(search, ignoreCase = true) }

    private val textInputHandler = TextInputHandler(
        textProvider = { search },
        textSetter = { search = it }
    )
    private val settingAnim = EaseInOutAnimation(200)
    private val baseHeight = Panel.HEIGHT + 28f
    private var extended = false
    private var hoveredOptionIndex: Int? = null
    private var optionHoverStartedAt = 0L
    private var draggingScrollbar = false
    private var scrollbarDragOffsetY = 0f

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)

        val fieldX = x + HORIZONTAL_PADDING
        val fieldY = y + baseHeight - 35f
        val fieldWidth = width - HORIZONTAL_PADDING * 2f

        GuiRenderer.text(name, fieldX, y + 5f, FONT_SIZE, Colors.WHITE.rgba)
        GuiRenderer.rect(fieldX, fieldY, fieldWidth, FIELD_HEIGHT, gray38.rgba, 4f)
        GuiRenderer.hollowRect(fieldX, fieldY, fieldWidth, FIELD_HEIGHT, 2f, ClickGUIModule.clickGUIColor.rgba, 4f)
        drawClippedCenteredText(selected, fieldX, fieldY + 2f, fieldWidth, INPUT_HEIGHT, Colors.WHITE.rgba)

        if (!extended && !settingAnim.isAnimating()) return baseHeight

        val displayHeight = getHeight()
        if (settingAnim.isAnimating()) GuiRenderer.pushScissor(x, y, width, displayHeight)

        val filtered = filteredOptionIndices
        val optionRowCount = filtered.size.coerceIn(1, MAX_VISIBLE_OPTIONS)
        val dropdownY = y + baseHeight + DROPDOWN_GAP
        val optionsY = dropdownY + SEARCH_ROW_HEIGHT
        val dropdownHeight = SEARCH_ROW_HEIGHT + optionRowCount * OPTION_HEIGHT + OPTION_LIST_BOTTOM_PADDING

        if (draggingScrollbar) {
            if (filtered.size > MAX_VISIBLE_OPTIONS) {
                updateScrollbarFromMouse(mouseY, optionsY, optionRowCount, filtered.size)
            } else {
                draggingScrollbar = false
            }
        }
        val visibleOptions = visibleOptions(filtered)

        GuiRenderer.rect(fieldX, dropdownY, fieldWidth, dropdownHeight, gray38.rgba, 5f)

        val searchFieldX = fieldX + 6f
        val searchFieldY = dropdownY + 5f
        val searchFieldWidth = fieldWidth - 12f
        val searchFieldHeight = SEARCH_ROW_HEIGHT - 10f
        GuiRenderer.rect(searchFieldX, searchFieldY, searchFieldWidth, searchFieldHeight, gray26.rgba, 5f)
        GuiRenderer.hollowRect(
            searchFieldX,
            searchFieldY,
            searchFieldWidth,
            searchFieldHeight,
            1f,
            Colors.MINECRAFT_DARK_GRAY.rgba,
            5f
        )

        val searchIconX = searchFieldX + 11f
        val searchIconY = searchFieldY + searchFieldHeight / 2f - 1f
        GuiRenderer.circle(searchIconX, searchIconY, 5f, Colors.MINECRAFT_GRAY.rgba)
        GuiRenderer.circle(searchIconX, searchIconY, 3f, gray26.rgba)
        GuiRenderer.line(
            searchIconX + 3.5f,
            searchIconY + 3.5f,
            searchIconX + 7f,
            searchIconY + 7f,
            2f,
            Colors.MINECRAFT_GRAY.rgba
        )

        if (search.isEmpty() && !textInputHandler.isListening) {
            GuiRenderer.verticallyCenteredText(
                "Search",
                searchFieldX + 27f,
                searchFieldY,
                searchFieldHeight,
                FONT_SIZE,
                Colors.MINECRAFT_GRAY.rgba
            )
        }
        textInputHandler.x = searchFieldX + 19f
        textInputHandler.y = searchFieldY
        textInputHandler.width = searchFieldWidth - 23f
        textInputHandler.height = searchFieldHeight
        textInputHandler.draw(mouseX, mouseY)

        GuiRenderer.line(
            fieldX + 12f,
            optionsY,
            fieldX + fieldWidth - 6f,
            optionsY,
            1.5f,
            Colors.MINECRAFT_DARK_GRAY.rgba
        )

        GuiRenderer.pushScissor(
            fieldX,
            optionsY,
            fieldWidth,
            optionRowCount * OPTION_HEIGHT + OPTION_LIST_BOTTOM_PADDING
        )
        if (visibleOptions.isEmpty()) {
            drawClippedCenteredText(
                "No results",
                fieldX,
                optionsY,
                fieldWidth,
                OPTION_HEIGHT,
                Colors.MINECRAFT_GRAY.rgba
            )
        } else {
            val hoveredRow = visibleOptions.indices.firstOrNull { isOptionHovered(it) }
            val currentHoveredOption = hoveredRow?.let(visibleOptions::get)
            updateHoveredOption(currentHoveredOption)

            visibleOptions.forEachIndexed { row, optionIndex ->
                val optionY = optionsY + OPTION_HEIGHT * row
                drawClippedCenteredText(
                    options[optionIndex],
                    fieldX,
                    optionY,
                    fieldWidth,
                    OPTION_HEIGHT,
                    Colors.WHITE.rgba,
                    if (optionIndex == currentHoveredOption) optionHoverStartedAt else null
                )
                if (row != visibleOptions.lastIndex) {
                    GuiRenderer.line(
                        fieldX + 12f,
                        optionY + OPTION_HEIGHT,
                        fieldX + fieldWidth - 6f,
                        optionY + OPTION_HEIGHT,
                        1.5f,
                        Colors.MINECRAFT_DARK_GRAY.rgba
                    )
                }
                if (isOptionHovered(row)) {
                    GuiRenderer.hollowRect(
                        fieldX + HOVER_BORDER_INSET,
                        optionY,
                        fieldWidth - HOVER_BORDER_INSET * 2f,
                        OPTION_HEIGHT,
                        HOVER_BORDER_THICKNESS,
                        ClickGUIModule.clickGUIColor.rgba,
                        4f
                    )
                }
            }
        }
        if (visibleOptions.isEmpty()) updateHoveredOption(null)
        GuiRenderer.popScissor()

        drawScrollbar(fieldX, optionsY, fieldWidth, optionRowCount, filtered.size)

        if (settingAnim.isAnimating()) GuiRenderer.popScissor()
        return displayHeight
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (extended && beginScrollbarDrag(mouseX, mouseY)) return true

            if (isFieldHovered()) {
                if (extended) close() else open()
                return true
            }

            if (extended && isSearchHovered()) {
                return textInputHandler.mouseClicked(mouseX, mouseY, click)
            }

            if (extended) {
                val visibleOptions = visibleOptions(filteredOptionIndices)
                visibleOptions.forEachIndexed { row, optionIndex ->
                    if (isOptionHovered(row)) {
                        index = optionIndex
                        close()
                        return true
                    }
                }
                close()
            }
        } else if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT && isBaseHovered()) {
            index++
            if (extended) close()
            return true
        }
        return false
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) draggingScrollbar = false
        textInputHandler.mouseReleased()
    }

    override fun mouseScrolled(amount: Int): Boolean {
        if (!extended || !isDropdownHovered() || amount == 0) return false

        val maxFirstVisible = (filteredOptionIndices.size - MAX_VISIBLE_OPTIONS).coerceAtLeast(0)
        val direction = if (amount > 0) -1 else 1
        firstVisibleOption = (firstVisibleOption + direction).coerceIn(0, maxFirstVisible)
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (!extended) return false

        return when (input.key) {
            InputConstants.KEY_RETURN -> {
                filteredOptionIndices.firstOrNull()?.let { index = it }
                close()
                true
            }

            InputConstants.KEY_ESCAPE -> {
                close()
                true
            }

            else -> textInputHandler.keyPressed(input)
        }
    }

    override fun keyTyped(input: CharacterEvent): Boolean =
        extended && textInputHandler.keyTyped(input)

    override fun getHeight(): Float {
        val optionRowCount = filteredOptionIndices.size.coerceIn(1, MAX_VISIBLE_OPTIONS)
        val expandedHeight =
            baseHeight + DROPDOWN_GAP + SEARCH_ROW_HEIGHT + optionRowCount * OPTION_HEIGHT + BOTTOM_PADDING
        return settingAnim.get(baseHeight, expandedHeight, !extended)
    }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(selected)

    override fun read(element: JsonElement, gson: Gson) {
        element.asString?.let { selected = it }
    }

    private fun open() {
        search = ""
        extended = true
        settingAnim.start()
    }

    private fun close() {
        if (extended) settingAnim.start()
        extended = false
        draggingScrollbar = false
        textInputHandler.stopListening()
    }

    private fun visibleOptions(filtered: List<Int>): List<Int> {
        val maxFirstVisible = (filtered.size - MAX_VISIBLE_OPTIONS).coerceAtLeast(0)
        firstVisibleOption = firstVisibleOption.coerceIn(0, maxFirstVisible)
        return filtered.drop(firstVisibleOption).take(MAX_VISIBLE_OPTIONS)
    }

    private fun updateHoveredOption(optionIndex: Int?) {
        if (hoveredOptionIndex == optionIndex) return

        hoveredOptionIndex = optionIndex
        optionHoverStartedAt = System.currentTimeMillis()
    }

    private fun beginScrollbarDrag(mouseX: Float, mouseY: Float): Boolean {
        val totalOptionCount = filteredOptionIndices.size
        if (totalOptionCount <= MAX_VISIBLE_OPTIONS) return false

        val visibleRowCount = MAX_VISIBLE_OPTIONS
        val fieldX = lastX + HORIZONTAL_PADDING
        val fieldWidth = width - HORIZONTAL_PADDING * 2f
        val optionsY = lastY + baseHeight + DROPDOWN_GAP + SEARCH_ROW_HEIGHT
        val trackX = fieldX + fieldWidth + SCROLLBAR_OUTSIDE_GAP
        val trackY = optionsY + SCROLLBAR_VERTICAL_PADDING
        val trackHeight = visibleRowCount * OPTION_HEIGHT - SCROLLBAR_VERTICAL_PADDING * 2f
        val thumbHeight = scrollbarThumbHeight(trackHeight, visibleRowCount, totalOptionCount)
        val thumbY = scrollbarThumbY(trackY, trackHeight, thumbHeight, visibleRowCount, totalOptionCount)

        val insideTrack = mouseX >= trackX - SCROLLBAR_HIT_PADDING &&
            mouseX <= trackX + SCROLLBAR_WIDTH + SCROLLBAR_HIT_PADDING &&
            mouseY >= trackY && mouseY <= trackY + trackHeight
        if (!insideTrack) return false

        scrollbarDragOffsetY = if (mouseY in thumbY..(thumbY + thumbHeight)) {
            mouseY - thumbY
        } else {
            thumbHeight / 2f
        }
        draggingScrollbar = true
        updateScrollbarFromMouse(mouseY, optionsY, visibleRowCount, totalOptionCount)
        return true
    }

    private fun updateScrollbarFromMouse(
        mouseY: Float,
        optionsY: Float,
        visibleRowCount: Int,
        totalOptionCount: Int
    ) {
        val trackY = optionsY + SCROLLBAR_VERTICAL_PADDING
        val trackHeight = visibleRowCount * OPTION_HEIGHT - SCROLLBAR_VERTICAL_PADDING * 2f
        val thumbHeight = scrollbarThumbHeight(trackHeight, visibleRowCount, totalOptionCount)
        val thumbTravel = trackHeight - thumbHeight
        if (thumbTravel <= 0f) return

        val progress = ((mouseY - scrollbarDragOffsetY - trackY) / thumbTravel).coerceIn(0f, 1f)
        val maxFirstVisible = totalOptionCount - visibleRowCount
        firstVisibleOption = (progress * maxFirstVisible).roundToInt()
    }

    private fun scrollbarThumbHeight(
        trackHeight: Float,
        visibleRowCount: Int,
        totalOptionCount: Int
    ): Float = (trackHeight * visibleRowCount / totalOptionCount)
        .coerceAtLeast(MIN_SCROLLBAR_THUMB_HEIGHT)

    private fun scrollbarThumbY(
        trackY: Float,
        trackHeight: Float,
        thumbHeight: Float,
        visibleRowCount: Int,
        totalOptionCount: Int
    ): Float {
        val maxFirstVisible = totalOptionCount - visibleRowCount
        val progress = firstVisibleOption.toFloat() / maxFirstVisible
        return trackY + (trackHeight - thumbHeight) * progress
    }

    private fun drawScrollbar(
        fieldX: Float,
        optionsY: Float,
        fieldWidth: Float,
        visibleRowCount: Int,
        totalOptionCount: Int
    ) {
        if (totalOptionCount <= MAX_VISIBLE_OPTIONS) return

        val trackX = fieldX + fieldWidth + SCROLLBAR_OUTSIDE_GAP
        val trackY = optionsY + SCROLLBAR_VERTICAL_PADDING
        val trackHeight = visibleRowCount * OPTION_HEIGHT - SCROLLBAR_VERTICAL_PADDING * 2f
        val thumbHeight = scrollbarThumbHeight(trackHeight, visibleRowCount, totalOptionCount)
        val thumbY = scrollbarThumbY(
            trackY,
            trackHeight,
            thumbHeight,
            visibleRowCount,
            totalOptionCount
        )

        GuiRenderer.rect(
            trackX,
            trackY,
            SCROLLBAR_WIDTH,
            trackHeight,
            Colors.MINECRAFT_DARK_GRAY.rgba,
            SCROLLBAR_WIDTH / 2f
        )
        GuiRenderer.rect(
            trackX,
            thumbY,
            SCROLLBAR_WIDTH,
            thumbHeight,
            ClickGUIModule.clickGUIColor.rgba,
            SCROLLBAR_WIDTH / 2f
        )
    }

    private fun drawClippedCenteredText(
        text: String,
        areaX: Float,
        areaY: Float,
        areaWidth: Float,
        areaHeight: Float,
        color: Int,
        marqueeStartedAt: Long? = null
    ) {
        val contentX = areaX + TEXT_PADDING
        val contentWidth = areaWidth - TEXT_PADDING * 2f
        val textWidth = GuiRenderer.textWidth(text, FONT_SIZE)
        val textX = if (textWidth > contentWidth) {
            if (marqueeStartedAt == null) {
                contentX + contentWidth - textWidth
            } else {
                contentX - marqueeOffset(textWidth - contentWidth, marqueeStartedAt)
            }
        } else contentX

        GuiRenderer.pushScissor(contentX, areaY, contentWidth, areaHeight)
        GuiRenderer.verticallyCenteredText(text, textX, areaY, areaHeight, FONT_SIZE, color)
        GuiRenderer.popScissor()
    }

    private fun marqueeOffset(overflow: Float, startedAt: Long): Float {
        val travelDuration = (overflow / MARQUEE_SPEED * 1000f).toLong().coerceAtLeast(1L)
        val cycleDuration = MARQUEE_START_HOLD + travelDuration + MARQUEE_END_HOLD
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) % cycleDuration

        return when {
            elapsed < MARQUEE_START_HOLD -> 0f
            elapsed < MARQUEE_START_HOLD + travelDuration -> {
                val progress = (elapsed - MARQUEE_START_HOLD).toFloat() / travelDuration
                overflow * progress
            }
            else -> overflow
        }
    }

    private fun isFieldHovered(): Boolean =
        isAreaHovered(
            lastX + HORIZONTAL_PADDING,
            lastY + baseHeight - 35f,
            width - HORIZONTAL_PADDING * 2f,
            FIELD_HEIGHT,
            true
        )

    private fun isBaseHovered(): Boolean = isAreaHovered(lastX, lastY, width, baseHeight, true)

    private fun isSearchHovered(): Boolean =
        isAreaHovered(
            lastX + HORIZONTAL_PADDING,
            lastY + baseHeight + DROPDOWN_GAP,
            width - HORIZONTAL_PADDING * 2f,
            SEARCH_ROW_HEIGHT,
            true
        )

    private fun isDropdownHovered(): Boolean {
        val optionRowCount = filteredOptionIndices.size.coerceIn(1, MAX_VISIBLE_OPTIONS)
        val scrollbarWidth = if (filteredOptionIndices.size > MAX_VISIBLE_OPTIONS) {
            SCROLLBAR_OUTSIDE_GAP + SCROLLBAR_WIDTH
        } else 0f
        return isAreaHovered(
            lastX + HORIZONTAL_PADDING,
            lastY + baseHeight + DROPDOWN_GAP,
            width - HORIZONTAL_PADDING * 2f + scrollbarWidth,
            SEARCH_ROW_HEIGHT + optionRowCount * OPTION_HEIGHT,
            true
        )
    }

    private fun isOptionHovered(row: Int): Boolean =
        isAreaHovered(
            lastX + HORIZONTAL_PADDING,
            lastY + baseHeight + DROPDOWN_GAP + SEARCH_ROW_HEIGHT + OPTION_HEIGHT * row,
            width - HORIZONTAL_PADDING * 2f,
            OPTION_HEIGHT,
            true
        )

    private fun optionIndex(option: String): Int =
        options.indexOfFirst { it.equals(option, ignoreCase = true) }.coerceAtLeast(0)

    private companion object {
        const val FONT_SIZE = 16f
        const val HORIZONTAL_PADDING = 6f
        const val TEXT_PADDING = 8f
        const val HOVER_BORDER_INSET = 2f
        const val HOVER_BORDER_THICKNESS = 1.5f
        const val FIELD_HEIGHT = 30f
        const val INPUT_HEIGHT = 26f
        const val SEARCH_ROW_HEIGHT = 32f
        const val OPTION_HEIGHT = 32f
        const val OPTION_LIST_BOTTOM_PADDING = 3f
        const val DROPDOWN_GAP = 5f
        const val BOTTOM_PADDING = 7f
        const val MAX_VISIBLE_OPTIONS = 6
        const val MAX_SEARCH_LENGTH = 64
        const val SCROLLBAR_WIDTH = 4f
        const val SCROLLBAR_OUTSIDE_GAP = 1f
        const val SCROLLBAR_HIT_PADDING = 2f
        const val SCROLLBAR_VERTICAL_PADDING = 5f
        const val MIN_SCROLLBAR_THUMB_HEIGHT = 16f
        const val MARQUEE_SPEED = 32f
        const val MARQUEE_START_HOLD = 600L
        const val MARQUEE_END_HOLD = 900L
    }
}
