package com.iq200.heigui.features.impl.dungeon.icefill

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.InputKey
import com.iq200.mixin.accessors.KeyMappingAccessor
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.block.Blocks
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.floor

object IceFillWalker {
    private data class Segment(
        val dir: Int,
        val endIndex: Int,
        val sprint: Boolean
    )

    private val movementKeys = listOf(
        InputKey.FORWARD,
        InputKey.RIGHT,
        InputKey.BACKWARD,
        InputKey.LEFT
    )

    private val controlledKeys = movementKeys + listOf(InputKey.SNEAK, InputKey.JUMP)
    private var waitingTurnIndex = -1
    private var waitingTicksLeft = 0
    private var completedWaitTurnIndex = -1
    private var completedSprintStopTurnIndex = -1

    fun isManualInputDown(): Boolean {
        val options = mc.options

        return isPhysicallyDown(options.keyUp) ||
                isPhysicallyDown(options.keyRight) ||
                isPhysicallyDown(options.keyDown) ||
                isPhysicallyDown(options.keyLeft) ||
                isPhysicallyDown(options.keyJump) ||
                isPhysicallyDown(options.keyShift)
    }

    fun walk(
        path: List<BlockPos>,
        turnWaitTicks: Int
    ): Boolean {
        val player = mc.player ?: return true
        if (player.y < 70.0) return true

        val keyStates = controlledKeys.associateWith { false }.toMutableMap()
        val x = player.x
        val y = player.y
        val z = player.z
        val motion = player.deltaMovement
        val path2d = path.map { it.x to it.z }.toMutableList()
        val dirOffset = floor((((player.yRot + 360f) % 360f + 45f) / 90f).toDouble()).toInt() % 4

        while (path2d.size > 1) {
            val current = path2d.removeAt(0)
            if (current.first != floor(x).toInt() || current.second != floor(z).toInt()) continue

            val currentIndex = findCurrentPathIndex(path, current, y)
            val next = path2d.first()
            val dir = calcDir(current, next)
            val relativeKeys = rotatedMovementKeys(dirOffset)

            val segment = getSegment(path, currentIndex) ?: return true
            val atTurn = isSegmentBoundary(path, currentIndex)
            val previousDir = if (currentIndex > 0) calcDir(path[currentIndex - 1].to2d(), path[currentIndex].to2d()) else -1
            val previousSegmentSprint = isPreviousSegmentSprint(path, currentIndex, previousDir)

            keyStates[InputKey.SNEAK] = !segment.sprint

            if (atTurn) {
                if (previousSegmentSprint) {
                    if (completedSprintStopTurnIndex != currentIndex && !hasReachedSprintStop(
                            x,
                            z,
                            motion.x,
                            motion.z,
                            path[currentIndex],
                            previousDir,
                            player.horizontalCollision,
                            player.minorHorizontalCollision
                        )
                    ) {
                        keyStates[InputKey.SNEAK] = false
                        pressDirection(keyStates, relativeKeys, previousDir)
                        applyKeyStates(keyStates)
                        return false
                    }

                    completedSprintStopTurnIndex = currentIndex
                }

                if ((previousSegmentSprint || segment.sprint) &&
                    waitAtTurn(currentIndex, turnWaitTicks, !segment.sprint, keyStates)
                ) {
                    return false
                }

            }

            val correction = calcCorrection(x, z, motion.x, motion.z, current, dir)
            pressDirection(keyStates, relativeKeys, dir)
            pressDirection(keyStates, relativeKeys, correction)

            applyKeyStates(keyStates)
            return false
        }

        return true
    }

    fun reset() {
        waitingTurnIndex = -1
        waitingTicksLeft = 0
        completedWaitTurnIndex = -1
        completedSprintStopTurnIndex = -1

        applyKeyStates(
            mapOf(
                InputKey.FORWARD to isPhysicallyDown(mc.options.keyUp),
                InputKey.RIGHT to isPhysicallyDown(mc.options.keyRight),
                InputKey.BACKWARD to isPhysicallyDown(mc.options.keyDown),
                InputKey.LEFT to isPhysicallyDown(mc.options.keyLeft),
                InputKey.SNEAK to isPhysicallyDown(mc.options.keyShift),
                InputKey.JUMP to isPhysicallyDown(mc.options.keyJump)
            )
        )
    }

