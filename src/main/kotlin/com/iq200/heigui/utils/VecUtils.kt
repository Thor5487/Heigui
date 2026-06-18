package com.iq200.heigui.utils

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class Vec2i(val x: Int, val z: Int)
data class Vec2(val x: Double, val z: Double)

operator fun Vec3.component1(): Double = x
operator fun Vec3.component2(): Double = y
operator fun Vec3.component3(): Double = z

operator fun BlockPos.component1(): Int = x
operator fun BlockPos.component2(): Int = y
operator fun BlockPos.component3(): Int = z

operator fun Vec3.unaryMinus(): Vec3 = Vec3(-x, -y, -z)

fun Vec3.floorVec(): Vec3 =
    Vec3(floor(x), floor(y), floor(z))

fun Vec3.addVec(x: Number = 0.0, y: Number = 0.0, z: Number = 0.0): Vec3 =
    Vec3(this.x + x.toDouble(), this.y + y.toDouble(), this.z + z.toDouble())

/**
 * Rotates a Vec3 around the given rotation.
 * @param rotation The rotation to rotate around
 * @return The rotated Vec3
 */
fun Vec3.rotateAroundNorth(rotation: Rotations): Vec3 =
    when (rotation) {
        Rotations.NORTH -> Vec3(-this.x, this.y, -this.z)
        Rotations.WEST ->  Vec3(-this.z, this.y, this.x)
        Rotations.SOUTH -> Vec3(this.x, this.y, this.z)
        Rotations.EAST ->  Vec3(this.z, this.y, -this.x)
        else -> this
    }

fun Vec3.toYawPitch(): Pair<Float, Float> {
    return PlayerUtils.getRotationsTo(Vec3(0.0, 0.0, 0.0), this)
}

fun BlockPos.rotateAroundNorth(rotation: Rotations): BlockPos =
    when (rotation) {
        Rotations.NORTH -> BlockPos(-this.x, this.y, -this.z)
        Rotations.WEST ->  BlockPos(-this.z, this.y, this.x)
        Rotations.SOUTH -> BlockPos(this.x, this.y, this.z)
        Rotations.EAST ->  BlockPos(this.z, this.y, -this.x)
        else -> this
    }

