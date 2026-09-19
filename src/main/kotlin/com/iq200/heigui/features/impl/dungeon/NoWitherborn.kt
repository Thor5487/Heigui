package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.itemId
import com.iq200.heigui.utils.loreString
import com.iq200.heigui.utils.noControlCodes
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import kotlin.math.abs

object NoWitherborn : Module(
    name = "No Witherborn",
    description = "Prevents Witherborn from spawning when wearing a full Wither armor set.",
    category = Category.DUNGEON
) {
    private const val ARMOR_CLICK_DELAY_TICKS = 1
    private const val HELMET_SLOT = 5

    private val witherArmorPrefixes = setOf("WITHER", "WISE_WITHER", "TANK_WITHER", "POWER_WITHER", "SPEED_WITHER")

    private var wasFullWitherborn = false
    private var pendingClickTicks = -1

    init {
        on<WorldEvent.Load> {
            reset()
        }

        on<TickEvent.Start> {
            if (!enabled) {
                reset()
                return@on
            }

            val player = mc.player ?: return@on
            val fullWitherborn = hasFullWitherbornBonus()

            if (!fullWitherborn) {
                wasFullWitherborn = false
                pendingClickTicks = -1
                return@on
            }

            if (!wasFullWitherborn) {
                wasFullWitherborn = true
                pendingClickTicks = ARMOR_CLICK_DELAY_TICKS
            }

            if (pendingClickTicks < 0) return@on
            if (mc.screen != null && mc.screen !is InventoryScreen) return@on

            if (pendingClickTicks > 0) {
                pendingClickTicks--
                return@on
            }

            if (isPlayerMoving()) return@on

            val openedInventory = mc.screen == null
            if (openedInventory) {
                mc.setScreen(InventoryScreen(player))
            }

            mc.gameMode?.handleContainerInput(
                player.inventoryMenu.containerId,
                HELMET_SLOT,
                2,
                ContainerInput.CLONE,
                player
            )

            if (openedInventory) {
                player.closeContainer()
            }

            pendingClickTicks = -1
        }
    }

    private fun reset() {
        wasFullWitherborn = false
        pendingClickTicks = -1
    }

    private fun hasFullWitherbornBonus(): Boolean {
        val helmet = mc.player?.getItemBySlot(EquipmentSlot.HEAD) ?: return false
        if (!helmet.isWitherArmorHelmet()) return false

        val cleanLore = helmet.loreString.map { it.noControlCodes }
        return cleanLore.withIndex().any { (index, line) ->
            line.contains("Witherborn", ignoreCase = true) &&
                    cleanLore.subList(index, (index + 4).coerceAtMost(cleanLore.size)).any { it.contains("4/4") }
        }
    }

    private fun isPlayerMoving(): Boolean {
        val motion = mc.player?.deltaMovement ?: return true
        return abs(motion.x) > 0.003 || abs(motion.z) > 0.003
    }

    private fun ItemStack.isWitherArmorHelmet(): Boolean {
        val id = itemId
        return witherArmorPrefixes.any { prefix -> id == "${prefix}_HELMET" }
    }
}
