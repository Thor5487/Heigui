package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.world.InteractionHand

object AutoClick : Module (
    name = "AutoClick",
    description = "autoclick",
    category = Category.DUNGEON
) {
    private val mac by BooleanSetting("MAC", false, "Mage Autoclick")
    private val cps by NumberSetting("CPS", 10, 1, 20, 1, "Clicks per second")
    private val delay by NumberSetting("Hold Delay", 200, 50, 500, 50, unit = "ms", desc = "Delay to Start Autoclick")

    private var isHolding = false
    private var pressStartTime = 0L
    private var lastClickTime = 0L

    init {
        on<WorldEvent.Load> {
            isHolding = false
        }

        on<InputEvent> {
            if (!enabled || mc.player == null) return@on


            val attackKey = (mc.options.keyAttack as KeyMappingAccessor).key

            if (key == attackKey) {
                if (isPress) {
                    isHolding = true
                    val now = System.currentTimeMillis()
                    pressStartTime = now
                    lastClickTime = now
                }
                else if (isRelease) {
                    isHolding = false
                }

            }
        }

        on<TickEvent.Start> {
            if (!enabled || mc.player == null || mc.level == null) {
                isHolding = false
                return@on
            }

            if (mc.screen != null) return@on

            if (isHolding) {
                val now = System.currentTimeMillis()

                if (now - pressStartTime >= delay) {
                    val cpsDelay = 1000L / cps
                    if (now - lastClickTime >= cpsDelay) {
                        if (checkItem()) {
                            PlayerUtils.leftClick()
                            lastClickTime = now
                        }
                    }
                }
            }
        }
    }

    private fun checkItem() : Boolean {
        val player = mc.player ?: return false
        val itemName = player.getItemInHand(InteractionHand.MAIN_HAND).hoverName.string.lowercase()

        if (mac && (itemName.contains("hyperion") || itemName.contains("claymore") || itemName.contains("cleaver"))) {
            return true
        }

        return false
    }


}