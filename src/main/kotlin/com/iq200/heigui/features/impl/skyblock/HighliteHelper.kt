package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
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
import com.iq200.mixin.accessors.KeyMappingAccessor
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult

object HighliteHelper : Module(
    name = "Highlite Helper",
    description = "Shoot Timegun until target mineral is reached, then mine",
    category = Category.SKYBLOCK
) {

    enum class BlockAction {
        SHOOT,
        MINE,
        IGNORE
    }

    val highlitesPerCycle by NumberSetting(
        "Highlites Per Cycle",
        2,
        1,
        16,
        1,
        desc = "Target Highlites per mining cycle"
    )
    val showProgress by BooleanSetting("Time Gun Progress", false, desc = "Show Time Gun Progress, better for gliding")
    private val progressHud by HUD("Cycle Progress", "Display Current Target and Progress") { example ->
        if (example) return@HUD textDim("§9Timite: §b32§7/64", 0, 0, Colors.WHITE)
        if (mc.player == null) return@HUD 0 to 0

        if (LocationUtils.currentArea != Island.Rift) return@HUD 0 to 0

        val youngiteCount = getInventoryItemCount("youngite")
        val timiteCount = getInventoryItemCount("timite")
        val obsoliteCount = getInventoryItemCount("obsolite")

        val targetAmount = highlitesPerCycle
        val reqY = targetAmount * 32
        val reqT = targetAmount * 32
        val reqO = targetAmount * 16

        val completedBatches = minOf(youngiteCount / reqY, timiteCount / reqT, obsoliteCount / reqO)
        val targetYoungite = (completedBatches + 1) * reqY
        val targetTimite = (completedBatches + 1) * reqT
        val targetObsolite = (completedBatches + 1) * reqO

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

        // ==========================================
        on<InputEvent> {
            if (LocationUtils.currentArea != Island.Rift) return@on

            if (mc.player == null) return@on

            if (mc.gui.screen() != null) return@on

            if (key.type == InputConstants.Type.MOUSE && key.value == InputConstants.MOUSE_BUTTON_LEFT) {
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

        // ==========================================
        on<TickEvent.Start> {
            if (LocationUtils.currentArea != Island.Rift) return@on

            if (!enabled || mc.player == null || !isPhysicalLMBDown) {
                return@on
            }


            val hit = mc.hitResult
            if (hit is BlockHitResult) {
                val lookingAtPos = hit.blockPos
                val currentBlockState = mc.level!!.getBlockState(lookingAtPos)


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
                            val maxTime = if (doubleTimeShooting) 1800.0 else 1800.0
                            val elapsed = now - shootStartTime


                            if (!doubleTimeShooting && !isHoldingGreen && (elapsed >= 1800.0 || (lastBlockState != null && lastBlockState!!.block != currentBlockState.block))) {

                                isHoldingGreen = true
                                greenHoldStartTime = now


                                doubleTimeShooting = true
                                shootStartTime = now
                            }


                            if (isHoldingGreen && (now - greenHoldStartTime >= 500L)) {
                                isHoldingGreen = false
                            }


                            if (doubleTimeShooting && !isHoldingGreen) {
                                if (elapsed >= maxTime || (lastBlockState != null && lastBlockState!!.block != currentBlockState.block)) {

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
            if (LocationUtils.currentArea != Island.Rift) return@on

            if (!showProgress) return@on
            val target = shootingTargetPos ?: return@on

            val now = System.currentTimeMillis()
            val aabb: AABB
            val boxColor: Color

            if (isHoldingGreen) {

                aabb = AABB(
                    target.x.toDouble(), target.y.toDouble(), target.z.toDouble(),
                    target.x + 1.0, target.y + 1.0, target.z + 1.0
                )
                boxColor = Color(50, 255, 100, 150)
            } else {

                val maxTime = if (doubleTimeShooting) 1800.0 else 1800.0
                val elapsed = now - shootStartTime
                val progress = (elapsed / maxTime).coerceIn(0.0, 1.0)

                aabb = AABB(
                    target.x.toDouble(),
                    target.y.toDouble(),
                    target.z.toDouble(),
                    target.x + 1.0,
                    target.y + progress * 1.0,
                    target.z + 1.0
                )

                boxColor = if (doubleTimeShooting) {
                    if (progress >= 1.0) Color(170, 0, 255, 150)
                    else Color(255, 100, 0, 150)
                } else {
                    if (progress >= 1.0) Color(50, 255, 100, 150)
                    else Color(255, 50, 50, 150)
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

    // ==========================================
    private fun getActionForBlock(pos: BlockPos, state: BlockState): BlockAction {
        val currentLevel = when {
            state.`is`(Blocks.STAINED_GLASS.lightBlue()) ||
                    state.`is`(Blocks.STAINED_GLASS_PANE.lightBlue()) -> 1

            state.`is`(Blocks.STAINED_GLASS.blue()) ||
                    state.`is`(Blocks.STAINED_GLASS_PANE.blue()) -> 2

            state.`is`(Blocks.STAINED_GLASS.purple()) ||
                    state.`is`(Blocks.STAINED_GLASS_PANE.purple()) -> 3

            else -> -1
        }

        if (currentLevel == -1) return BlockAction.IGNORE


        val youngiteCount = getInventoryItemCount("youngite")
        val timiteCount = getInventoryItemCount("timite")
        val obsoliteCount = getInventoryItemCount("obsolite")




        val targetAmount = highlitesPerCycle
        val reqY = targetAmount * 32
        val reqT = targetAmount * 32
        val reqO = targetAmount * 16


        val completedBatches = minOf(youngiteCount / reqY, timiteCount / reqT, obsoliteCount / reqO)


        val targetYoungite = (completedBatches + 1) * reqY
        val targetTimite = (completedBatches + 1) * reqT
        val targetObsolite = (completedBatches + 1) * reqO


        val targetLevel = when {
            youngiteCount < targetYoungite -> 1
            timiteCount < targetTimite -> 2
            obsoliteCount < targetObsolite -> 3
            else -> 1
        }

        return when {
            currentLevel < targetLevel -> BlockAction.SHOOT
            currentLevel == targetLevel -> BlockAction.MINE
            else -> BlockAction.MINE
        }
    }

    private fun resetHelper() {
        mc.options.keyUse.isDown = isPhysicallyDown(mc.options.keyUse)
        mc.options.keyAttack.isDown = isPhysicallyDown(mc.options.keyAttack)
        shootingTargetPos = null
        lastBlockState = null
        doubleTimeShooting = false
        isHoldingGreen = false
    }

    private fun isPhysicallyDown(keyMapping: KeyMapping): Boolean {
        val key = (keyMapping as KeyMappingAccessor).key
        if (key == InputConstants.UNKNOWN) return false

        return when (key.type) {
            InputConstants.Type.MOUSE -> when (key.value) {
                InputConstants.MOUSE_BUTTON_LEFT -> mc.mouseHandler.isLeftPressed
                InputConstants.MOUSE_BUTTON_RIGHT -> mc.mouseHandler.isRightPressed
                InputConstants.MOUSE_BUTTON_MIDDLE -> mc.mouseHandler.isMiddlePressed
                else -> false
            }
            else -> InputConstants.isKeyDown(key.value)
        }
    }

    private fun getInventoryItemCount(keyword: String): Int {
        val player = mc.player ?: return 0
        var count = 0

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
