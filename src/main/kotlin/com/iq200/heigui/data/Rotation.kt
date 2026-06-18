package com.iq200.heigui.data

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.RotationUtils
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

// 1. 主建構子 (Primary Constructor)：直接寫在類別名稱後面，並可以給定預設值
class Rotation(var pitch: Float = 0f, var yaw: Float = 0f) {

    // 2. 次建構子 (Copy Constructor)：使用 constructor 關鍵字，並呼叫 this()
    constructor(other: Rotation) : this(other.pitch, other.yaw)

    fun getValue(): Float {
        return abs(this.yaw) + abs(this.pitch)
    }

    override fun toString(): String {
        // Kotlin 支援字串模板，不需要用 + 號串接
        return "Rotation{pitch=$pitch, yaw=$yaw}"
    }

    // 3. 覆寫 equals 必須加上 override 關鍵字，且參數型別必須是 Any?
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rotation) return false
        return this.pitch == other.pitch && this.yaw == other.yaw
    }

    // 覆寫了 equals 通常也強烈建議覆寫 hashCode
    override fun hashCode(): Int {
        var result = pitch.hashCode()
        result = 31 * result + yaw.hashCode()
        return result
    }

    fun distance(): Float {
        return this.pitchSq() + this.yawSq()
    }

    fun yawSq(): Float {
        return this.yaw * this.yaw
    }

    fun pitchSq(): Float {
        return this.pitch * this.pitch
    }

    // 4. Companion Object (伴生物件)：相當於 Java 的 static 方法
    // 因為 from() 是用來「產生」一個新的 Rotation，做成靜態方法最合適
    companion object {
        fun from(to: Vec3, from: Vec3): Rotation {
            return RotationUtils.getRotation(from, to)
        }

        fun from(to: Vec3): Rotation {
            val player = mc.player ?: return Rotation() // 加上 Null 安全檢查
            val fromVec = player.position().add(0.0, player.getEyeHeight(player.pose).toDouble(), 0.0)
            return RotationUtils.getRotation(fromVec, to)
        }
    }
}