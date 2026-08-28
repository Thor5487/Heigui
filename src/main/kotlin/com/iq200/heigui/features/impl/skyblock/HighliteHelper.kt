package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.render.textDim
import com.iq200.heigui.utils.skyblock.Island
import com.iq200.heigui.utils.skyblock.LocationUtils
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import org.lwjgl.glfw.GLFW

object HighliteHelper : Module(
    name = "Highlite Helper",
    description = "Shoot Timegun until target mineral is reached, then mine",
    category = Category.SKYBLOCK
) {

    enum class BlockAction {
        SHOOT,  // 還沒進化完：需要拿 Timegun 射
        MINE,   // 已經是目標了：可以直接挖
        IGNORE  // 不是目標礦物：直接無視
    }

    val showProgress by BooleanSetting("Time Gun Progress", false, desc = "Show Time Gun Progress, better for gliding")
    private val progressHud by HUD("Cycle Progress", "Display Current Target and Progress") { example ->
        if (example) return@HUD textDim("§9Timite: §b32§7/64", 0, 0, Colors.WHITE)
        if (mc.player == null) return@HUD 0 to 0

        if (LocationUtils.currentArea != Island.Rift) return@HUD 0 to 0

        val youngiteCount = getInventoryItemCount("youngite")
        val timiteCount = getInventoryItemCount("timite")
        val obsoliteCount = getInventoryItemCount("obsolite")

        // 邏輯跟上面一模一樣，用來計算當前的目標值
        val completedBatches = minOf(youngiteCount / 64, timiteCount / 64, obsoliteCount / 32)
        val targetYoungite = (completedBatches + 1) * 64
        val targetTimite = (completedBatches + 1) * 64
        val targetObsolite = (completedBatches + 1) * 32

        val text = when {
            youngiteCount < targetYoungite -> "§3Youngite: §b$youngiteCount§7/$targetYoungite"
            timiteCount < targetTimite -> "§9Timite: §b$timiteCount§7/$targetTimite"
            obsoliteCount < targetObsolite -> "§5Obsolite: §b$obsoliteCount§7/$targetObsolite"
            else -> "§aCalculating..."
        }

        textDim(text, 0, 0, com.iq200.heigui.utils.Colors.WHITE)
    }

    private var isPhysicalLMBDown = false

    private var shootingTargetPos: BlockPos? = null
    private var shootStartTime: Long = 0L

    private var lastBlockState: BlockState? = null
    private var doubleTimeShooting = false

    private var greenHoldStartTime: Long = 0L
    private var isHoldingGreen = false


    init {
        // ==========================================
        // 1. InputEvent：開關接管
        // ==========================================
        on<InputEvent> {
            if (mc.player == null) return@on

            if (mc.screen != null) return@on

            if (key.type == InputConstants.Type.MOUSE && key.value == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (isPress) {
                    isPhysicalLMBDown = true
                }
                else if (isRelease) {
                    if (isPhysicalLMBDown) {
                        isPhysicalLMBDown = false
                        resetHelper()
                    }
                }
            }
        }

        // ==========================================
        // 2. TickEvent：狀態機大腦 (所見即所得)
        // ==========================================
        on<TickEvent.Start> {
            if (!enabled || mc.player == null || !isPhysicalLMBDown) {
                return@on
            }


            val hit = mc.hitResult
            if (hit is BlockHitResult) {
                val lookingAtPos = hit.blockPos
                val currentBlockState = mc.level!!.getBlockState(lookingAtPos)

                // 詢問大腦這顆方塊該怎麼處理
                val action = getActionForBlock(lookingAtPos, currentBlockState)

                when (action) {
                    BlockAction.SHOOT -> {
                        val timegunSlot = PlayerUtils.findItemInHotbar("time gun")
                        if (timegunSlot != null && mc.player!!.inventory.selectedSlot != timegunSlot) {
                            PlayerUtils.setHotbarSlot(timegunSlot)
                        }
                        mc.options.keyAttack.isDown = false
                        mc.options.keyUse.isDown = true

                        val now = System.currentTimeMillis()

                        if (shootingTargetPos == lookingAtPos) {
                            val maxTime = if (doubleTimeShooting) 1800.0 else 2000.0
                            val elapsed = now - shootStartTime

                            // 1. 檢查第一階段是否達成（時間到了，或者伺服器已經提前回傳方塊變色）
                            if (!doubleTimeShooting && !isHoldingGreen && (elapsed >= 2000.0 || (lastBlockState != null && lastBlockState != currentBlockState))) {
                                // 🌟 啟動 0.5 秒的「綠色視覺滯留提醒」
                                isHoldingGreen = true
                                greenHoldStartTime = now

                                // 🌟 關鍵：橘色進度「立刻」在背景起跑，不需等 0.5 秒結束！
                                doubleTimeShooting = true
                                shootStartTime = now
                            }

                            // 2. 如果正在綠色滯留期內，檢查 0.5 秒是否過期（只用來關閉綠色顯示）
                            if (isHoldingGreen && (now - greenHoldStartTime >= 500L)) {
                                isHoldingGreen = false
                            }

                            // 3. 第二階段（橘色充能）的完成判定與循環
                            if (doubleTimeShooting && !isHoldingGreen) {
                                if (elapsed >= maxTime || (lastBlockState != null && lastBlockState != currentBlockState)) {
                                    // 達成紫色或進入下一個循環，重置計時
                                    shootStartTime = now
                                }
                            }
                        } else {
                            shootingTargetPos = lookingAtPos
                            doubleTimeShooting = false
                            isHoldingGreen = false
                            shootStartTime = now
                            lastBlockState = currentBlockState
                        }
                        lastBlockState = currentBlockState
                    }
                    BlockAction.MINE -> {
                        shootingTargetPos = null
                        val pickaxeSlot = PlayerUtils.findItemInHotbar("chrono pickaxe")
                        if (pickaxeSlot != null && mc.player!!.inventory.selectedSlot != pickaxeSlot) {
                            PlayerUtils.setHotbarSlot(pickaxeSlot)
                        }

                        mc.options.keyUse.isDown = false
                        mc.options.keyAttack.isDown = true
                    }
                    BlockAction.IGNORE -> {
                        shootingTargetPos = null
                        mc.options.keyAttack.isDown = true
                    }
                }
            } else {
                shootingTargetPos = null
                mc.options.keyUse.isDown = false
                mc.options.keyAttack.isDown = true
            }

        }


        on<RenderEvent.Extract> {
            if (!showProgress) return@on
            val target = shootingTargetPos ?: return@on

            val now = System.currentTimeMillis()
            val aabb: AABB
            val boxColor: Color

            if (isHoldingGreen) {
                // 🌟 滯留期：強制畫出「滿格的綠色」維持 0.5 秒
                aabb = AABB(
                    target.x - 0.01, target.y.toDouble(), target.z - 0.01,
                    target.x + 1.01, target.y + 1.0, target.z + 1.01
                )
                boxColor = Color(50, 255, 100, 150) // 綠色
            } else {
                // 一般計算進度
                val maxTime = if (doubleTimeShooting) 1800.0 else 2000.0
                val elapsed = now - shootStartTime
                val progress = (elapsed / maxTime).coerceIn(0.0, 1.0)

                aabb = AABB(
                    target.x - 0.01,
                    target.y.toDouble(),
                    target.z - 0.01,
                    target.x + 1.01,
                    target.y + progress * 1.0,
                    target.z + 1.01
                )

                boxColor = if (doubleTimeShooting) {
                    if (progress >= 1.0) Color(170, 0, 255, 150) // 紫色 (1.8s 滿)
                    else Color(255, 100, 0, 150) // 橘紅色 (第二階段充能)
                } else {
                    if (progress >= 1.0) Color(50, 255, 100, 150) // 綠色
                    else Color(255, 50, 50, 150) // 紅色 (第一階段充能)
                }
            }

            drawStyledBox(
                aabb = aabb,
                color = boxColor,
                style = 2,
                depth = true
            )
        }
    }

    // ==========================================
    // 🧠 預留區：未來的判斷機制全寫在這裡
    // ==========================================
    private fun getActionForBlock(pos: BlockPos, state: BlockState): BlockAction {
        val currentLevel = when {
            state.`is`(Blocks.LIGHT_BLUE_STAINED_GLASS) ||
                    state.`is`(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE) -> 1

            state.`is`(Blocks.BLUE_STAINED_GLASS) ||
                    state.`is`(Blocks.BLUE_STAINED_GLASS_PANE) -> 2

            state.`is`(Blocks.PURPLE_STAINED_GLASS) ||
                    state.`is`(Blocks.PURPLE_STAINED_GLASS_PANE) -> 3

            else -> -1
        }

        if (currentLevel == -1) return BlockAction.IGNORE

        // 1. 取得背包目前數量
        val youngiteCount = getInventoryItemCount("youngite")
        val timiteCount = getInventoryItemCount("timite")
        val obsoliteCount = getInventoryItemCount("obsolite")

        // 2. 🌟 核心算法：計算已經「完美湊齊」了幾組
        // 例如：Y=130(2組), T=65(1組), O=10(0組) -> 最小值是 0，代表第 1 組還沒湊齊
        // 例如：Y=130(2組), T=128(2組), O=64(2組) -> 最小值是 2，準備開始湊第 3 組
        val completedBatches = minOf(youngiteCount / 64, timiteCount / 64, obsoliteCount / 32)

        // 3. 計算當前循環的目標數量
        val targetYoungite = (completedBatches + 1) * 64
        val targetTimite = (completedBatches + 1) * 64
        val targetObsolite = (completedBatches + 1) * 32

        // 4. 判斷現在該挖哪一種
        val targetLevel = when {
            youngiteCount < targetYoungite -> 1
            timiteCount < targetTimite -> 2
            obsoliteCount < targetObsolite -> 3
            else -> 1 // 理論上不會走到這裡，因為一旦三個都達標，completedBatches 就會自動 +1
        }

        return when {
            currentLevel < targetLevel -> BlockAction.SHOOT
            currentLevel == targetLevel -> BlockAction.MINE
            else -> BlockAction.MINE
        }
    }

    private fun resetHelper() {
        mc.options.keyUse.isDown = false
        mc.options.keyAttack.isDown = false
        shootingTargetPos = null
        lastBlockState = null
        doubleTimeShooting = false
        isHoldingGreen = false
    }

    private fun getInventoryItemCount(keyword: String): Int {
        val player = mc.player ?: return 0
        var count = 0
        // 遍歷玩家的所有背包格子
        for (i in 0 until player.inventory.containerSize) {
            val itemStack = player.inventory.getItem(i)
            if (!itemStack.isEmpty) {
                val itemName = itemStack.hoverName.string.noControlCodes.lowercase()
                if (itemName.contains(keyword.lowercase())) {
                    count += itemStack.count
                }
            }
        }
        return count
    }

    override fun onDisable() {
        resetHelper()
        isPhysicalLMBDown = false
        super.onDisable()
    }
}