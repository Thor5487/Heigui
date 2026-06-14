package com.iq200.heigui.events

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.core.onReceive
import com.iq200.heigui.events.core.onSend
import com.iq200.heigui.utils.ChatManager
import com.iq200.heigui.utils.containsOneOf
import com.iq200.heigui.utils.equalsOneOf
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.render.RenderBatchManager
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils.isSecret
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity

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

        onReceive<ClientboundTakeItemEntityPacket> {
            if (mc.player == null || !DungeonUtils.inClear) return@onReceive
            val itemEntity = mc.level?.getEntity(itemId) as? ItemEntity ?: return@onReceive
            if (itemEntity.item.hoverName.string.containsOneOf(dungeonItemDrops, true) && itemEntity.distanceTo(mc.player ?: return@onReceive) <= 6)
                SecretPickupEvent.Item(itemEntity).postAndCatch()
        }

        onReceive<ClientboundRemoveEntitiesPacket> {
            if (mc.player == null || !DungeonUtils.inClear) return@onReceive
            entityIds.forEach { id ->
                val entity = mc.level?.getEntity(id) as? ItemEntity ?: return@forEach
                if (entity.item.hoverName.string.containsOneOf(dungeonItemDrops, true) && entity.distanceTo(mc.player ?: return@onReceive) <= 6)
                    SecretPickupEvent.Item(entity).postAndCatch()
            }
        }

        onReceive<ClientboundSoundPacket> {
            if (!DungeonUtils.inClear) return@onReceive
            if (sound.value().equalsOneOf(SoundEvents.BAT_HURT, SoundEvents.BAT_DEATH) && volume == 0.1f)
                SecretPickupEvent.Bat(this).postAndCatch()
        }

        onSend<ServerboundUseItemOnPacket> {
            if (!DungeonUtils.inDungeons || hand == InteractionHand.OFF_HAND) return@onSend
            SecretPickupEvent.Interact(
                hitResult.blockPos,
                mc.level?.getBlockState(hitResult.blockPos)?.takeIf { isSecret(it, hitResult.blockPos) } ?: return@onSend
            ).postAndCatch()
        }
    }
    private val dungeonItemDrops = listOf(
        "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion", "Healing Potion VIII Splash Potion", "Healing VIII Splash Potion", "Healing 8 Splash Potion",
        "Decoy", "Inflatable Jerry", "Spirit Leap", "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key", "Treasure Talisman", "Revive Stone", "Architect's First Draft",
        "Secret Dye", "Candycomb"
    )
}