    private fun applyKeyStates(keyStates: Map<InputKey, Boolean>) {
        val player = mc.player ?: return
        val options = mc.options

        keyStates[InputKey.FORWARD]?.let { options.keyUp.isDown = it }
        keyStates[InputKey.RIGHT]?.let { options.keyRight.isDown = it }
        keyStates[InputKey.BACKWARD]?.let { options.keyDown.isDown = it }
        keyStates[InputKey.LEFT]?.let { options.keyLeft.isDown = it }
        keyStates[InputKey.SNEAK]?.let {
            options.keyShift.isDown = it
            player.isShiftKeyDown = it
        }
        keyStates[InputKey.JUMP]?.let { options.keyJump.isDown = it }

        val current = player.input.keyPresses
        player.input.keyPresses = Input(
            keyStates[InputKey.FORWARD] ?: current.forward,
            keyStates[InputKey.BACKWARD] ?: current.backward,
            keyStates[InputKey.LEFT] ?: current.left,
            keyStates[InputKey.RIGHT] ?: current.right,
            keyStates[InputKey.JUMP] ?: current.jump,
            keyStates[InputKey.SNEAK] ?: current.shift,
            current.sprint
        )
    }

    private fun waitAtTurn(
        turnIndex: Int,
        waitTicks: Int,
        sneak: Boolean,
        keyStates: MutableMap<InputKey, Boolean>
    ): Boolean {
        if (waitTicks <= 0 || completedWaitTurnIndex == turnIndex) return false

        if (waitingTurnIndex != turnIndex) {
            waitingTurnIndex = turnIndex
            waitingTicksLeft = waitTicks
        }

        if (waitingTicksLeft <= 0) {
            completedWaitTurnIndex = turnIndex
            waitingTurnIndex = -1
            return false
        }

        waitingTicksLeft--
        keyStates[InputKey.SNEAK] = sneak
        applyKeyStates(keyStates)
        return true
    }

    private fun hasReachedSprintStop(
        x: Double,
        z: Double,
        motionX: Double,
        motionZ: Double,
        end: BlockPos,
        dir: Int,
        horizontalCollision: Boolean,
        minorHorizontalCollision: Boolean
    ): Boolean {
        val nearExpectedWall = when (dir) {
            0 -> z >= end.z + 0.68
            1 -> x <= end.x + 0.32
            2 -> z <= end.z + 0.32
            3 -> x >= end.x + 0.68
            else -> false
        }
        if (!nearExpectedWall) return false

        val frontVelocityStopped = when (dir) {
            0 -> abs(motionZ) < 0.003
            1 -> abs(motionX) < 0.003
            2 -> abs(motionZ) < 0.003
            3 -> abs(motionX) < 0.003
            else -> false
        }

        return horizontalCollision || minorHorizontalCollision || frontVelocityStopped
    }

    private fun calcDir(from: Pair<Int, Int>, to: Pair<Int, Int>): Int {
        val xDiff = to.first - from.first
        val zDiff = to.second - from.second

        return when {
            zDiff > 0 -> 0
            xDiff < 0 -> 1
            zDiff < 0 -> 2
            xDiff > 0 -> 3
            else -> -1
        }
    }

    private fun findCurrentPathIndex(path: List<BlockPos>, current: Pair<Int, Int>, playerY: Double): Int {
        return path.withIndex()
            .filter { (_, pos) -> pos.x == current.first && pos.z == current.second }
            .minByOrNull { (_, pos) -> abs(pos.y - playerY) }
            ?.index ?: -1
    }

