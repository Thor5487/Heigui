package com.iq200.heigui.clickgui.settings.impl

import com.iq200.heigui.clickgui.ClickGUI
import com.iq200.heigui.clickgui.settings.RenderableSetting
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.ui.HoverHandler
import com.iq200.heigui.utils.ui.animations.LinearAnimation
import com.iq200.heigui.utils.ui.isAreaHovered
import com.iq200.heigui.utils.ui.rendering.GuiRenderer
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent

/**
 * A setting intended to show or hide other settings in the GUI.
 *
 * @author Bonsai
 */
class DropdownSetting(
    name: String,
    override val default: Boolean = false,
    desc: String = ""
) : RenderableSetting<Boolean>(name, desc) {

    override var value: Boolean = default
    private var enabled: Boolean by this::value

    private val toggleAnimation = LinearAnimation<Float>(200)
    private val hoverHandler = HoverHandler(150)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        GuiRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba)

        hoverHandler.handle(lastX + width - 30f, lastY + getHeight() / 2f - 16f, 24f, 24f, true)

        val imageSize = 24f + (6f * hoverHandler.percent() / 100f)
        val offset = (imageSize - 24f) / 2f

        GuiRenderer.push()
        GuiRenderer.translate(x + width - 18f, y + height / 2f - 4f)
        GuiRenderer.rotate(toggleAnimation.get(0f, Math.PI.toFloat() / 2f, enabled))
        GuiRenderer.translate(-(12f + offset), -(12f + offset))
        GuiRenderer.image(ClickGUI.chevronImage, 0f, 0f, imageSize, imageSize)
        GuiRenderer.pop()

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT || !isHovered) return false
        enabled = !enabled
        toggleAnimation.start()
        return true
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 30f, lastY + getHeight() / 2f - 16f, 24f, 24f, true)
}
