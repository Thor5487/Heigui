package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.events.core.onReceive
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.sendCommand
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.phys.Vec3

object SpringBoots : Module(
    name = "Spring Boots",
    description = "Provides Spring Boots utilities for Floor 7 and Master Mode 7.",
    category = Category.FLOOR7
) {
    private val autoSpringBoots by BooleanSetting(
        "Auto Spring Boots",
        false,
        "Automatically equips Spring Boots when entering the F7 or M7 boss fight."
    )
    private val loadoutSlot by NumberSetting(
        "Loadout Slot",
        1,
        1,
        9,
        1,
        "The loadout to equip when entering the boss fight."
    ).withDependency { autoSpringBoots }
    private val clickDelay by NumberSetting(
        "Click Delay",
        2,
        1,
        20,
        1,
        "Delay between inventory actions.",
        "ticks"
    ).withDependency { autoSpringBoots }

    private val f7EntryPos = Vec3(73.5, 221.5, 14.5)
    private var swapState = SwapState.IDLE
    private var delayTicks = 0
    private var autoEquipTriggered = false

    init {
        onReceive<ClientboundPlayerPositionPacket> {
            val player = mc.player ?: return@onReceive
            val absolute = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(player),
                change(),
                relatives()
            )

            if (
                autoSpringBoots &&
                !autoEquipTriggered &&
                DungeonUtils.isFloor(7) &&
                absolute.position().distanceToSqr(f7EntryPos) <= ENTRY_POSITION_EPSILON_SQR
            ) {
                autoEquipTriggered = true
                mc.execute {
                    if (swapState == SwapState.IDLE) swapState = SwapState.OPEN_LOADOUT
                }
            }
        }

        on<TickEvent.End> {
            processAutoEquip()
        }

        on<WorldEvent.Load> {
            resetAutoEquip(resetTrigger = true)
        }
    }

    private fun processAutoEquip() {
        if (!autoSpringBoots) {
            if (swapState != SwapState.IDLE) {
                if (currentLoadoutScreen() != null) mc.player?.closeContainer()
                resetAutoEquip()
            }
            return
        }
        if (swapState == SwapState.IDLE) return
        val player = mc.player ?: return

        when (swapState) {
            SwapState.OPEN_LOADOUT -> {
                if (mc.gui.screen() != null) return
                sendCommand("loadout")
                swapState = SwapState.WAIT_SCREEN
            }

            SwapState.WAIT_SCREEN -> {
                if (mc.gui.screen() == null) return
                if (currentLoadoutScreen() == null) {
                    resetAutoEquip()
                    return
                }

                swapState = SwapState.WAIT_CLICK
                delayTicks = clickDelay
            }

            SwapState.WAIT_CLICK -> {
                val screen = currentLoadoutScreen() ?: run {
                    resetAutoEquip()
                    return
                }
                if (waitForClickDelay()) return

                val zeroBasedIndex = loadoutSlot - 1
                val row = zeroBasedIndex / LOADOUT_COLUMNS + 1
                val column = zeroBasedIndex % LOADOUT_COLUMNS
                val targetSlot = row * INVENTORY_ROW_SIZE + column + LOADOUT_COLUMN_OFFSET

                mc.gameMode?.handleContainerInput(
                    screen.menu.containerId,
                    targetSlot,
                    0,
                    ContainerInput.PICKUP,
                    player
                )
                swapState = SwapState.WAIT_CLOSE
                delayTicks = clickDelay
            }

            SwapState.WAIT_CLOSE -> {
                if (currentLoadoutScreen() == null) {
                    resetAutoEquip()
                    return
                }
                if (waitForClickDelay()) return

                player.closeContainer()
                resetAutoEquip()
            }

            else -> Unit
        }
    }

    private fun currentLoadoutScreen(): AbstractContainerScreen<*>? {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return null
        return screen.takeIf { it.title.string.contains("Loadouts") }
    }

    private fun waitForClickDelay(): Boolean {
        if (delayTicks <= 0) return false
        delayTicks--
        return true
    }

    private fun resetAutoEquip(resetTrigger: Boolean = false) {
        swapState = SwapState.IDLE
        delayTicks = 0
        if (resetTrigger) autoEquipTriggered = false
    }

    override fun onDisable() {
        if (currentLoadoutScreen() != null) mc.player?.closeContainer()
        resetAutoEquip(resetTrigger = true)
        super.onDisable()
    }

    private enum class SwapState {
        IDLE,
        OPEN_LOADOUT,
        WAIT_SCREEN,
        WAIT_CLICK,
        WAIT_CLOSE
    }

    private const val LOADOUT_COLUMNS = 3
    private const val INVENTORY_ROW_SIZE = 9
    private const val LOADOUT_COLUMN_OFFSET = 5
    private const val ENTRY_POSITION_EPSILON_SQR = 0.01
}
