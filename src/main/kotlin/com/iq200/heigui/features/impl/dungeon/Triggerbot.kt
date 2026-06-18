package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.UUID

class BlockRegion(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) {
    val minX = minOf(x1, x2)
    val minY = minOf(y1, y2)
    val minZ = minOf(z1, z2)

    val maxX = maxOf(x1, x2)
    val maxY = maxOf(y1, y2)
    val maxZ = maxOf(z1, z2)

    operator fun contains(pos: BlockPos): Boolean {
        return pos.x in minX..maxX &&
                pos.y in minY..maxY &&
                pos.z in minZ..maxZ
    }
}


object Triggerbot : Module (
    name = "Triggerbot",
    description = "Auto Click on Things While Aiming at it",
    category = Category.DUNGEON
) {
    private val lever by BooleanSetting("Lever", false, "lever triggerbot")
    private val chest by BooleanSetting("Chest", false, "chest triggerbot")
    private val essence by BooleanSetting("Essence", false, "essence triggerbot")
    private val cd by NumberSetting("CD for Click", 200, 50, 1000, 10, "CD between clicking same block")

    private val clickedBlocks = mutableMapOf<BlockPos, Long>()

    private val blacklistedRegions = listOf(
        BlockRegion(56, 132, 142, 63, 140, 142)
    )


    init {
        on<WorldEvent.Load> {
            clearBlocks()
        }

        on<RenderEvent.Extract> {
            if (!enabled) return@on

            if (!DungeonUtils.inDungeons) return@on

            val currentRoom = ScanUtils.currentRoom

            if (currentRoom != null && (currentRoom.data.name == "Three Weirdos" || currentRoom.data.name == "Water Board")) return@on

            val now = System.currentTimeMillis()
            clickedBlocks.entries.removeIf{now - it.value > cd.toLong()}

            val hitResult = mc.hitResult?:return@on

            if (hitResult.type == HitResult.Type.BLOCK) {
                val blockHit = hitResult as BlockHitResult

                handleTriggerBot(blockHit)
            }

        }
    }

    private val SECRET_SKULL_UUID = UUID.fromString("e0f3e929-869e-3dca-9504-54c666ee6f23")

    fun handleTriggerBot(hitResult: BlockHitResult) {
        val pos = hitResult.blockPos

        if (blacklistedRegions.any{pos in it}) return

        val now = System.currentTimeMillis()
        val lastClickedTime = clickedBlocks[pos] ?: 0L
        if (now - lastClickedTime < cd) return

        val level = mc.level ?: return
        val block = level.getBlockState(pos).block

        var shouldClick = false

        if (chest && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST)) shouldClick = true
        else if (lever && block == Blocks.LEVER) shouldClick = true
        else if (essence && block is AbstractSkullBlock) {
            val blockEntity = level.getBlockEntity(pos)

            if (blockEntity is SkullBlockEntity) {
                val profileComponent = blockEntity.ownerProfile
                if (profileComponent != null) {
                    val gameProfile = profileComponent.partialProfile()
                    val id = gameProfile.id

                    if (id != null && SECRET_SKULL_UUID == id) {
                        shouldClick = true
                    }
                }
            }
        }

        if (!shouldClick) return

        doClick(hitResult)

        clickedBlocks[pos] = now
    }

    fun clearBlocks() {
        clickedBlocks.clear()
    }

    fun doClick(hitResult : BlockHitResult) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
        player.swing(InteractionHand.MAIN_HAND)
    }
}