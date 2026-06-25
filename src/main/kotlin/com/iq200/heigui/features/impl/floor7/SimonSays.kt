package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Clock
import com.iq200.heigui.events.*
import com.iq200.heigui.features.Category
import com.iq200.heigui.utils.Color.Companion.withAlpha
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.createSoundSettings
import com.iq200.heigui.utils.handlers.schedule
import com.iq200.heigui.utils.playSoundSettings
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.sendCommand
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.M7Phases
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult


object SimonSays : Module(
    name = "Simon Says",
    description = "features for simon says",
    category = Category.FLOOR7
) {
    // These are visible settings that will render under this module in the GUI

    private val announceProgress by BooleanSetting("Announce Progress", false, "announce ss progress")
    private val blockWrongClick by BooleanSetting("Block Wrong Click", false, "prevent clicking on wrong button, sneak to bypass")
    private val blockSound = createSoundSettings("Block Sound", "entity.blaze.hurt") { blockWrongClick }
    private val autoStart by BooleanSetting("Auto Start", false, "auto starts ss when s1 starts")
    private val triggerbot by BooleanSetting("Triggerbot", false, desc = "")
    private val triggerBotDelay by NumberSetting("Triggerbot Delay", 200L, 0, 500, unit = "ms", desc = "Delay for Each Click").withDependency { triggerbot }
    private val triggerBotClock = Clock(triggerBotDelay)
    private val firstClickClock = Clock(100)

    private val startButton = BlockPos(110, 121, 91)
    private val clickInOrder = ArrayList<BlockPos>()
    private var clickNeeded = 0
    private var firstPhase = true
    private var clickCntOnStartBtn = 0

    private fun resetSolution() {
        clickInOrder.clear()
        clickNeeded = 0
    }


    init {
        on<WorldEvent.Load> {
            resetSolution()
            firstPhase = true
            clickCntOnStartBtn = 0
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
                    if (updated.block == Blocks.SEA_LANTERN && old.block == Blocks.OBSIDIAN) {

                        if (pos != clickInOrder.getOrNull(clickInOrder.size - 1)) {
                            clickInOrder.add(pos.immutable())
                            if (!firstPhase) return@on
                            if (clickInOrder.size == 3) clickInOrder.removeAt(0)
                        }
                    }

                110 -> // 偵測按鈕被按下，更新進度
                    if (updated.block == Blocks.STONE_BUTTON && updated.getValue(BlockStateProperties.POWERED)) {
                        val indexOfButton = clickInOrder.indexOf(pos.east())
                        clickNeeded = indexOfButton + 1
                        if (announceProgress && clickInOrder.isNotEmpty()) {
                            if (pos.east() == clickInOrder[clickInOrder.size - 1]) sendCommand("pc SS ${clickInOrder.size}/5")
                        }
                        if (clickNeeded >= clickInOrder.size) {
                            resetSolution()
                            firstPhase = false
                        }
                    }

            }
        }

        on<RenderEvent.Extract> {
            if (!triggerbot|| !triggerBotClock.hasTimePassed(triggerBotDelay) || mc.screen != null) return@on

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

        on<PlayerInputEvent.Use> {
            if (result !is BlockHitResult) return@on

            if (!DungeonUtils.isFloor(7)) return@on

            val pos = result.blockPos

            if (pos == startButton) {
                clickCntOnStartBtn++

                if (clickCntOnStartBtn > 3) {
                    sendCommand("pc SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE SS BROKE")
                }

                return@on
            }

            val player = mc.player ?: return@on

            if (!blockWrongClick || player.isShiftKeyDown) return@on

            if (pos.y in 120..123 && pos.z in 92..95 && pos.x == 110) {
               val expectedPos = clickInOrder.getOrNull(clickNeeded)

                if (expectedPos == null || pos.east() != expectedPos) {
                    cancel()
                    playSoundSettings(blockSound())
                }
            }
        }

        on<RenderEvent.Extract> {
            if (DungeonUtils.getF7Phase() != M7Phases.P3) return@on

            for (index in clickNeeded until clickInOrder.size) {
                with(clickInOrder[index]) {
                    val color = when (index) {
                        clickNeeded -> Colors.MINECRAFT_GREEN.withAlpha(0.5f)
                        clickNeeded + 1 -> Colors.MINECRAFT_GOLD.withAlpha(0.5f)
                        else -> Colors.MINECRAFT_RED.withAlpha(0.5f)
                    }

                    drawStyledBox(AABB(x + 0.05, y + 0.37, z + 0.3, x - 0.15, y + 0.63, z + 0.7), color, 2, true)
                }
            }
        }

        on<ChatPacketEvent> {
            if (value != "[BOSS] Goldor: Who dares trespass into my domain?") return@on
            clickCntOnStartBtn = 0
            if (!autoStart) return@on

            val hitResult = mc.hitResult ?: return@on

            if (hitResult !is BlockHitResult) return@on
            val blockPos = hitResult.blockPos
            if (blockPos == startButton) {
                repeat(3) {
                    schedule(it * 3 + 1) {
                        PlayerUtils.rightClick()
                    }
                }
            }
        }

    }

}