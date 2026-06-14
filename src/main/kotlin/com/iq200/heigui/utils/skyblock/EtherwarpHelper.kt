package com.iq200.heigui.utils.skyblock

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.component1
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.toBlockPos
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.BigDripleafStemBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BubbleColumnBlock
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.ComparatorBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.DryVegetationBlock
import net.minecraft.world.level.block.FireBlock
import net.minecraft.world.level.block.FlowerBlock
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.GrowingPlantBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.MushroomBlock
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.NetherWartBlock
import net.minecraft.world.level.block.RedStoneWireBlock
import net.minecraft.world.level.block.RedstoneTorchBlock
import net.minecraft.world.level.block.RepeaterBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.level.block.SeagrassBlock
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.SmallDripleafBlock
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.StemBlock
import net.minecraft.world.level.block.SugarCaneBlock
import net.minecraft.world.level.block.TallFlowerBlock
import net.minecraft.world.level.block.TallGrassBlock
import net.minecraft.world.level.block.TallSeagrassBlock
import net.minecraft.world.level.block.TorchBlock
import net.minecraft.world.level.block.TripWireBlock
import net.minecraft.world.level.block.TripWireHookBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.WallSkullBlock
import net.minecraft.world.level.block.WebBlock
import net.minecraft.world.level.block.WoolCarpetBlock
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.phys.Vec3
import java.util.BitSet
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sign

object EtherwarpHelper {
    fun rayTraceBlockPos(player: Player, distance: Double = 60.0): Vec3? {
        val level = player.level()
        // 取得視線起點
        val eyeHeight = PlayerUtils.SNEAK_EYE_HEIGHT
        val startVec = player.position().add(0.0, eyeHeight, 0.0)

        // 取得視線終點
        val lookVec = player.lookAngle
        val endVec = startVec.add(lookVec.scale(distance))

        return traverseVoxels(startVec, endVec, level)
    }

    fun rayTracePoints(level: Level, startVec: Vec3, endVec: Vec3, isEtherwarp: Boolean = true): Vec3? {
        return traverseVoxels(startVec, endVec, level, isEtherwarp)
    }

    private fun traverseVoxels(startVec: Vec3, endVec: Vec3, level: Level, isEtherwarp: Boolean = true): Vec3? {
        val x0 = startVec.x
        val y0 = startVec.y
        val z0 = startVec.z

        val x1 = endVec.x
        val y1 = endVec.y
        val z1 = endVec.z

        // 將起點轉為整數網格
        var x = floor(x0).toInt()
        var y = floor(y0).toInt()
        var z = floor(z0).toInt()

        val dirX = x1 - x0
        val dirY = y1 - y0
        val dirZ = z1 - z0

        val isBoundaryX = x1 % 1.0 == 0.0
        val isBoundaryY = y1 % 1.0 == 0.0
        val isBoundaryZ = z1 % 1.0 == 0.0

        val endX = floor(if (dirX < 0 && isBoundaryX) x1 - 0.5 else x1).toInt()
        val endY = floor(if (dirY < 0 && isBoundaryY) y1 - 0.5 else y1).toInt()
        val endZ = floor(if (dirZ < 0 && isBoundaryZ) z1 - 0.5 else z1).toInt()

        val stepX = sign(dirX).toInt()
        val stepY = sign(dirY).toInt()
        val stepZ = sign(dirZ).toInt()

        val invDirX = if (dirX != 0.0) 1.0 / dirX else Double.MAX_VALUE
        val invDirY = if (dirY != 0.0) 1.0 / dirY else Double.MAX_VALUE
        val invDirZ = if (dirZ != 0.0) 1.0 / dirZ else Double.MAX_VALUE

        val tDeltaX = abs(invDirX * stepX)
        val tDeltaY = abs(invDirY * stepY)
        val tDeltaZ = abs(invDirZ * stepZ)

        // 初始化 tMax
        var tMaxX = abs((x + max(stepX, 0) - x0) * invDirX)
        var tMaxY = abs((y + max(stepY, 0) - y0) * invDirY)
        var tMaxZ = abs((z + max(stepZ, 0) - z0) * invDirZ)

        // 🌟 新增：用來追蹤上一次跨越發生在哪一軸
        var hitAxis = 0 // 1: X, 2: Y, 3: Z

        // 初始化命中檢查：如果起點方塊本身就是實體方塊
        run {
            val blockPos = BlockPos(x, y, z)
            val chunk = level.getChunk(SectionPos.blockToSectionCoord(blockPos.x), SectionPos.blockToSectionCoord(blockPos.z))
            val currentBlockState = chunk.getBlockState(blockPos)
            val currentBlockId = Block.getId(currentBlockState.block.defaultBlockState())

            if (!validEtherwarpFeetIds.get(currentBlockId)) {
                // 如果起點就是實體，撞擊點就是起點本身
                return startVec
            }
        }

        repeat(1000) {
            // 演算法推演：往最近的一個網格邊界跨越一步
            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> {
                    tMaxX += tDeltaX
                    x += stepX
                    hitAxis = 1
                }
                tMaxY <= tMaxZ -> {
                    tMaxY += tDeltaY
                    y += stepY
                    hitAxis = 2
                }
                else -> {
                    tMaxZ += tDeltaZ
                    z += stepZ
                    hitAxis = 3
                }
            }

            // 取得當下方塊的狀態
            val blockPos = BlockPos(x, y, z)
            val chunk = level.getChunk(SectionPos.blockToSectionCoord(blockPos.x), SectionPos.blockToSectionCoord(blockPos.z))
            val currentBlockState = chunk.getBlockState(blockPos)
            val currentBlockId = Block.getId(currentBlockState.block.defaultBlockState())

            // 🌟 如果撞到實體方塊 (白名單外)
            if (!validEtherwarpFeetIds.get(currentBlockId)) {

                // --- 🌟 A. 物理空間檢查 (維持白名單特性) ---
                if (isEtherwarp) {
                    val footBlockId = Block.getId(chunk.getBlockState(blockPos.above(1)).block.defaultBlockState())
                    if (!validEtherwarpFeetIds.get(footBlockId)) return null // 卡住了

                    val headBlockId = Block.getId(chunk.getBlockState(blockPos.above(2)).block.defaultBlockState())
                    if (!validEtherwarpFeetIds.get(headBlockId)) return null // 卡住了
                }

                // --- 🌟 B. 核心：精確撞擊點反算數學 ---
                // 找出進入這一格方塊時，射線所處的精確 t 距離
                val hitT = when (hitAxis) {
                    1 -> tMaxX - tDeltaX // X 軸跨越
                    2 -> tMaxY - tDeltaY // Y 軸跨越
                    else -> tMaxZ - tDeltaZ // Z 軸跨越
                }

                // 利用參數方程：HitPoint = Start + T * Dir
                val preciseHitVec = Vec3(
                    x0 + (hitT * dirX),
                    y0 + (hitT * dirY),
                    z0 + (hitT * dirZ)
                )

                // 回傳這顆完美的實體方塊 (Full Block) 座標，與雷射打在上面的精確交界點
                return preciseHitVec
            }

            // 如果抵達了視線盡頭
            if (x == endX && y == endY && z == endZ) {
                return null
            }
        }