fun Vec3.toBlockPos(): BlockPos {
    return BlockPos(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
}

fun BlockPos.toVec3(): Vec3 {
    return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

fun BlockPos.toVec3Center(): Vec3 {
    return Vec3(this.x + 0.5, this.y + 0.5, this.z + 0.5)
}

fun BlockPos.toVec3TopCenter(): Vec3 {
    return Vec3(this.x + 0.5, this.y + 1.0, this.z + 0.5)
}

fun BlockPos.toVec3BottomCenter(): Vec3 {
    return Vec3(x + 0.5, y.toDouble(), z + 0.5)
}
/**
 * Rotates a Vec3 to the given rotation.
 * @param rotation The rotation to rotate to
 * @return The rotated Vec3
 */

fun Vec3.offset(i: Double, j: Double, k: Double): Vec3 {
    return Vec3(x + i, y + j, z + k)
}

fun Vec3.offset(vec3: Vec3): Vec3{
    return Vec3(x + vec3.x, y + vec3.y, z + vec3.z)
}

fun BlockPos.rotateToNorth(rotation: Rotations): BlockPos =
    when (rotation) {
        Rotations.NORTH -> BlockPos(-this.x, this.y, -this.z)
        Rotations.WEST ->  BlockPos(this.z, this.y, -this.x)
        Rotations.SOUTH -> BlockPos(this.x, this.y, this.z)
        Rotations.EAST ->  BlockPos(-this.z, this.y, this.x)
        else -> this
    }

fun Vec3.rotateToNorth(rotation: Rotations): Vec3 =
    when (rotation) {
        Rotations.NORTH -> Vec3(-this.x, this.y, -this.z)
        Rotations.WEST ->  Vec3(this.z, this.y, -this.x)
        Rotations.SOUTH -> Vec3(this.x, this.y, this.z)
        Rotations.EAST ->  Vec3(-this.z, this.y, this.x)
        else -> this
    }

fun isXZInterceptable(box: AABB, range: Double, pos: Vec3, yaw: Float, pitch: Float): Boolean {
    val start = pos.addVec(y = (mc.player?.eyeY ?: 0.0))
    val goal = start.add(getLook(yaw, pitch).multiply(range, range, range))

    return isVecInZ(start.intermediateWithXValue(goal, box.minX), box) ||
            isVecInZ(start.intermediateWithXValue(goal, box.maxX), box) ||
            isVecInX(start.intermediateWithZValue(goal, box.minZ), box) ||
            isVecInX(start.intermediateWithZValue(goal, box.maxZ), box)
}

private fun getLook(yaw: Float, pitch: Float): Vec3 {
    val f2 = -cos(-pitch * 0.017453292f).toDouble()
    return Vec3(
        sin(-yaw * 0.017453292f - 3.1415927f) * f2,
        sin(-pitch * 0.017453292f).toDouble(),
        cos(-yaw * 0.017453292f - 3.1415927f) * f2
    )
}

private fun isVecInX(vec: Vec3?, box: AABB): Boolean =
    vec != null && vec.x >= box.minX && vec.x <= box.maxX

private fun isVecInZ(vec: Vec3?, box: AABB): Boolean =
    vec != null && vec.z >= box.minZ && vec.z <= box.maxZ

private fun Vec3.intermediateWithXValue(goal: Vec3, x: Double): Vec3? {
    val dx = goal.x - this.x
    if (dx * dx < 1e-8) return null
    val t = (x - this.x) / dx
    return if (t in 0.0..1.0) Vec3(
        this.x + dx * t,
        this.y + (goal.y - this.y) * t,
        this.z + (goal.z - this.z) * t
    ) else null
}

private fun Vec3.intermediateWithZValue(goal: Vec3, z: Double): Vec3? {
    val dz = goal.z - this.z
    if (dz * dz < 1e-8) return null
    val t = (z - this.z) / dz
    return if (t in 0.0..1.0) Vec3(
        this.x + (goal.x - this.x) * t,
        this.y + (goal.y - this.y) * t,
        this.z + dz * t
    ) else null
}

fun Vec3.toJsonObject(): JsonObject {
    return JsonObject().apply {
        addProperty("x", this@toJsonObject.x)
        addProperty("y", this@toJsonObject.y)
        addProperty("z", this@toJsonObject.z)
    }
}

fun Vec3.subtract(x: Double, y: Double, z: Double): Vec3 =
    Vec3(this.x - x, this.y - y, this.z - z)

fun Vec3.divide(x: Double, y: Double, z: Double): Vec3 =
    Vec3(this.x / x, this.y / y, this.z / z)

fun Vec3.divide(f: Double): Vec3 =
    Vec3(this.x / f, this.y / f, this.z / f)

// 2. 四捨五入與進位 (對應 RSM 的 round)
fun Vec3.round(places: Int = 0): Vec3 {
    if (places == 0) return Vec3(x.roundToInt().toDouble(), y.roundToInt().toDouble(), z.roundToInt().toDouble())
    val factor = 10.0.pow(places.toDouble())
    return Vec3(
        (this.x * factor).roundToInt() / factor,
        (this.y * factor).roundToInt() / factor,
        (this.z * factor).roundToInt() / factor
    )
}

// 3. 根據麥塊方向 (Direction) 位移 (對應 RSM 的 shift)
fun Vec3.shift(dir: Direction, amount: Double): Vec3 =
    when (dir) {
        Direction.UP -> this.add(0.0, amount, 0.0)
        Direction.DOWN -> this.add(0.0, -amount, 0.0)
        Direction.WEST -> this.add(-amount, 0.0, 0.0)
        Direction.SOUTH -> this.add(0.0, 0.0, amount)
        Direction.NORTH -> this.add(0.0, 0.0, -amount)
        Direction.EAST -> this.add(amount, 0.0, 0.0)
    }

// 4. JSON 序列化 (對應 RSM 的 getAsJsonPrimitive / fromJsonPrimitive)
fun Vec3.toJsonPrimitive(): JsonPrimitive =
    JsonPrimitive("$x $y $z")

fun JsonPrimitive.toVec3(): Vec3 {
    val parts = this.asString.trim().split("\\s+".toRegex())
    require(parts.size == 3) { "Invalid Vec3 format: \"${this.asString}\"" }
    return Vec3(parts[0].toDouble(), parts[1].toDouble(), parts[2].toDouble())
}

// 5. 伴生物件工廠方法 (對應 RSM 的 static fromRotation)
// 因為 Kotlin 無法直接對未聲明 Companion 的 Java 類別 (Vec3) 加 Companion 擴充
// 所以我們提供一個檔案層級的靜態函數
fun vec3FromRotation(pitch: Float, yaw: Float): Vec3 {
    val f = cos(-yaw * 0.017453292 - Math.PI)
    val f1 = sin(-yaw * 0.017453292 - Math.PI)
    val f2 = -cos(-pitch * 0.017453292)
    val f3 = sin(-pitch * 0.017453292)
    return Vec3(f1 * f2, f3, f * f2).normalize()
}

fun Vec3.multiply(m : Double) : Vec3 {
    return this.multiply(m, m, m)
}