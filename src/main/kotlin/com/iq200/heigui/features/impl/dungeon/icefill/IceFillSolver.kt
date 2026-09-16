package com.iq200.heigui.features.impl.dungeon.icefill

import com.iq200.heigui.Heigui.mc
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import kotlin.math.floor

object IceFillSolver {
    fun solve(): List<BlockPos>? {
        val player = mc.player ?: return null
        val offsetX = floor((player.x + 200.0) / 32.0).toInt() * 32 - 200
        val offsetZ = floor((player.z + 200.0) / 32.0).toInt() * 32 - 200

        IceFillPatterns.starts.forEachIndexed { rotation, (x, z) ->
            val startX = offsetX + x
            val startZ = offsetZ + z

            if (!isIceStart(BlockPos(startX, 69, startZ))) return@forEachIndexed

            return scanAllFloors(IceFillPoint(startX, 70, startZ), rotation)
        }

        return null
    }

    private fun scanAllFloors(pos: IceFillPoint, rotation: Int): List<BlockPos>? {
        val starts = listOf(
            pos,
            pos + transformTo(IceFillPoint(5, 1, 0), rotation),
            pos + transformTo(IceFillPoint(12, 2, 0), rotation)
        )

        val fullPattern = mutableListOf<BlockPos>()
        starts.forEachIndexed { floorIndex, start ->
            val scanned = scan(start, floorIndex, rotation)?.toMutableList() ?: return null
            scanned.add(0, IceFillPoint(0, 0, 0))
            scanned.add(scanned.last() + IceFillPoint(1, 1, 0))

            scanned
                .map { start + transformTo(it, rotation) }
                .mapTo(fullPattern) { BlockPos(it.x, it.y, it.z) }
        }

        return fullPattern
    }

    private fun scan(pos: IceFillPoint, floorIndex: Int, rotation: Int): List<IceFillPoint>? {
        val checks = IceFillPatterns.representativeFloors.getOrNull(floorIndex) ?: return null

        for ((index, check) in checks.withIndex()) {
            val airPoint = transform(check.airX, check.airZ, rotation).let { pos + it }
            val solidPoint = transform(check.solidX, check.solidZ, rotation).let { pos + it }

            if (isAir(airPoint) && !isAir(solidPoint)) {
                return IceFillPatterns.floors.getOrNull(floorIndex)?.getOrNull(index)
            }
        }

        return null
    }

    private fun transform(x: Int, z: Int, rotation: Int): IceFillPoint {
        return when (rotation) {
            0 -> IceFillPoint(-z, 0, x)
            1 -> IceFillPoint(-x, 0, -z)
            2 -> IceFillPoint(z, 0, -x)
            else -> IceFillPoint(x, 0, z)
        }
    }

    private fun transformTo(point: IceFillPoint, rotation: Int): IceFillPoint {
        return when (rotation) {
            0 -> IceFillPoint(-point.z, point.y, point.x)
            1 -> IceFillPoint(-point.x, point.y, -point.z)
            2 -> IceFillPoint(point.z, point.y, -point.x)
            else -> point
        }
    }

    private fun isIceStart(pos: BlockPos): Boolean {
        val state = mc.level?.getBlockState(pos) ?: return false
        return state.`is`(Blocks.ICE) || state.`is`(Blocks.PACKED_ICE)
    }

    private fun isAir(point: IceFillPoint): Boolean {
        return mc.level?.getBlockState(BlockPos(point.x, point.y, point.z))?.isAir == true
    }

    private operator fun IceFillPoint.plus(other: IceFillPoint): IceFillPoint =
        IceFillPoint(x + other.x, y + other.y, z + other.z)
}
