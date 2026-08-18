package com.iq200.heigui.features.impl.mining

import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.alert
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils

object MSB : Module(
    name = "MSB Notifier",
    description = "Notify when MSB is ready",
    category = Category.MINING
) {
    init {
        on<ChatPacketEvent> {
            if (!DungeonUtils.inDungeons && value.contains("Mining Speed Boost is now available!")) {
                alert("§bMSB READY!!!")
            }
        }
    }
}