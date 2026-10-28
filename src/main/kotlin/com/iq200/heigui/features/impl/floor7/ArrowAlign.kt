package com.iq200.heigui.features.impl.floor7

import com.mojang.blaze3d.platform.InputConstants
import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.KeybindSetting
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.EntityInteractEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color.Companion.withAlpha
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.addVec
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.render.drawText
import com.iq200.heigui.utils.skyblock.SplitsManager
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.M7Phases
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

object ArrowAlign : Module(
    name = "Arrow Align",
    description = "Shows the solution for the Arrow Align device",
    category = Category.FLOOR7
) {
    private val blockWrongClick by BooleanSetting(
        "Block Wrong Clicks",
        true,
        desc = "Blocks clicks on arrows that are already facing the correct direction"
    )

    private val preventBlocking = KeybindSetting(
        "Prevent Blocking",
        InputConstants.KEY_LSHIFT,
        desc = "Temporarily bypasses click blocking before terminals start, or after the board is solved"
    ).withDependency { blockWrongClick }

    private val renderAimedHitbox by BooleanSetting(
        "Render Aimed Hitbox",
        true,
        desc = "Highlights the aimed arrow frame during terminals"
    )

    private val recentClickTimestamps = mutableMapOf<Int, Long>()
    private val clicksRemaining = mutableMapOf<Int, Int>()
    private var currentFrameRotations: List<Int>? = null
    private var targetSolution: List<Int>? = null
    private var boardRecognized = false
    private var boardSolved = false
    private var terminalsStarted = false

    private val frameGridCorner = BlockPos(-2, 120, 75)
    private val centerBlock = BlockPos(0, 120, 77)

    init {
        on<TickEvent.End> {
            scanBoard()
        }

        on<EntityInteractEvent> {
            if (DungeonUtils.getF7Phase() != M7Phases.P3) return@on

            val frame = entity as? ItemFrame ?: return@on
            if (frame.item.item != Items.ARROW) return@on

            val frameIndex = getFrameIndex(frame.blockPosition()) ?: return@on

            val rotations = currentFrameRotations ?: return@on
            if (rotations[frameIndex] == MISSING_FRAME) return@on

            val preventHeld = preventBlocking.isDown()
            val canBypass = preventHeld && (!haveTerminalsStarted() || boardSolved)
            val shouldBlock = blockWrongClick && !canBypass

            if (frameIndex !in clicksRemaining && shouldBlock) {
                cancel()
                return@on
            }

            val target = targetSolution?.get(frameIndex) ?: return@on
            if (target == MISSING_FRAME) return@on

            recentClickTimestamps[frameIndex] = System.currentTimeMillis()
            val updatedRotation = (rotations[frameIndex] + 1) % ROTATION_COUNT
            currentFrameRotations = rotations.toMutableList().apply {
                this[frameIndex] = updatedRotation
            }

            val remaining = calculateClicksNeeded(updatedRotation, target)
            if (remaining == 0) {
                clicksRemaining.remove(frameIndex)
            } else {
                clicksRemaining[frameIndex] = remaining
            }
            boardSolved = boardRecognized && clicksRemaining.isEmpty()
        }

        on<RenderEvent.Extract> {
            if (DungeonUtils.getF7Phase() != M7Phases.P3) return@on

            clicksRemaining.forEach { (index, clickNeeded) ->
                val colorCode = when {
                    clickNeeded < 3 -> 'a'
                    clickNeeded < 5 -> '6'
                    else -> 'c'
                }

                drawText(
                    "§$colorCode$clickNeeded",
                    Vec3.atCenterOf(getFramePositionFromIndex(index)).addVec(y = 0.1, x = -0.3),
                    1f,
                    false
                )
            }

            if (!renderAimedHitbox || !haveTerminalsStarted()) return@on

            val hitResult = mc.hitResult as? EntityHitResult ?: return@on
            val frame = hitResult.entity as? ItemFrame ?: return@on
            if (frame.item.item != Items.ARROW) return@on

            val frameIndex = getFrameIndex(frame.blockPosition()) ?: return@on
            val color = if (frameIndex in clicksRemaining) {
                Colors.MINECRAFT_GREEN.withAlpha(0.5f)
            } else {
                Colors.MINECRAFT_RED.withAlpha(0.5f)
            }

            drawStyledBox(frame.boundingBox, color, style = 2, depth = false)
        }

        on<ChatPacketEvent> {
            if (value == TERMINALS_START_MESSAGE) terminalsStarted = true
        }

        on<WorldEvent.Load> {
            resetState()
            terminalsStarted = false
        }
    }

    override fun onEnable() {
        terminalsStarted = isTerminalsSplitStarted()
        super.onEnable()
    }

    override fun onDisable() {
        resetState()
        super.onDisable()
    }

    private fun scanBoard() {
        if (DungeonUtils.getF7Phase() != M7Phases.P3) {
            resetBoard()
            return
        }

        val playerPos = mc.player?.blockPosition() ?: return
        if (playerPos.distSqr(centerBlock) > MAX_SCAN_DISTANCE_SQUARED) {
            resetBoard()
            return
        }

        val rotations = getFrames()
        currentFrameRotations = rotations

        val solution = possibleSolutions.firstOrNull { candidate ->
            candidate.indices.all { index ->
                (candidate[index] == MISSING_FRAME) == (rotations[index] == MISSING_FRAME)
            }
        }

        clicksRemaining.clear()
        targetSolution = solution
        boardRecognized = solution != null

        if (solution == null) {
            boardSolved = false
            return
        }

        solution.indices.forEach { index ->
            val current = rotations[index]
            val target = solution[index]
            if (current == MISSING_FRAME || target == MISSING_FRAME) return@forEach

            val remaining = calculateClicksNeeded(current, target)
            if (remaining != 0) clicksRemaining[index] = remaining
        }

        boardSolved = clicksRemaining.isEmpty()
    }

    private fun getFrames(): List<Int> {
        val itemFrames = mc.level?.entitiesForRendering()
            ?.mapNotNull { entity ->
                (entity as? ItemFrame)?.takeIf { it.item.item == Items.ARROW }
            }
            .orEmpty()

        val now = System.currentTimeMillis()
        return List(FRAME_COUNT) { index ->
            val recentClick = recentClickTimestamps[index]?.let { now - it < LOCAL_ROTATION_GRACE_MS } == true
            if (recentClick) {
                currentFrameRotations?.getOrNull(index) ?: MISSING_FRAME
            } else {
                itemFrames.find { it.blockPosition() == getFramePositionFromIndex(index) }?.rotation
                    ?: MISSING_FRAME
            }
        }
    }

    private fun haveTerminalsStarted(): Boolean {
        if (terminalsStarted) return true

        if (isTerminalsSplitStarted()) terminalsStarted = true
        return terminalsStarted
    }

    private fun isTerminalsSplitStarted(): Boolean =
        SplitsManager.currentSplits.splits.any {
            it.name == TERMINALS_SPLIT_NAME && it.time != 0L
        }

    private fun resetState() {
        resetBoard()
        recentClickTimestamps.clear()
    }

    private fun resetBoard() {
        clicksRemaining.clear()
        currentFrameRotations = null
        targetSolution = null
        boardRecognized = false
        boardSolved = false
    }

    private fun getFramePositionFromIndex(index: Int): BlockPos =
        frameGridCorner.offset(0, index % GRID_SIZE, index / GRID_SIZE)

    private fun getFrameIndex(pos: BlockPos): Int? {
        if (pos.x != frameGridCorner.x) return null

        val index = (pos.y - frameGridCorner.y) + (pos.z - frameGridCorner.z) * GRID_SIZE
        return index.takeIf { it in 0 until FRAME_COUNT }
    }

    private fun calculateClicksNeeded(currentRotation: Int, targetRotation: Int): Int =
        (ROTATION_COUNT - currentRotation + targetRotation) % ROTATION_COUNT

    private val possibleSolutions = listOf(
        listOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1),
        listOf(-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1),
        listOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3),
        listOf(5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1),
        listOf(5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        listOf(7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        listOf(-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
        listOf(-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1),
        listOf(-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1)
    )

    private const val GRID_SIZE = 5
    private const val FRAME_COUNT = GRID_SIZE * GRID_SIZE
    private const val ROTATION_COUNT = 8
    private const val MISSING_FRAME = -1
    private const val MAX_SCAN_DISTANCE_SQUARED = 200.0
    private const val LOCAL_ROTATION_GRACE_MS = 1_000L
    private const val TERMINALS_START_MESSAGE = "[BOSS] Goldor: Who dares trespass into my domain?"
    private const val TERMINALS_SPLIT_NAME = "§6Terminals"
}
