package com.iq200.heigui.features.impl.skyblock.dojo

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.RotationUtils
import com.iq200.heigui.utils.render.drawLine
import com.iq200.heigui.utils.render.drawText
import net.minecraft.core.BlockPos
import net.minecraft.world.item.BowItem
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.abs

internal object Mastery {
    private val targets = linkedMapOf<BlockPos, Long>()
    private var holdingUse = false
    private var cooldownTicks = 0
    private var lastAimFrame = 0L

    fun onBlockUpdate(pos: BlockPos, state: BlockState) {
        if (!Dojo.activateFromEvent(DojoChallenge.MASTERY)) return
        val immutablePos = pos.immutable()

        if (state.`is`(Blocks.WOOL.lime())) {
            val player = mc.player ?: return
            val distance = player.position().distanceTo(aimPosition(immutablePos))
            val travelTime = (distance * 1000.0 / ARROW_SPEED_BLOCKS_PER_SECOND).toLong()
            val releaseTime = System.currentTimeMillis() + BLOCK_LIFETIME_MS - Dojo.adjustedPing() - travelTime
            targets.putIfAbsent(immutablePos, releaseTime)
        } else if (state.isAir) {
            targets.remove(immutablePos)
        }
    }

    fun update() {
        val player = mc.player ?: return stopDrawing()
        if (mc.gui.screen() != null || player.mainHandItem.item !is BowItem) {
            stopDrawing()
            return
        }

        if (cooldownTicks > 0) {
            cooldownTicks--
            return
        }

        val targetEntry = targets.entries.firstOrNull() ?: return stopDrawing()
        if (!holdingUse) {
            mc.options.keyUse.isDown = true
            holdingUse = true
        }

        if (System.currentTimeMillis() < targetEntry.value || player.getTicksUsingItem() < FULL_BOW_CHARGE_TICKS) return
        if (!isAimedAt(aimPosition(targetEntry.key))) return

        mc.options.keyUse.isDown = false
        mc.gameMode?.releaseUsingItem(player)
        holdingUse = false
        targets.remove(targetEntry.key)
        cooldownTicks = 1
    }

    fun render(event: RenderEvent.Extract) {
        val player = mc.player ?: return
        val entries = targets.entries.toList()
        if (entries.isEmpty()) {
            lastAimFrame = 0L
            return
        }

        val partialTick = event.context.gameRenderer().mainCamera().getCameraEntityPartialTicks(mc.deltaTracker)
        val playerEyePosition = player.getEyePosition(partialTick)
        val firstTarget = aimPosition(entries[0].key)
        smoothAimAt(playerEyePosition, firstTarget)

        event.drawLine(listOf(playerEyePosition, firstTarget), TARGET_COLOR, depth = false, thickness = 2f)
        if (entries.size >= 2) {
            event.drawLine(
                listOf(firstTarget, aimPosition(entries[1].key)),
                TARGET_COLOR,
                depth = false,
                thickness = 2f
            )
        }

        val now = System.currentTimeMillis()
        for ((pos, releaseTime) in entries) {
            val seconds = ((releaseTime - now).coerceAtLeast(0L)) / 1000.0
            event.drawText(
                String.format(Locale.ROOT, "%.2f", seconds),
                Vec3.atCenterOf(pos).add(0.0, 1.0, 0.0),
                scale = 3f,
                depth = false
            )
        }
    }

    private fun smoothAimAt(from: Vec3, to: Vec3) {
        val player = mc.player ?: return
        val targetRotation = RotationUtils.getRotation(from, to)
        val now = System.nanoTime()
        val deltaSeconds = if (lastAimFrame == 0L) {
            1.0 / 60.0
        } else {
            ((now - lastAimFrame) / 1_000_000_000.0).coerceIn(0.0, 0.05)
        }
        lastAimFrame = now

        val maxStep = (Dojo.masteryAimSpeed * deltaSeconds).toFloat()
        val yawDelta = RotationUtils.wrapAngleTo180(targetRotation.yaw - player.yRot)
        val pitchDelta = targetRotation.pitch - player.xRot
        val yaw = player.yRot + yawDelta.coerceIn(-maxStep, maxStep)
        val pitch = (player.xRot + pitchDelta.coerceIn(-maxStep, maxStep)).coerceIn(-90f, 90f)
        setPlayerRotation(yaw, pitch)
    }

    private fun isAimedAt(target: Vec3): Boolean {
        val player = mc.player ?: return false
        val targetRotation = RotationUtils.getRotation(player.eyePosition, target)
        val yawError = abs(RotationUtils.wrapAngleTo180(targetRotation.yaw - player.yRot))
        val pitchError = abs(targetRotation.pitch - player.xRot)
        return yawError <= AIM_TOLERANCE && pitchError <= AIM_TOLERANCE
    }

    private fun aimPosition(pos: BlockPos): Vec3 = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0)

    private fun setPlayerRotation(yaw: Float, pitch: Float) {
        val player = mc.player ?: return
        player.yRot = yaw
        player.xRot = pitch
        player.yRotO = yaw
        player.xRotO = pitch
    }

    private fun stopDrawing() {
        if (!holdingUse) return
        mc.options.keyUse.isDown = false
        holdingUse = false
    }

    fun reset() {
        stopDrawing()
        targets.clear()
        cooldownTicks = 0
        lastAimFrame = 0L
    }

    private const val BLOCK_LIFETIME_MS = 6550L
    private const val ARROW_SPEED_BLOCKS_PER_SECOND = 60.0
    private const val FULL_BOW_CHARGE_TICKS = 20
    private const val AIM_TOLERANCE = 2.0f
    private val TARGET_COLOR = Color(192, 192, 192)
}
