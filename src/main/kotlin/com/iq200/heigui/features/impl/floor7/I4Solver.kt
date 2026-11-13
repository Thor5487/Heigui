package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.events.BlockUpdateEvent
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.EntityDataUpdateEvent
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.PlayerInputEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color.Companion.withAlpha
import com.iq200.heigui.utils.Colors
//#if PRIVATE
import com.iq200.heigui.utils.RotationUtils
//#endif
import com.iq200.heigui.utils.alert
import com.iq200.heigui.utils.itemId
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.render.drawFilledBox
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.skyblock.ActionBarParser
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.M7Phases
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object I4Solver : Module(
    name = "I4 Solver",
    description = "Shows completed I4 positions and the optimized Terminator aim point.",
    category = Category.FLOOR7
) {
    private val renderMode by SelectorSetting(
        "Render Mode",
        "Filled Outline",
        listOf("Fill", "Outline", "Filled Outline"),
        "Rendering style for I4 targets."
    )
    private val shotMode by SelectorSetting(
        "Shot Mode",
        "Passive",
        listOf("Passive", "Active"),
        "Passive predicts shots from server ticks; Active changes vanilla's right-click delay and keeps startUseItem behavior."
    )
    private val prediction by BooleanSetting(
        "Prediction",
        true,
        "Prefire the next assumed target before the server confirms the current target is complete."
    )
    private val i4CompleteNotification by BooleanSetting(
        "I4 Complete Notification",
        true,
        "Shows a chat message and title when the I4 device is confirmed complete."
    )
    //#if PRIVATE
    private val preventOvershoot by BooleanSetting(
        "Prevent Overshoot",
        true,
        "Locks Terror aim to the horizontal center until the view leaves the I4 grid vertically."
    )
    //#endif
    private val doneColor by ColorSetting(
        "Complete Color",
        Colors.MINECRAFT_RED.withAlpha(0.5f),
        allowAlpha = true,
        desc = "Color of completed positions."
    )
    private val aimPointColor by ColorSetting(
        "Aim Point Color",
        Colors.MINECRAFT_AQUA,
        allowAlpha = true,
        desc = "Color of the Terminator spread aim point."
    )
    private val aimPixelSize by NumberSetting(
        "Aim Pixel Size",
        0.25,
        0.05,
        1.0,
        0.05,
        unit = "blocks",
        desc = "Full width, height, and depth of the aim pixel."
    )
    private val predictionTimeoutTicks by NumberSetting(
        "Prediction Timeout",
        10,
        1,
        10,
        1,
        unit = "ticks",
        desc = "Server ticks to wait for a use-item prediction to be confirmed by the current Emerald disappearing."
    ).withDependency { prediction }
    private val shotIntervalTicks by NumberSetting(
        "Shot Interval",
        5,
        5,
        10,
        1,
        unit = "ticks",
        desc = "Server ticks between predicted shots while holding right click."
    )
    private val deviceBlocks = setOf(
        BlockPos(68, 130, 50), BlockPos(66, 130, 50), BlockPos(64, 130, 50),
        BlockPos(68, 128, 50), BlockPos(66, 128, 50), BlockPos(64, 128, 50),
        BlockPos(68, 126, 50), BlockPos(66, 126, 50), BlockPos(64, 126, 50)
    )

    private val doneBlocks = mutableSetOf<BlockPos>()
    private val speculativeDoneBlocks = mutableSetOf<BlockPos>()
    private val speculativeAgeTicks = mutableMapOf<BlockPos, Int>()
    private var target: BlockPos? = null
    private var solverTarget: BlockPos? = null
    private var spreadTarget: BlockPos? = null
    private var aimPoint: Vec3? = null
    private var previousGeneralAimX: Double? = null
    private var lastTerrorAimState: Boolean? = null
    private var isI4Complete = false
    //#if PRIVATE
    private var terrorCenterYawLocked = false
    //#endif

    @Volatile private var isHoldingRightClick = false
    @Volatile private var shotCycleActive = false
    @Volatile private var tickTimer = 0

    init {
        on<InputEvent> {
            val useKey = (mc.options.keyUse as KeyMappingAccessor).key
            if (key != useKey) return@on

            if (isRelease) {
                stopShotTimer()
                return@on
            }

            if (!isPress || isHoldingRightClick || mc.gui.screen() != null || mc.player == null) return@on

            isHoldingRightClick = true
            startShotCycleIfEligible()
        }

        on<PlayerInputEvent.Use>(priority = 1_000) {
            if (shotMode == ACTIVE_MODE && isShotControlEligible()) onPredictedShot()
        }

        on<TickEvent.Server> {
            refreshTerrorAimState()
            if (!prediction && speculativeDoneBlocks.isNotEmpty()) {
                rollbackSpeculativePrediction()
            }
            checkSpeculativeTimeouts()
            if (!isHoldingRightClick) return@on
            if (mc.gui.screen() != null || mc.player == null) {
                stopShotTimer()
                return@on
            }

            if (shotMode != PASSIVE_MODE) {
                shotCycleActive = false
                tickTimer = 0
                return@on
            }

            if (!isShotControlEligible()) {
                shotCycleActive = false
                tickTimer = 0
                return@on
            }

            if (!shotCycleActive) {
                startShotCycleIfEligible()
                return@on
            }

            tickTimer++
            if (tickTimer >= shotIntervalTicks) {
                tickTimer = 0
                triggerShot()
            }
        }

        on<BlockUpdateEvent> {
            if (DungeonUtils.getF7Phase() != M7Phases.P3 || isI4Complete || pos !in deviceBlocks) return@on

            if (old.block == Blocks.EMERALD_BLOCK && updated.block == Blocks.DYED_TERRACOTTA.blue()) {
                val predictionAlreadyAdvanced = solverTarget != null && solverTarget != pos
                doneBlocks.add(pos.immutable())
                speculativeDoneBlocks.remove(pos)
                speculativeAgeTicks.remove(pos)
                if (target == pos) target = null

                if (predictionAlreadyAdvanced) {
                    updateAimSolution()
                } else if (prediction) {
                    advancePredictionAfterDisappearance()
                } else {
                    solverTarget = null
                    clearAimSolution()
                }
                return@on
            }

            if (updated.block == Blocks.EMERALD_BLOCK) {
                target = pos.immutable()
                val predictionMatched = solverTarget == pos || pos in speculativeDoneBlocks
                val predictionCanHitTarget = solverTarget?.let { predicted ->
                    predicted.y == pos.y && (
                        isTerrorAimActive() || abs(predicted.x - pos.x) == DEVICE_COLUMN_SPACING
                    )
                } == true

                // Terror covers the full row; the general three-arrow aim covers adjacent positions.
                // Keep it stable instead of snapping to the newly confirmed target.
                if (!predictionMatched && predictionCanHitTarget) return@on

                if (!predictionMatched) {
                    speculativeDoneBlocks.clear()
                    speculativeAgeTicks.clear()
                    solverTarget = pos.immutable()
                }
                updateAimSolution()
            }
        }

        on<RenderEvent.Extract> {
            if (DungeonUtils.getF7Phase() != M7Phases.P3 || !isOnDevice()) {
                resetI4State()
                return@on
            }

            aimPoint?.let { point ->
                drawFilledBox(
                    aimPointBounds(point),
                    aimPointColor,
                    depth = true
                )
            }
            doneBlocks.forEach { drawStyledBox(AABB(it), doneColor, renderMode, depth = true) }
        }

        on<WorldEvent.Load> {
            reset()
        }

        on<ChatPacketEvent> {
            if (value == GOLDOR_START_MESSAGE) reset()
        }

        on<EntityDataUpdateEvent> {
            if (DungeonUtils.getF7Phase() != M7Phases.P3 || !isPlayerInI4Room() || isI4Complete) return@on
            if (entity.name.string == ACTIVE_ENTITY_NAME) onI4Complete()
        }
    }

    private fun onI4Complete() {
        isI4Complete = true
        resetI4State()
        stopShotTimer()
        if (!i4CompleteNotification) return

        modMessage("§aI4 device complete")
        alert("§aI4 Complete")
    }

    /** Future prediction/prefire behavior should be triggered from this single hook. */
    private fun onPredictedShot() {
        if (!prediction) return
        tryAdvancePrefire()
    }

    private fun startShotCycleIfEligible() {
        if (!isShotControlEligible()) return

        if (shotMode != PASSIVE_MODE) {
            shotCycleActive = false
            tickTimer = 0
            return
        }

        shotCycleActive = true
        tickTimer = 0
        triggerShot()
    }

    private fun triggerShot() {
        if (shotMode == PASSIVE_MODE) onPredictedShot()
    }

    /** Used by MixinMinecraft to preserve vanilla startUseItem while changing only its repeat delay. */
    @JvmStatic
    fun modifyRightClickDelay(originalDelay: Int): Int {
        if (!enabled || shotMode != ACTIVE_MODE || !isShotControlEligible()) return originalDelay
        return shotIntervalTicks
    }

    //#if PRIVATE
    /** Called after vanilla has applied sensitivity and mouse inversion, immediately before LocalPlayer.turn. */
    @JvmStatic
    fun adjustMouseTurnForOvershoot(yawInput: Double, pitchInput: Double): DoubleArray {
        val player = mc.player
        val point = aimPoint
        if (
            !enabled || !preventOvershoot || mc.gui.screen() != null || player == null || point == null ||
            DungeonUtils.getF7Phase() != M7Phases.P3 || !isOnDevice() || !isTerrorAimActive()
        ) {
            resetOvershootGuard()
            return doubleArrayOf(yawInput, pitchInput)
        }

        if (!isLookingAtI4Grid()) {
            resetOvershootGuard()
            return doubleArrayOf(yawInput, pitchInput)
        }

        // Once centered, horizontal movement stays locked in both directions.
        // Pitch remains untouched so the player can leave the grid vertically to unlock it.
        if (terrorCenterYawLocked) return doubleArrayOf(0.0, pitchInput)

        val center = Vec3(TERROR_CENTER_X, player.eyePosition.y, DEVICE_FRONT_Z)
        val targetYaw = RotationUtils.getRotation(player.eyePosition, center).yaw
        val yawError = RotationUtils.wrapAngleTo180(targetYaw - player.yRot).toDouble()
        val appliedRotation = yawInput * PLAYER_TURN_SCALE
        if (abs(yawError) <= TERROR_CENTER_YAW_EPSILON) {
            terrorCenterYawLocked = true
            return doubleArrayOf(0.0, pitchInput)
        }

        val movingTowardTarget = yawError * appliedRotation > 0.0
        if (!movingTowardTarget || abs(appliedRotation) < abs(yawError)) {
            return doubleArrayOf(yawInput, pitchInput)
        }

        terrorCenterYawLocked = true
        return doubleArrayOf(yawError / PLAYER_TURN_SCALE, pitchInput)
    }

    private fun isLookingAtI4Grid(): Boolean {
        val player = mc.player ?: return false
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(PREFIRE_RAY_LENGTH))
        return I4_GRID_BOUNDS.clip(start, end).isPresent
    }

    private fun resetOvershootGuard() {
        terrorCenterYawLocked = false
    }
    //#endif

    private fun isShotControlEligible(): Boolean {
        if (isI4Complete || !isHoldingTerminator()) return false

        return isHoldingRightClick &&
            DungeonUtils.getF7Phase() == M7Phases.P3 &&
            isOnDevice()
    }

    private fun isHoldingTerminator(): Boolean {
        val player = mc.player ?: return false
        return isHoldingRightClick && player.mainHandItem.itemId == TERMINATOR_ID
    }

    private fun stopShotTimer() {
        isHoldingRightClick = false
        shotCycleActive = false
        tickTimer = 0
    }

    private fun isOnDevice(): Boolean {
        val player = mc.player ?: return false
        return abs(player.y - 127.0) < 0.5 && player.x in 62.0..65.0 && player.z in 34.0..37.0
    }

    private fun isPlayerInI4Room(): Boolean =
        mc.player?.let { I4_ROOM_BOUNDING_BOX.contains(it.position()) } == true

    private fun updateAimSolution(preferredSpreadTarget: BlockPos? = null) {
        val current = solverTarget ?: target ?: return clearAimSolution()
        if (isTerrorAimActive()) {
            spreadTarget = null
            aimPoint = calculateTerrorAimPoint(current)
            return
        }

        val adjacentTargets = deviceBlocks
            .filter { candidate ->
                candidate.y == current.y &&
                    abs(candidate.x - current.x) == DEVICE_COLUMN_SPACING
            }
        val unfinishedTargets = adjacentTargets.filterNot {
            it in doneBlocks || it in speculativeDoneBlocks
        }
        val candidates = unfinishedTargets.ifEmpty { adjacentTargets }

        spreadTarget = preferredSpreadTarget
            ?.takeIf { it in candidates }
            ?: selectGeneralSpreadTarget(current, candidates)
        aimPoint = calculateGeneralAimPoint(current, spreadTarget)
        aimPoint?.let { previousGeneralAimX = it.x }
    }

    private fun refreshTerrorAimState() {
        val terror = isTerrorAimActive()
        if (lastTerrorAimState == terror) return

        lastTerrorAimState = terror
        updateAimSolution()
    }

    private fun isTerrorAimActive(): Boolean =
        ActionBarParser.stacks >= TERROR_STACK_THRESHOLD && isWearingTerrorArmor()

    private fun isWearingTerrorArmor(): Boolean {
        val player = mc.player ?: return false
        return ARMOR_SLOTS.any { player.getItemBySlot(it).itemId in TERROR_ARMOR_IDS }
    }

    private fun selectGeneralSpreadTarget(current: BlockPos, candidates: List<BlockPos>): BlockPos? {
        if (candidates.size <= 1) return candidates.firstOrNull()

        val previousX = previousGeneralAimX
        if (previousX != null) {
            candidates.firstOrNull { candidate ->
                calculateGeneralAimPoint(current, candidate)?.x == previousX
            }?.let { return it }
        }

        return candidates.random()
    }

    private fun tryAdvancePrefire() {
        val current = solverTarget ?: target ?: return
        if (!isAimedAtCurrentAimPoint()) return

        if (speculativeDoneBlocks.add(current)) {
            // Timeout only applies when useItem predicts the Emerald that currently exists.
            // Guessed future positions have nothing on the server to time out against yet.
            if (current == target) speculativeAgeTicks[current] = 0
        }
        advanceToNextPredictedTarget()
    }

    /**
     * The server-confirmed Emerald disappeared before its successor appeared.
     * Predict a successor even when no useItem prediction happened for that Emerald.
     */
    private fun advancePredictionAfterDisappearance() {
        advanceToNextPredictedTarget()
    }

    private fun advanceToNextPredictedTarget() {
        val remainingTargets = deviceBlocks.filterNot {
            it in doneBlocks || it in speculativeDoneBlocks
        }
        if (remainingTargets.isEmpty()) {
            solverTarget = null
            clearAimSolution()
            return
        }

        if (isTerrorAimActive()) {
            solverTarget = selectBestTerrorPrediction(remainingTargets)
            updateAimSolution()
            return
        }

        val choice = selectBestGeneralPrediction(remainingTargets)
        solverTarget = choice?.target
        if (choice == null) clearAimSolution() else updateAimSolution(choice.spreadTarget)
    }

    /** Terror covers an entire row, so prefer the row containing the most unfinished positions. */
    private fun selectBestTerrorPrediction(remainingTargets: List<BlockPos>): BlockPos? {
        val rows = remainingTargets.groupBy { it.y }
        val highestCoverage = rows.maxOfOrNull { it.value.size } ?: return null
        return rows.values
            .filter { it.size == highestCoverage }
            .randomOrNull()
            ?.randomOrNull()
    }

    /**
     * General Terminator spread covers one horizontal gap. Score every gap by how many of its
     * two positions remain unfinished, then keep the previous side when multiple gaps tie.
     */
    private fun selectBestGeneralPrediction(remainingTargets: List<BlockPos>): GeneralPredictionChoice? {
        val remaining = remainingTargets.toSet()
        val scoredPairs = deviceBlocks.flatMap { first ->
            deviceBlocks.mapNotNull { second ->
                if (first.y != second.y || second.x - first.x != DEVICE_COLUMN_SPACING) return@mapNotNull null
                val coverage = listOf(first, second).count { it in remaining }
                if (coverage == 0) null else ScoredAimPair(first, second, coverage)
            }
        }
        val highestCoverage = scoredPairs.maxOfOrNull { it.coverage } ?: return null
        val bestPairs = scoredPairs.filter { it.coverage == highestCoverage }
        val previousX = previousGeneralAimX
        val selectedPair = previousX?.let { x ->
            bestPairs.firstOrNull { calculateGeneralAimPoint(it.first, it.second)?.x == x }
        } ?: bestPairs.randomOrNull() ?: return null

        val target = listOf(selectedPair.first, selectedPair.second)
            .filter { it in remaining }
            .randomOrNull()
            ?: return null
        val spreadTarget = if (target == selectedPair.first) selectedPair.second else selectedPair.first
        return GeneralPredictionChoice(target, spreadTarget)
    }

    private fun checkSpeculativeTimeouts() {
        if (speculativeAgeTicks.isEmpty()) return

        var timedOut = false
        speculativeAgeTicks.replaceAll { _, age ->
            val updatedAge = age + 1
            if (updatedAge >= predictionTimeoutTicks) timedOut = true
            updatedAge
        }
        if (!timedOut) return

        rollbackSpeculativePrediction()
    }

    private fun rollbackSpeculativePrediction() {
        speculativeDoneBlocks.clear()
        speculativeAgeTicks.clear()
        solverTarget = target?.takeIf { it !in doneBlocks }
        if (solverTarget == null) clearAimSolution() else updateAimSolution()
    }

    private fun isAimedAtCurrentAimPoint(): Boolean {
        val player = mc.player ?: return false
        val point = aimPoint ?: return false
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(PREFIRE_RAY_LENGTH))
        return aimPointBounds(point).clip(start, end).isPresent
    }

    private fun aimPointBounds(point: Vec3): AABB {
        val halfSize = aimPixelSize / 2.0
        return AABB(
            point.x - halfSize,
            point.y - halfSize,
            point.z - aimPixelSize,
            point.x + halfSize,
            point.y + halfSize,
            point.z
        )
    }

    /** General solver: one of the two horizontal gaps on each of the three device rows. */
    private fun calculateGeneralAimPoint(current: BlockPos, adjacent: BlockPos?): Vec3? {
        adjacent ?: return null
        return Vec3(
            (current.x + adjacent.x) / 2.0 + 0.5,
            current.y + 1.0,
            DEVICE_FRONT_Z
        )
    }

    /** Terror five-arrow aim: fixed to the top edge of the middle target column. */
    private fun calculateTerrorAimPoint(current: BlockPos): Vec3 = Vec3(
        TERROR_CENTER_X,
        current.y + 1.0,
        DEVICE_FRONT_Z
    )

    private fun clearAimSolution() {
        spreadTarget = null
        aimPoint = null
        //#if PRIVATE
        resetOvershootGuard()
        //#endif
    }

    private fun reset() {
        resetI4State()
        isI4Complete = false
        stopShotTimer()
    }

    private fun resetI4State() {
        doneBlocks.clear()
        speculativeDoneBlocks.clear()
        speculativeAgeTicks.clear()
        target = null
        solverTarget = null
        clearAimSolution()
        previousGeneralAimX = null
        lastTerrorAimState = null
    }

    private data class ScoredAimPair(
        val first: BlockPos,
        val second: BlockPos,
        val coverage: Int
    )

    private data class GeneralPredictionChoice(
        val target: BlockPos,
        val spreadTarget: BlockPos
    )

    override fun onEnable() {
        reset()
        super.onEnable()
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private const val GOLDOR_START_MESSAGE = "[BOSS] Goldor: Who dares trespass into my domain?"
    private const val ACTIVE_ENTITY_NAME = "Active"
    private const val TERMINATOR_ID = "TERMINATOR"
    private const val PASSIVE_MODE = 0
    private const val ACTIVE_MODE = 1
    private const val DEVICE_COLUMN_SPACING = 2
    private const val DEVICE_FRONT_Z = 49.95
    private const val TERROR_STACK_THRESHOLD = 10
    private const val TERROR_CENTER_X = 66.5
    private const val PREFIRE_RAY_LENGTH = 64.0
    //#if PRIVATE
    private const val PLAYER_TURN_SCALE = 0.15
    private const val TERROR_CENTER_YAW_EPSILON = 1.0E-3
    private val I4_GRID_BOUNDS = AABB(64.0, 126.0, 49.9, 69.0, 131.0, 50.1)
    //#endif
    private val I4_ROOM_BOUNDING_BOX = AABB(20.0, 100.0, 30.0, 89.0, 151.0, 51.0)
    private val ARMOR_SLOTS = listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    )
    private val TERROR_ARMOR_IDS = setOf(
        "TERROR_HELMET",
        "TERROR_CHESTPLATE",
        "TERROR_LEGGINGS",
        "TERROR_BOOTS"
    )
}
