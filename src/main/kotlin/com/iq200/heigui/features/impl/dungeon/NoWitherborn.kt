package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.itemId
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
        val player = mc.player ?: return false
        val armorFamilies = listOf(
            player.getItemBySlot(EquipmentSlot.HEAD).witherArmorFamily("HELMET"),
            player.getItemBySlot(EquipmentSlot.CHEST).witherArmorFamily("CHESTPLATE"),
            player.getItemBySlot(EquipmentSlot.LEGS).witherArmorFamily("LEGGINGS"),
            player.getItemBySlot(EquipmentSlot.FEET).witherArmorFamily("BOOTS")
        )

        return armorFamilies.all { it != null } && armorFamilies.distinct().size == 1
    }

    private fun isPlayerMoving(): Boolean {
        val motion = mc.player?.deltaMovement ?: return true
        return abs(motion.x) > 0.003 || abs(motion.z) > 0.003
    }

    private fun ItemStack.witherArmorFamily(piece: String): String? {
        val id = itemId
        return witherArmorPrefixes.firstOrNull { prefix -> id == "${prefix}_$piece" }
    }
}
