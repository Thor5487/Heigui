package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Clock
import com.iq200.heigui.events.*
import com.iq200.heigui.features.Category
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.M7Phases
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties


object SSTriggerBot : Module(
    name = "SS TriggerBot",
    description = "Triggerbot for SS",
    category = Category.FLOOR7
) {
    // These are visible settings that will render under this module in the GUI
    private val toggled by BooleanSetting("Enabled", false, desc = "")
    private val triggerBotDelay by NumberSetting("Triggerbot Delay", 200L, 70, 500, unit = "ms", desc = "Delay for Each Click")

    private val triggerBotClock = Clock(triggerBotDelay)
    private val firstClickClock = Clock(100)

    private val startButton = BlockPos(110, 121, 91)
    private val clickInOrder = ArrayList<BlockPos>()
    private var clickNeeded = 0
    private var firstPhase = true

    private fun resetSolution() {
        clickInOrder.clear()
        clickNeeded = 0
    }


    init {
        on<WorldEvent.Load> {
            resetSolution()
            firstPhase = true
        }

        on<BlockUpdateEvent> {
            // 只在 F7 第三階段 (Terminals) 運作
            if (DungeonUtils.getF7Phase() != M7Phases.P3) return@on

            // 偵測開始按鈕被按下
            if (pos == startButton && updated.block == Blocks.STONE_BUTTON && updated.getValue(BlockStateProperties.POWERED)) {
                resetSolution()
                firstPhase = true
                return@on
            }

            // Simon Says 區域判定 (y: 120-123, z: 92-95)
            if (pos.y !in 120..123 || pos.z !in 92..95) return@on

            when (pos.x) {
                111 -> // 偵測海晶燈變回黑曜石 (代表該按鈕被閃過)
                    if (updated.block == Blocks.OBSIDIAN && old.block == Blocks.SEA_LANTERN && pos !in clickInOrder) {
                        clickInOrder.add(pos.immutable())
                        if (!firstPhase) return@on
                        // 處理第一階段特殊的順序反轉與跳過邏輯
                        when (clickInOrder.size) {
                            2 -> clickInOrder.reverse()
                            3 -> clickInOrder.removeAt(clickInOrder.lastIndex - 1)
                        }
                    }

                110 -> // 偵測按鈕被按下，更新進度
                    if (updated.block == Blocks.AIR) resetSolution()
                    else if (old.block == Blocks.STONE_BUTTON && updated.getValue(BlockStateProperties.POWERED)) {
                        clickNeeded = clickInOrder.indexOf(pos.east()) + 1
                        if (clickNeeded >= clickInOrder.size) {
                            resetSolution()
                            firstPhase = false
                        }
                    }
            }
        }

        on<RenderEvent.Extract> {
            if (!toggled|| !triggerBotClock.hasTimePassed(triggerBotDelay) || mc.screen != null) return@on

            // 如果還沒有解答，或者已經點完了，就跳出
            if (clickInOrder.isEmpty() || clickNeeded >= clickInOrder.size) return@on

            // 取得玩家目前準心看著的方塊
            val hitResult = mc.hitResult as? net.minecraft.world.phys.BlockHitResult ?: return@on
            val pos = hitResult.blockPos
            // 檢查：看著的方塊的「東邊一格」是不是等於解答需要的方塊？
            if (clickInOrder.getOrNull(clickNeeded) != pos.east()) return@on

            // 核心點擊邏輯
            if (clickNeeded == 0) {
                // 避免狂點第一個按鈕導致解謎壞掉
                if (!firstClickClock.hasTimePassed()) return@on
                firstClickClock.update()
                val player = mc.player ?: return@on
                mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
                mc.player?.swing(InteractionHand.MAIN_HAND)
                return@on
            }

            // 點擊後續的按鈕
            triggerBotClock.update()
            val player = mc.player ?: return@on
            mc.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND)
        }

    }

}