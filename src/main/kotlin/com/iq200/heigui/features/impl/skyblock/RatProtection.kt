package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.events.ScreenEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.modMessage
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress

object RatProtection : Module(
    name = "RatProtection",
    description = "Tryna Fight with Ratters by Connecting Back to Hypixel",
    category = Category.SKYBLOCK
) {
    init {
        on<ScreenEvent.Open> {
            val disconnectScreen = screen as? DisconnectedScreen ?: return@on

            val rawReason = disconnectScreen.narrationMessage.string
            val cleanReason = rawReason.replace(Regex("§[0-9a-fk-or]"), "").trim()

            if (cleanReason.contains("logged in from another location", ignoreCase = true)) {

                mc.execute {
                    val serverData = ServerData("Hypixel", "mc.hypixel.net", ServerData.Type.OTHER)
                    val serverAddress = ServerAddress.parseString(serverData.ip)

                    ConnectScreen.startConnecting(
                        TitleScreen(),
                        mc,
                        serverAddress,
                        serverData,
                        false,
                        null
                    )
                }
            }
        }
    }
}