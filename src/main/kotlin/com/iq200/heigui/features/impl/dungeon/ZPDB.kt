package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object ZPDB : Module (
    name = "ZPDB",
    description = "Zero Ping DungeonBreaker",
    category = Category.DUNGEON
) {
    private val blacklistedBlocks = setOf(
        Blocks.BARRIER,
        Blocks.BEDROCK,
        Blocks.COMMAND_BLOCK,
        Blocks.REPEATING_COMMAND_BLOCK,
        Blocks.CHAIN_COMMAND_BLOCK,
        Blocks.TNT,
        Blocks.CHEST,
        Blocks.END_PORTAL_FRAME,
        Blocks.PISTON,
        Blocks.STICKY_PISTON,
        Blocks.PISTON_HEAD,
        Blocks.TRAPPED_CHEST,
        Blocks.LEVER
    )

    init {
        on<PacketEvent.Send> {
            if (!enabled) return@on

            if (!DungeonUtils.inDungeons) return@on

            if (packet is ServerboundPlayerActionPacket) {
                if (packet.action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return@on

                val itemName = mc.player!!.mainHandItem.hoverName.string.lowercase()

                if (!itemName.contains("dungeonbreaker")) return@on

                val pos = packet.pos

                val level = mc.level?:return@on

                val targetBlock = level.getBlockState(pos).block

                if (targetBlock in blacklistedBlocks || targetBlock is AbstractSkullBlock) return@on

                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
            }
        }
    }
}