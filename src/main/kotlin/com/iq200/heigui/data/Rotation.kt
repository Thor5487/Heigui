package com.iq200.heigui.data

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.RotationUtils
import net.minecraft.world.phys.Vec3
import kotlin.math.abs


class Rotation(var pitch: Float = 0f, var yaw: Float = 0f) {


    constructor(other: Rotation) : this(other.pitch, other.yaw)

    fun getValue(): Float {
        return abs(this.yaw) + abs(this.pitch)
    }

    override fun toString(): String {

        return "Rotation{pitch=$pitch, yaw=$yaw}"
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rotation) return false
        return this.pitch == other.pitch && this.yaw == other.yaw
    }


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



    companion object {
        fun from(to: Vec3, from: Vec3): Rotation {
            return RotationUtils.getRotation(from, to)
        }

        fun from(to: Vec3): Rotation {
            val player = mc.player ?: return Rotation()
            val fromVec = player.position().add(0.0, player.getEyeHeight(player.pose).toDouble(), 0.0)
            return RotationUtils.getRotation(fromVec, to)
        }
    }
}