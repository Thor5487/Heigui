package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket

object AutoClose : Module (
    name = "Auto Close",
    description = "Close chests in dungeon immediately",
    category = Category.DUNGEON
) {
    init {
        on<PacketEvent.Receive> {
            if (!enabled) return@on

            if (!DungeonUtils.inDungeons) return@on

            if (packet is ClientboundOpenScreenPacket) {
                val menuTitle = packet.title.string.lowercase()

                if (!menuTitle.contains("chest")) return@on

                val id = packet.containerId

                mc.connection?.send(ServerboundContainerClosePacket(id))

                cancel()
            }
        }
    }
}