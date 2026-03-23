package com.iq200.heigui.utils

import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.events.core.onReceive
import net.minecraft.network.protocol.game.ClientboundSetTimePacket

object DeathTickUtil {
    var ticks: Long = 0L
        private set

    init {
        onReceive<ClientboundSetTimePacket> {
            val totalWorldTime = gameTime

            if (totalWorldTime == 0L) return@onReceive

            ticks = 40L - (totalWorldTime % 40L)

        }

        on<TickEvent.Server> {
            if (ticks == 1L) ticks = 40

            if (ticks > 1) ticks--

        }
    }
}