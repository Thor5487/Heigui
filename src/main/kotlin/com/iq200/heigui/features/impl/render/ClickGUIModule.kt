package com.iq200.heigui.features.impl.render

import com.google.gson.annotations.SerializedName
import com.iq200.heigui.Heigui
import com.iq200.heigui.clickgui.ClickGUI
import com.iq200.heigui.clickgui.HudManager
import com.iq200.heigui.clickgui.settings.AlwaysActive
import com.iq200.heigui.clickgui.settings.impl.*
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.alert
import com.iq200.heigui.utils.getChatBreak
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.network.WebUtils.fetchJson
import com.iq200.heigui.utils.ui.rendering.NVGRenderer
import kotlinx.coroutines.launch
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import org.lwjgl.glfw.GLFW
import java.net.URI
import kotlin.math.max
import kotlin.math.round

@AlwaysActive
object ClickGUIModule : Module(
    name = "Click GUI",
    description = "Allows you to customize the UI.",
    key = GLFW.GLFW_KEY_RIGHT_SHIFT,
    category = Category.RENDER
) {
    val enableNotification by BooleanSetting("Chat notifications", true, desc = "Sends a message when you toggle a module with a keybind")
    val clickGUIColor by ColorSetting("Color", Color(50, 150, 220), desc = "The color of the Click GUI.")

    val roundedPanelBottom by BooleanSetting("Rounded Panel Bottoms", true, desc = "Whether to extend panels to make them rounded at the bottom.")

    val hypixelApiUrl by StringSetting("API URL", "https://api.odtheking.com/hypixel/", 128, "The Hypixel API server to connect to.").hide()

    private val action by ActionSetting("Open HUD Editor", desc = "Opens the HUD editor when clicked.") { mc.setScreen(HudManager) }
    val devMessage by BooleanSetting("Developer Message", false, desc = "Sends development related messages to the chat.")

    override fun onKeybind() {
        toggle()
    }

    override fun onEnable() {
        mc.setScreen(ClickGUI)
        super.onEnable()
        toggle()
    }

    val panelSetting by MapSetting("Panel Settings", mutableMapOf<String, PanelData>())
    data class PanelData(var x: Float = 10f, var y: Float = 10f, var extended: Boolean = true)

    fun resetPositions() {
        Category.categories.entries.forEachIndexed { index, (categoryName, _) ->
            val setting = panelSetting.getOrPut(categoryName) { PanelData() }
            setting.x = 10f + 260f * index
            setting.y = 10f
            setting.extended = true
        }
    }


    fun getStandardGuiScale(): Float {
        val verticalScale = (mc.window.screenHeight.toFloat() / 1080f) / NVGRenderer.devicePixelRatio()
        val horizontalScale = (mc.window.screenWidth.toFloat() / 1920f) / NVGRenderer.devicePixelRatio()
        return round(max(verticalScale, horizontalScale).coerceIn(1f, 3f) * 10f) / 10f
    }

}