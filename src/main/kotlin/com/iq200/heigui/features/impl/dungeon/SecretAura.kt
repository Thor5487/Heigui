package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.RaytraceUtils
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3


object SecretAura : Module (
    name = "Secret Aura",
    description = "Doing Secrets within Range",
    category = Category.DUNGEON
) {
    private val range by NumberSetting("Secret Aura Range", default = 3.0, min = 2.1, max = 7.0, increment = 0.1, desc = "Range for Secret Aura")
    private val skullRange by NumberSetting("Skull Aura Range", default = 3.0, min = 2.1, max = 4.7, increment = 0.1,"Range for Skull")
    private val clickDelay by NumberSetting("Click Delay", default = 50, min = 50, max = 1000, desc = "Delay for Clicking", unit = "ms")
    private val reclickDelay by NumberSetting("Re-Click Delay", default = 500, min = 100, max = 2000, desc = "Delay for Re-Clicking", unit = "ms")
    private val swapSlot by BooleanSetting("Swap Slot", false, "Swap Slot to Click")
    private val swapSlotIdx by NumberSetting("Swap Slot Index", default = 1, min = 1, max = 9, desc = "Slot to Swap").withDependency { swapSlot }

    private val blocksDone = mutableListOf<BlockPos>()
    private val blocksCooldown = mutableMapOf<BlockPos, Long>()
    private var originalSlot = -1
    private var swapTime = 0L
    private var waitingForClick = false
    private var redstoneKey = false
    private val SECRET_SKULL_UUID: String = "e0f3e929-869e-3dca-9504-54c666ee6f23"
    private val REDSTONE_KEY_UUID: String = "fed95410-aba1-39df-9b95-1d4f361eb66e"

    init {
        on<WorldEvent.Load> {
            clearBlocks()
        }

        on<TickEvent.Server> {
            if (!enabled || !DungeonUtils.inDungeons || ScanUtils.currentRoom?.data?.name == "Three Weirdos") return@on

            val player = mc.player?:return@on
            val level = mc.level?:return@on

            val eyePos = player.eyePosition
            val currentTime = System.currentTimeMillis()

            if (waitingForClick && currentTime - swapTime > 1000) {
                waitingForClick = false
                if (originalSlot != -1) {
                    PlayerUtils.setHotbarSlot(originalSlot)
                    originalSlot = -1
                }
            }

            val boxMin = BlockPos((eyePos.x - range).toInt(), (eyePos.y - range).toInt(), (eyePos.z - range).toInt())
            val boxMax = BlockPos((eyePos.x + range).toInt(), (eyePos.y + range).toInt(), (eyePos.z + range).toInt())

            for (blockPos in BlockPos.betweenClosed(boxMin, boxMax)) {
                if (blocksDone.contains(blockPos)) continue

                val cd = blocksCooldown.get(blockPos)
                if (cd != null && cd + reclickDelay > currentTime) continue

                val state = level.getBlockState(blockPos)
                val block = state.block

                if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
                    if (handleChestSecret(blockPos, eyePos, currentTime)) {
                        return@on
                    }
                }

                else if (block == Blocks.LEVER) {
                    if (handleLeverSecret(blockPos, eyePos, currentTime)) {
                        return@on
                    }
                }

                else if (block == Blocks.PLAYER_HEAD || block == Blocks.PLAYER_WALL_HEAD) {
                    if (handleSkullSecret(blockPos, eyePos, currentTime)) {
                        return@on
                    }
                }
                else if (block == Blocks.REDSTONE_BLOCK) {
                    if (handleRedstoneBlockSecret(blockPos, eyePos, currentTime)) {
                        return@on
                    }
                }
            }
        }

        on<PacketEvent.Receive> {
            if (!enabled || !DungeonUtils.inDungeons) return@on

            if (packet is ClientboundOpenScreenPacket) {
                if (packet.type !in CHEST_TYPES) return@on

                val rawTitle = packet.title.string
                val normalizedTitle = rawTitle.replace(Regex("§[0-9a-fk-or]"), "").trim().lowercase()

                if (normalizedTitle in SECRET_CHEST_TITLES) {
                    mc.connection?.send(ServerboundContainerClosePacket(packet.containerId))

                    cancel()
                }
            }
        }
    }

    private fun trySwapBeforeClick(currentTime: Long): Boolean {
        if (!swapSlot) return true

        val player = mc.player ?: return false
        val targetSlot = swapSlotIdx.toInt() - 1

        if (player.inventory.selectedSlot == targetSlot) {
            if (waitingForClick) {
                // 檢查是否已經過了我們設定的 clickDelay
                if (currentTime - swapTime >= clickDelay) {
                    waitingForClick = false // 延遲結束，解鎖！
                    return true
                }
                return false // 還沒過延遲，繼續等
            }
            return true // 本來就在這個槽位，不需延遲直接放行
        }

        // 情況 B：不在目標槽位，執行切換並開始計時
        originalSlot = player.inventory.selectedSlot
        PlayerUtils.setHotbarSlot(targetSlot)
        swapTime = currentTime
        waitingForClick = true
        return false
    }

    private fun handleChestSecret(blockPos: BlockPos, eyePos: Vec3, currentTime: Long): Boolean {
        val aabb = AABB(0.0625, 0.0, 0.0625, 0.9375, 0.875, 0.9375)
        val centerPos = Vec3(blockPos.x + 0.5, blockPos.y + 0.4375, blockPos.z + 0.5);

        if (eyePos.distanceTo(centerPos) <= range) {
            val hitResult = RaytraceUtils.collisionRayTrace(blockPos, aabb, eyePos, centerPos) ?: return false

            if (!trySwapBeforeClick(currentTime)) {
                return true
            }

            mc.connection?.send(ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, 0))
            if (originalSlot != -1) {
                PlayerUtils.setHotbarSlot(originalSlot)
                originalSlot = -1
            }
            blocksCooldown[blockPos.immutable()] = currentTime

            return true
        }

        return false
    }

    private fun handleLeverSecret(blockPos: BlockPos, eyePos: Vec3, currentTime: Long): Boolean {
        val aabb = AABB(0.25, 0.2, 0.25, 0.75, 0.8, 0.75)
        val centerPos = Vec3.atCenterOf(blockPos)

        if (eyePos.distanceTo(centerPos) <= range) {
            val hitResult = RaytraceUtils.collisionRayTrace(blockPos, aabb, eyePos, centerPos) ?: return false

            if (!trySwapBeforeClick(currentTime)) {
                return true
            }

            mc.connection?.send(ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, 0))
            if (originalSlot != -1) {
                PlayerUtils.setHotbarSlot(originalSlot)
                originalSlot = -1
            }
            blocksCooldown[blockPos.immutable()] = currentTime

            return true
        }

        return false
    }

    private fun handleSkullSecret(blockPos: BlockPos, eyePos: Vec3, currentTime: Long): Boolean {
        val blockEntity: BlockEntity? = mc.level!!.getBlockEntity(blockPos)

        if (blockEntity !is SkullBlockEntity) return false

        val profileComponent = blockEntity.ownerProfile ?: return false
        val gameProfile = profileComponent.partialProfile()
        val id = gameProfile.id ?: return false

        val uuid = id.toString()
        val isSecretSkull = (uuid == SECRET_SKULL_UUID)
        val isRedstoneKeySkull = (uuid == REDSTONE_KEY_UUID)

        if (!isSecretSkull && !isRedstoneKeySkull) return false

        if (isRedstoneKeySkull) {
            if (hasRedstoneBlockNearby(blockPos)) {
                redstoneKey = false
                blocksDone.add(blockPos)
                return false
            }
        }

        val aabb = AABB(0.25, 0.0, 0.25, 0.75, 0.5, 0.75)

        val centerPos = Vec3.atLowerCornerOf(blockPos).add(
        (aabb.minX + aabb.maxX) / 2.0,
        (aabb.minY + aabb.maxY) / 2.0,
        (aabb.minZ + aabb.maxZ) / 2.0
        )

        if (eyePos.distanceTo(centerPos) <= skullRange) {
            val hitResult = RaytraceUtils.collisionRayTrace(blockPos, aabb, eyePos, centerPos) ?: return false

            if (!trySwapBeforeClick(currentTime)){
                return true
            }

            mc.connection?.send(ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, 0))

            if (isRedstoneKeySkull) {
                redstoneKey = true
            }

            if (originalSlot != -1) {
                PlayerUtils.setHotbarSlot(originalSlot)
                originalSlot = -1
            }
            blocksCooldown[blockPos.immutable()] = currentTime

            return true
        }
        return false
    }

    private fun handleRedstoneBlockSecret(blockPos: BlockPos, eyePos: Vec3, currentTime: Long): Boolean{
        if (!redstoneKey) return false

        if (hasSkullNearby(blockPos)) {
            blocksDone.add(blockPos)
            redstoneKey = false
            return false
        }

        val aabb = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        val centerPos = Vec3.atLowerCornerOf(blockPos).add(0.5, 0.5, 0.5)

        if (eyePos.distanceTo(centerPos) <= range) {
            val hitResult = RaytraceUtils.collisionRayTrace(blockPos, aabb, eyePos, centerPos) ?: return false

            if (!trySwapBeforeClick(currentTime)) {
                return true
            }

            mc.connection?.send(ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, 0))
            redstoneKey = false

            if (originalSlot != -1) {
                PlayerUtils.setHotbarSlot(originalSlot)
                originalSlot = -1
            }
            blocksCooldown[blockPos.immutable()] = currentTime

            return true
        }
        return false
    }

    private fun hasRedstoneBlockNearby(pos: BlockPos): Boolean {
        return mc.level!!.getBlockState(pos.below()).block == Blocks.REDSTONE_BLOCK ||
                mc.level!!.getBlockState(pos.north()).block == Blocks.REDSTONE_BLOCK ||
                mc.level!!.getBlockState(pos.south()).block == Blocks.REDSTONE_BLOCK ||
                mc.level!!.getBlockState(pos.west()).block == Blocks.REDSTONE_BLOCK ||
                mc.level!!.getBlockState(pos.east()).block == Blocks.REDSTONE_BLOCK;
    }

    private fun hasSkullNearby(pos: BlockPos): Boolean {
        val up = mc.level!!.getBlockState(pos.above()).block
        val north = mc.level!!.getBlockState(pos.north()).block
        val south = mc.level!!.getBlockState(pos.south()).block
        val west = mc.level!!.getBlockState(pos.west()).block
        val east = mc.level!!.getBlockState(pos.east()).block

        return up === Blocks.PLAYER_HEAD || up === Blocks.PLAYER_WALL_HEAD ||
                north === Blocks.PLAYER_HEAD || north === Blocks.PLAYER_WALL_HEAD ||
                south === Blocks.PLAYER_HEAD || south === Blocks.PLAYER_WALL_HEAD ||
                west === Blocks.PLAYER_HEAD || west === Blocks.PLAYER_WALL_HEAD ||
                east === Blocks.PLAYER_HEAD || east === Blocks.PLAYER_WALL_HEAD
    }

    private fun clearBlocks() {
        blocksDone.clear()
        blocksCooldown.clear()
    }

    private val CHEST_TYPES = setOf(
        net.minecraft.world.inventory.MenuType.GENERIC_9x1,
        net.minecraft.world.inventory.MenuType.GENERIC_9x2,
        net.minecraft.world.inventory.MenuType.GENERIC_9x3,
        net.minecraft.world.inventory.MenuType.GENERIC_9x4,
        net.minecraft.world.inventory.MenuType.GENERIC_9x5,
        net.minecraft.world.inventory.MenuType.GENERIC_9x6,
        net.minecraft.world.inventory.MenuType.GENERIC_3x3
    )

    private val SECRET_CHEST_TITLES = setOf("chest", "large chest")
}