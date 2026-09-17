package com.iq200.heigui.features.impl.dungeon.icefill

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

object IceFill : Module(
    name = "IceFill",
    description = "Solves Ice Fill and walks the path.",
    category = Category.DUNGEON
) {
    private val renderSolution by BooleanSetting("Render Solution", true, desc = "Render the Ice Fill solution path.")
    private val autoWalk by BooleanSetting("Auto Walk", false, desc = "Automatically walk the Ice Fill solution path.")
    private val turnWaitTicks by NumberSetting("Turn Wait Ticks", 0, 0, 5, 1, "How many ticks to wait at sprint-related turns before entering the next segment.").withDependency { autoWalk }
    private val sprintBlocks by NumberSetting("Sprint Blocks", 4, 2, 8, 1, "Minimum number of blocks in a segment required to sprint. The segment still needs a polished andesite stop.").withDependency { autoWalk }
    private val sneakUntilStopBlocks by NumberSetting("Sneak Until Stop Blocks", 0, 0, 8, 1, "On sprint segments, sneak while farther than this many blocks from the polished andesite stop. 0 disables this.").withDependency { autoWalk }
    private val disableSprintWhileNotSneaking by BooleanSetting("Disable Sprint", false, desc = "Prevent sprinting when Ice Fill is moving without sneak.").withDependency { autoWalk }

    private var path: List<BlockPos> = emptyList()
    private var walking = false
    private var lastSolveAttempt = 0L

    fun shouldSuppressSprintKey(): Boolean = walking && IceFillWalker.shouldSuppressSprintKey()

    init {
        on<WorldEvent.Load> {
            reset()
        }

        on<TickEvent.End> {
            if (!shouldRun()) {
                reset()
                return@on
            }

            val now = System.currentTimeMillis()
            if (path.isEmpty() && now - lastSolveAttempt >= 250L) {
                lastSolveAttempt = now
                path = IceFillSolver.solve().orEmpty()
            }

            if (!autoWalk || path.isEmpty()) {
                if (walking) {
                    walking = false
                    IceFillWalker.reset()
                }
                return@on
            }

            if (mc.screen != null) {
                if (walking) {
                    walking = false
                    IceFillWalker.reset()
                }
                return@on
            }

            if (IceFillWalker.isManualInputDown()) {
                if (walking) {
                    walking = false
                    IceFillWalker.reset()
                }
                return@on
            }

            if (!walking && isStandingOnIce()) walking = true

            if (walking && IceFillWalker.walk(path, turnWaitTicks, sprintBlocks, sneakUntilStopBlocks, disableSprintWhileNotSneaking)) {
                walking = false
                IceFillWalker.reset()
            }
        }

        on<RenderEvent.Extract> {
            if (!renderSolution || path.isEmpty()) return@on
            IceFillRenderer.render(this, path)
        }
    }

    private fun shouldRun(): Boolean {
        return DungeonUtils.inClear && ScanUtils.currentRoom?.data?.name == "Ice Fill"
    }

    private fun reset() {
        path = emptyList()
        if (walking) IceFillWalker.reset()
        walking = false
        lastSolveAttempt = 0L
    }

    private fun isStandingOnIce(): Boolean {
        val player = mc.player ?: return false
        val state = mc.level?.getBlockState(player.blockPosition().below()) ?: return false
        return state.`is`(Blocks.ICE) || state.`is`(Blocks.PACKED_ICE)
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }
}