    private fun getSegment(path: List<BlockPos>, index: Int): Segment? {
        if (index !in 0 until path.lastIndex) return null

        val dir = calcDir(path[index].to2d(), path[index + 1].to2d())
        if (dir == -1) return null
        if (path[index].y != path[index + 1].y) return Segment(dir, index + 1, false)

        var endIndex = path.lastIndex
        for (i in index + 1 until path.lastIndex) {
            if (path[i].y != path[i + 1].y || calcDir(path[i].to2d(), path[i + 1].to2d()) != dir) {
                endIndex = i
                break
            }
        }

        val length = endIndex - index
        return Segment(dir, endIndex, length >= 3 && hasAndesiteStop(path[endIndex], dir))
    }

    private fun isPreviousSegmentSprint(path: List<BlockPos>, index: Int, dir: Int): Boolean {
        if (index <= 0 || dir == -1) return false

        var startIndex = index - 1
        while (startIndex > 0 &&
            path[startIndex - 1].y == path[startIndex].y &&
            calcDir(path[startIndex - 1].to2d(), path[startIndex].to2d()) == dir
        ) {
            startIndex--
        }

        val length = index - startIndex
        return length >= 3 && hasAndesiteStop(path[index], dir)
    }

    private fun hasAndesiteStop(end: BlockPos, dir: Int): Boolean {
        val stop = end.offset(dirOffsetX(dir), 0, dirOffsetZ(dir))
        return isStone(stop.x, stop.y, stop.z) || isStone(stop.x, stop.y + 1, stop.z)
    }

    private fun pressDirection(keyStates: MutableMap<InputKey, Boolean>, relativeKeys: List<InputKey>, dir: Int) {
        if (dir == -1) return
        keyStates[relativeKeys[dir]] = true
    }

    private fun dirOffsetX(dir: Int): Int =
        when (dir) {
            1 -> -1
            3 -> 1
            else -> 0
        }

    private fun dirOffsetZ(dir: Int): Int =
        when (dir) {
            0 -> 1
            2 -> -1
            else -> 0
        }

    private fun isOnTurnBlock(path: List<BlockPos>, index: Int): Boolean {
        if (index <= 0 || index >= path.lastIndex) return false

        return calcDir(path[index - 1].to2d(), path[index].to2d()) !=
                calcDir(path[index].to2d(), path[index + 1].to2d())
    }

    private fun isSegmentBoundary(path: List<BlockPos>, index: Int): Boolean {
        if (index <= 0 || index >= path.lastIndex) return false

        return isOnTurnBlock(path, index) ||
                path[index - 1].y != path[index].y ||
                path[index].y != path[index + 1].y
    }

    private fun BlockPos.to2d(): Pair<Int, Int> = x to z

    private fun rotatedMovementKeys(dirOffset: Int): List<InputKey> {
        val keys = movementKeys.toMutableList()
        repeat(dirOffset) {
            keys.add(0, keys.removeLast())
        }
        return keys
    }

    private fun calcCorrection(
        x: Double,
        z: Double,
        motionX: Double,
        motionZ: Double,
        current: Pair<Int, Int>,
        dir: Int
    ): Int {
        val x1 = current.first
        val z1 = current.second

        return when {
            (motionZ > 0 || motionX == 0.0) && z > z1 + 0.55 && dir != 0 -> 2
            (motionX < 0 || motionZ == 0.0) && x < x1 + 0.45 && dir != 1 -> 3
            (motionZ < 0 || motionX == 0.0) && z < z1 + 0.45 && dir != 2 -> 0
            (motionX > 0 || motionZ == 0.0) && x > x1 + 0.55 && dir != 3 -> 1
            else -> -1
        }
    }

    private fun isStone(x: Int, y: Int, z: Int): Boolean {
        return mc.level?.getBlockState(BlockPos(x, y, z))?.`is`(Blocks.POLISHED_ANDESITE) == true
    }

    private fun isPhysicallyDown(keyMapping: KeyMapping): Boolean {
        val key = (keyMapping as KeyMappingAccessor).key
        if (key == InputConstants.UNKNOWN) return false

        return when (key.type) {
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(mc.window.handle(), key.value) == GLFW.GLFW_PRESS
            else -> InputConstants.isKeyDown(mc.window, key.value)
        }
    }
}
