package com.iq200.heigui.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.iq200.heigui.clickgui.settings.RenderableSetting
import com.iq200.heigui.clickgui.settings.Saving
import com.iq200.heigui.clickgui.widget.Toggle
import com.iq200.heigui.clickgui.widget.drawToggle
import com.iq200.heigui.utils.ui.animations.Fade
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class BooleanSetting(
    name: String,
    override val default: Boolean = false,
    desc: String,
) : RenderableSetting<Boolean>(name, desc), Saving {

    override var value: Boolean = default
    var enabled: Boolean by this::value

    private val toggleAnimation = Fade(TOGGLE_DURATION)

    override fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        drawLabel(graphics)
        graphics.drawToggle(x + width - Toggle.WIDTH - RIGHT_PAD, y + height / 2, toggleAnimation.progress(enabled), hover)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        enabled = !enabled
    }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(enabled)

    override fun read(element: JsonElement, gson: Gson) {
        enabled = element.asBoolean
    }

    private companion object {
        const val TOGGLE_DURATION = 200L
        const val RIGHT_PAD = 6
    }
}