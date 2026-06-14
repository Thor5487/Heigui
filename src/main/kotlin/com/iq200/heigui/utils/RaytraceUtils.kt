package com.iq200.heigui.utils

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object RaytraceUtils {
    fun collisionRayTrace(blockPos: BlockPos, aabb: AABB, start: Vec3, end: Vec3): BlockHitResult? {
        val worldAABB: AABB = aabb.move(blockPos)

        val hitVecOpt = worldAABB.clip(start, end)

        if (hitVecOpt.isEmpty) {
            return null
        }

        val hit = hitVecOpt.get()

        val side = getSideHit(worldAABB, hit, start)

        return BlockHitResult(hit, side, blockPos, false)
    }

    private fun getSideHit(aabb: AABB, hit: Vec3, start: Vec3): Direction {
        val eps = 0.0001

        when {
            abs(hit.x - aabb.minX) < eps -> return Direction.WEST
            abs(hit.x - aabb.maxX) < eps -> return Direction.EAST
            abs(hit.y - aabb.minY) < eps -> return Direction.DOWN
            abs(hit.y - aabb.maxY) < eps -> return Direction.UP
            abs(hit.z - aabb.minZ) < eps -> return Direction.NORTH
            abs(hit.z - aabb.maxZ) < eps -> return Direction.SOUTH
        }

        val direction = hit.subtract(start).normalize()
        val absX = abs(direction.x)
        val absY = abs(direction.y)
        val absZ = abs(direction.z)

        return when {
            absX > absY && absX > absZ -> if (direction.x > 0) Direction.EAST else Direction.WEST

            absY > absZ -> if (direction.y > 0) Direction.UP else Direction.DOWN

            else -> if (direction.z > 0) Direction.SOUTH else Direction.NORTH
        }
    }
}