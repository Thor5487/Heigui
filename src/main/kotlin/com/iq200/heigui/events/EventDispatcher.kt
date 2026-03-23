package com.iq200.heigui.events

import com.iq200.heigui.events.core.onReceive
import com.iq200.heigui.utils.ChatManager
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.render.RenderBatchManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

object EventDispatcher {

    init {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> WorldEvent.Load.postAndCatch() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> WorldEvent.Unload.postAndCatch() }

        ClientTickEvents.START_WORLD_TICK.register { world -> TickEvent.Start(world).postAndCatch() }
        ClientTickEvents.END_WORLD_TICK.register { world -> TickEvent.End(world).postAndCatch() }

        WorldRenderEvents.END_EXTRACTION.register { handler -> RenderEvent.Extract(handler, RenderBatchManager.renderConsumer).postAndCatch() }
        WorldRenderEvents.END_MAIN.register { context -> RenderEvent.Last(context).postAndCatch() }

        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (overlay) return@register true
            !ChatManager.shouldCancelMessage(text)
        }

        // 🌟 1.21.1 官方映射: 封包變數改為方法，加上 ()
        onReceive<ClientboundSystemChatPacket> {
            if (!overlay()) {
                ChatPacketEvent(content().string.noControlCodes, content()).postAndCatch()
            }
        }
    }
}