package com.iq200.heigui.features.impl.skyblock.dojo

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.RotationUtils
import com.iq200.heigui.utils.render.drawWireFrameBox
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal object Control {
    private var updateTicks = 0
    private var target: WitherSkeleton? = null
    private var lastPosition: Vec3? = null
    private var predictionOffset: Vec3? = null
    private var previousPredictionOffset: Vec3? = null
    private var lastPredictionUpdate = 0L

    fun onEntityLoad(entity: Entity) {
        if (target != null || !Dojo.activateFromEvent(DojoChallenge.CONTROL)) return
        val player = mc.player ?: return
        val witherSkeleton = entity as? WitherSkeleton ?: return
        if (!witherSkeleton.isAlive || !witherSkeleton.closerThan(player, 50.0, 5.0)) return
        target = witherSkeleton
    }

    fun update() {
        val currentTarget = target
        if (currentTarget != null && (!currentTarget.isAlive || currentTarget.isRemoved)) {
            reset()
            return
        }
        if (currentTarget == null) return

        updateTicks++
        if (updateTicks < UPDATE_INTERVAL_TICKS) return
        updateTicks = 0

        val currentPosition = currentTarget.position()
        val previousPosition = lastPosition
        if (previousPosition != null) {
            previousPredictionOffset = predictionOffset
            val movement = currentPosition.subtract(previousPosition).multiply(1.0, 0.1, 1.0)
            val pingSeconds = Dojo.adjustedPing() / 1000.0
            predictionOffset = movement.scale(1.0 + UPDATE_INTERVAL_TICKS / 20.0 + pingSeconds)
            if (previousPredictionOffset == null) previousPredictionOffset = predictionOffset
        }

        lastPosition = currentPosition
        lastPredictionUpdate = System.currentTimeMillis()
    }

    fun render(event: RenderEvent.Extract) {
        val player = mc.player ?: return
        val currentTarget = target ?: return
        if (!currentTarget.isAlive || currentTarget.isRemoved) return

        val currentOffset = predictionOffset ?: return
        val oldOffset = previousPredictionOffset ?: return
        val partialTick = event.context.gameRenderer().mainCamera().getCameraEntityPartialTicks(mc.deltaTracker)
        val progress = ((System.currentTimeMillis() - lastPredictionUpdate) / UPDATE_INTERVAL_MS)
            .coerceIn(0.0, 1.0)
        val smoothedOffset = oldOffset.scale(1.0 - progress).add(currentOffset.scale(progress))
        val aimPosition = currentTarget.getEyePosition(partialTick).add(smoothedOffset)
        val playerEyePosition = player.getEyePosition(partialTick)

        val viewVector = if (Dojo.controlAutoAim) {
            val rotation = RotationUtils.getRotation(playerEyePosition, aimPosition)
            setPlayerRotation(rotation.yaw, rotation.pitch)
            Vec3.directionFromRotation(rotation.pitch, rotation.yaw)
        } else {
            player.getViewVector(partialTick)
        }

        val targetBox = AABB(aimPosition.add(-0.5, -0.5, -0.5), aimPosition.add(0.5, 0.5, 0.5))
        val isLookingAtTarget = targetBox.clip(
            playerEyePosition,
            playerEyePosition.add(viewVector.scale(AIM_CHECK_DISTANCE))
        ).isPresent

        event.drawWireFrameBox(
            targetBox,
            if (isLookingAtTarget) AIMED_COLOR else TARGET_COLOR,
            thickness = 3f,
            depth = false
        )
    }

    private fun setPlayerRotation(yaw: Float, pitch: Float) {
        val player = mc.player ?: return
        player.yRot = yaw
        player.xRot = pitch
        player.yRotO = yaw
        player.xRotO = pitch
    }

    fun reset() {
        updateTicks = 0
        target = null
        lastPosition = null
        predictionOffset = null
        previousPredictionOffset = null
        lastPredictionUpdate = 0L
    }

    private const val UPDATE_INTERVAL_TICKS = 3
    private const val UPDATE_INTERVAL_MS = 150.0
    private const val AIM_CHECK_DISTANCE = 30.0
    private val AIMED_COLOR = Color(0, 255, 0)
    private val TARGET_COLOR = Color(192, 192, 192)
}
