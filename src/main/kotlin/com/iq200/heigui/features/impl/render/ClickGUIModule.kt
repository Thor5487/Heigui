package com.iq200.heigui.features.impl.render

import com.iq200.heigui.clickgui.ClickGUI
import com.iq200.heigui.clickgui.HudManager
import com.iq200.heigui.clickgui.settings.AlwaysActive
import com.iq200.heigui.clickgui.settings.impl.*
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color
import org.lwjgl.glfw.GLFW

@AlwaysActive
object ClickGUIModule : Module(
    name = "Click GUI",
    description = "Allows you to customize the UI.",
    key = GLFW.GLFW_KEY_RIGHT_SHIFT,
    category = Category.RENDER
) {
    val clickGuiScale by NumberSetting("Click GUI Size", 2, 1..4, 1, desc = "GUI scale the Click GUI is drawn at, whatever the video setting says.")
    val enableNotification by BooleanSetting("Chat notifications", true, desc = "Sends a message when you toggle a module with a keybind")
    val clickGUIColor by ColorSetting("Color", Color(50, 150, 220), desc = "The color of the Click GUI.")

    val roundedPanelBottom by BooleanSetting("Rounded Panel Bottoms", true, desc = "Whether to extend panels to make them rounded at the bottom.")

    val hypixelApiUrl by StringSetting("API URL", "https://api.odtheking.com/hypixel/", 128, "The Hypixel API server to connect to.", "Click").hide()

    private val action by ActionSetting("Open HUD Editor", desc = "Opens the HUD editor when clicked.") { mc.setScreenAndShow(HudManager) }
    val devMessage by BooleanSetting("Developer Message", false, desc = "Sends development related messages to the chat.")

    override fun onKeybind() {
        toggle()
    }

    override fun onEnable() {
        mc.setScreenAndShow(ClickGUI)
        super.onEnable()
        toggle()
    }

    val panelSetting by MapSetting("Panel Settings", mutableMapOf<String, PanelData>())
    data class PanelData(var x: Int, var y: Int, var extended: Boolean = true)

    fun resetPositions() {
        Category.categories.forEach { (categoryName, category) ->
            panelSetting.getOrPut(categoryName) { PanelData(0, 0) }.apply {
                x = category.x
                y = category.y
                extended = true
            }
        }
    }


}