        return null
    }


    fun isHitTarget(hitVec3: Vec3?, targetVec: Vec3): Boolean {
        if (hitVec3 == null) return false

        if (hitVec3.distanceTo(targetVec) < 0.05) {
            return true
        }

        val fixX = round(hitVec3.x * 10000.0) / 10000.0
        val fixY = round(hitVec3.y * 10000.0) / 10000.0
        val fixZ = round(hitVec3.z * 10000.0) / 10000.0

        val fixedHitPos = BlockPos(
            floor(fixX).toInt(),
            floor(fixY).toInt(),
            floor(fixZ).toInt()
        )

        return fixedHitPos == targetVec.toBlockPos()
    }

    fun applyBoundaryOffset(hitVec3: Vec3?, startVec: Vec3): Vec3? {
        if (hitVec3 == null) return null

        val dirX = hitVec3.x - startVec.x
        val dirY = hitVec3.y - startVec.y
        val dirZ = hitVec3.z - startVec.z

        val eps = 0.001
        val checkX = if (hitVec3.x % 1.0 == 0.0) hitVec3.x + sign(dirX) * eps else hitVec3.x
        val checkY = if (hitVec3.y % 1.0 == 0.0) hitVec3.y + sign(dirY) * eps else hitVec3.y
        val checkZ = if (hitVec3.z % 1.0 == 0.0) hitVec3.z + sign(dirZ) * eps else hitVec3.z

        return Vec3(checkX, checkY, checkZ)
    }


    private val validTypes = setOf(
        ButtonBlock::class, CarpetBlock::class, SkullBlock::class,
        WallSkullBlock::class, LadderBlock::class, SaplingBlock::class,
        FlowerBlock::class, StemBlock::class, CropBlock::class,
        BaseRailBlock::class, SnowLayerBlock::class, BubbleColumnBlock::class,
        TripWireBlock::class, TripWireHookBlock::class, FireBlock::class,
        AirBlock::class, TorchBlock::class, FlowerPotBlock::class,
        TallFlowerBlock::class, TallGrassBlock::class, BushBlock::class,
        SeagrassBlock::class, TallSeagrassBlock::class, SugarCaneBlock::class,
        LiquidBlock::class, VineBlock::class, MushroomBlock::class, GrowingPlantBlock::class,
        PistonHeadBlock::class, WoolCarpetBlock::class, WebBlock::class,
        DryVegetationBlock::class, SmallDripleafBlock::class, LeverBlock::class,
        NetherWartBlock::class, NetherPortalBlock::class, RedStoneWireBlock::class,
        ComparatorBlock::class, RedstoneTorchBlock::class, RepeaterBlock::class, BigDripleafStemBlock::class
    )

    private val validEtherwarpFeetIds = BitSet().apply {
        BuiltInRegistries.BLOCK.forEach { block ->
            if (validTypes.any { it.isInstance(block) }) {
                set(Block.getId(block.defaultBlockState()))
            }
        }
    }
}