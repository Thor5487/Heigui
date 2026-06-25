package com.iq200.heigui.utils.camera

import net.minecraft.world.phys.Vec3


//From RSM
interface CameraProvider {
    val priority: Int
        get() = 100

    fun shouldOverridePosition(): Boolean
    fun shouldOverrideYaw(): Boolean
    fun shouldOverridePitch(): Boolean

    fun shouldOverrideHitPos(): Boolean
    fun shouldOverrideHitRot(): Boolean

    fun shouldBlockKeyboardMovement(): Boolean
    fun shouldBlockMouseMovement(): Boolean

    fun isActive(): Boolean {
        return shouldOverridePosition() || shouldOverrideYaw() || shouldOverridePitch()
    }

    fun getCameraPosition(): Vec3? = null
    fun getYaw(): Float
    fun getPitch(): Float
    fun getPosForHit(): Vec3?
    fun getRotForHit(): Vec3
}

interface CameraPositionProvider : CameraProvider {
    override fun shouldOverrideYaw() = false
    override fun shouldOverridePitch() = false
    override fun shouldBlockMouseMovement() = false
    override fun getYaw() = 0f
    override fun getPitch() = 0f
}

interface CameraRotationProvider : CameraProvider {
    override fun shouldOverridePosition() = false
    override fun shouldOverrideHitPos() = false
    override fun shouldOverrideHitRot() = false
    override fun getCameraPosition(): Vec3 = Vec3.ZERO
    override fun getPosForHit(): Vec3? = Vec3.ZERO
    override fun getRotForHit(): Vec3 = Vec3.ZERO
    override fun shouldBlockKeyboardMovement() = false
}

interface ClientRotationProvider {
    fun isClientRotationActive(): Boolean
    fun allowClientKeyInputs(): Boolean

    fun isDesyncPaused(): Boolean = false
    fun onDesyncDisable() {}
    fun onDesyncPause() {}
}