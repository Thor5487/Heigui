package com.iq200.heigui.utils

import com.iq200.heigui.data.Rotation
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.*
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.shapes.CollisionContext
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RotationUtils {
    fun rotateVector(x: Float, y: Float, deltaYaw: Float): Vec2 {
        val radians = Math.toRadians(deltaYaw.toDouble())
        val sin = sin(radians)
        val cos = cos(radians)
        return Vec2((x * cos + y * sin).toFloat(), (y * cos - x * sin).toFloat())
    }

    fun constructMovementVector(input: Input): Vec2 {
        val f = calculateImpulse(input.forward(), input.backward())
        val g = calculateImpulse(input.left(), input.right())
        return Vec2(f, g).normalized() // Original function has f and g switched for some fuckass reason
    }

    fun calculateImpulse(bl: Boolean, bl2: Boolean): Float {
        return if (bl == bl2) {
            0.0f
        } else {
            if (bl) 1.0f else -1.0f
        }
    }

    fun wrapAngleTo360(angle: Float): Float {
        var angle = angle
        angle %= 360f
        if (angle < 0) angle += 360f
        return angle
    }

    fun wrapAngleTo180(angle: Float): Float {
        var angle = angle
        angle = angle % 360.0f

        while (angle >= 180) {
            angle -= 360.0f
        }
        while (angle < -180.0f) {
            angle += 360.0f
        }

        return angle
    }

    fun wrapAngleTo180(angle: Double): Double {
        var angle = angle
        angle %= 360.0

        while (angle >= 180) {
            angle -= 360.0
        }
        while (angle < -180.0) {
            angle += 360.0
        }

        return angle
    }

    fun getRotation(from: Vec3, to: Vec3): Rotation {
        val diffX = to.x() - from.x()
        val diffY = to.y() - from.y()
        val diffZ = to.z() - from.z()
        val dist = sqrt(diffX * diffX + diffZ * diffZ)

        var pitch = -atan2(dist, diffY).toFloat()
        var yaw = atan2(diffZ, diffX).toFloat()
        pitch = wrapAngleTo180((pitch * 180f / Math.PI + 90) * -1).toFloat()
        yaw = wrapAngleTo180((yaw * 180 / Math.PI) - 90).toFloat()

        return Rotation(pitch, yaw)
    }

    fun getBlockHitResult(d: Double, yaw: Float, pitch: Float, eyePos: Vec3): HitResult? {
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return null
        val vec32 = Minecraft.getInstance().player!!.calculateViewVector(pitch, yaw) // Reversed for some reason
        val vec33 = eyePos.add(vec32.x * d, vec32.y * d, vec32.z * d)
        return Minecraft.getInstance().level!!.clip(
            ClipContext(
                eyePos,
                vec33,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                CollisionContext.placementContext(Minecraft.getInstance().player)
            )
        )
    }

    fun collisionRayTrace(pos: BlockPos, box: AABB, start: Vec3, end: Vec3): BlockHitResult? {
        val localStart = start.subtract(pos.getX().toDouble(), pos.getY().toDouble(), pos.getZ().toDouble())
        val localEnd = end.subtract(pos.getX().toDouble(), pos.getY().toDouble(), pos.getZ().toDouble())

        val hit = box.clip(localStart, localEnd)

        if (hit.isEmpty()) {
            return null
        }

        val hitPosLocal = hit.get()
        val hitPosWorld = hitPosLocal.add(pos.getX().toDouble(), pos.getY().toDouble(), pos.getZ().toDouble())

        val face = Direction.getApproximateNearest(
            hitPosLocal.x - box.getCenter().x,
            hitPosLocal.y - box.getCenter().y,
            hitPosLocal.z - box.getCenter().z
        )
        return BlockHitResult(hitPosWorld, face, pos, false)
    }

    fun getRotationAABB(from: Vec3, to: AABB): Rotation? {
        val randomX = if (to.minX == to.maxX) to.maxX else ThreadLocalRandom.current().nextDouble(to.minX, to.maxX)
        val randomY = if (to.minY == to.maxY) to.maxY else ThreadLocalRandom.current().nextDouble(to.minY, to.maxY)
        val randomZ = if (to.minZ == to.maxZ) to.maxZ else ThreadLocalRandom.current().nextDouble(to.minZ, to.maxZ)

        val diffX = randomX - from.x()
        val diffY = randomY - from.y()
        val diffZ = randomZ - from.z()

        val dist = sqrt(diffX * diffX + diffZ * diffZ)

        var pitch = -atan2(dist, diffY).toFloat()
        var yaw = atan2(diffZ, diffX).toFloat()

        pitch = wrapAngleTo180((pitch * 180f / Math.PI + 90) * -1).toFloat()
        yaw = wrapAngleTo180((yaw * 180 / Math.PI) - 90).toFloat()

        return Rotation(pitch, yaw)
    }

    fun getRotationAABBExact(from: Vec3, to: AABB): Rotation? {
        val diffX = to.maxX - from.x()
        val diffY = to.maxY - from.y()
        val diffZ = to.maxZ - from.z()

        val dist = sqrt(diffX * diffX + diffZ * diffZ)

        var pitch = -atan2(dist, diffY).toFloat()
        var yaw = atan2(diffZ, diffX).toFloat()

        pitch = wrapAngleTo180((pitch * 180f / Math.PI + 90) * -1).toFloat()
        yaw = wrapAngleTo180((yaw * 180 / Math.PI) - 90).toFloat()

        return Rotation(pitch, yaw)
    }
}