package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.itemId
import com.iq200.heigui.utils.sendCommand
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.inventory.ContainerInput

object AutoCrit : Module (
    name = "Auto Crit",
    description = "Automatically proceed crit tech, requires sulphur in your sacks, Sulphur Bow and Death Bow. Left click sulphur bow to run. Only works twice for each instance.",
    category = Category.FLOOR7
) {
    private val swordSwap by BooleanSetting("Sword Swap", false, desc = "Swap to the sword for multipliers.")
    private val swordSlot by NumberSetting("Sword Slot", 1.0, 1.0, 8.0, 1, "The hotbar slot to swap to for sword.").withDependency { swordSwap }
    private val armorSwap by BooleanSetting("Armor Swap", false, desc = "Swap armor in the wardrobe.")
    private val slotIndex by NumberSetting("Wardrobe Slot", 1.0, 1.0, 9.0, 1, "The armor to swap to in wardrobe.").withDependency { armorSwap }

    private enum class State {
        IDLE,
        SHOOT,
        SWAP_SWORD,
        OPEN_WORDRABE,
        SWAP_ARMOR
    }

    private var state = State.IDLE
    private var wait = true
    private var chargeTicks = 0
    private var chargingConfirmed = false

    init {
        on<WorldEvent.Load> {
            reset()
        }

        on<InputEvent> {
            if (state != State.IDLE) return@on
            if (mc.screen != null) return@on

            if (key.value != InputConstants.MOUSE_BUTTON_LEFT || !isHoldingDeathBow()) return@on

            state = State.SHOOT
            chargeTicks = 0
            chargingConfirmed = false
            KeyMapping.set(mc.options.keyUse.defaultKey, true)
            cancel()
        }

        on<PacketEvent.Send> {
            if (state != State.SHOOT) return@on
            if (packet !is ServerboundUseItemPacket) return@on
            if (!isHoldingDeathBow()) return@on

            chargeTicks = 0
            chargingConfirmed = true
        }

        on<TickEvent.Server> {
            val player = mc.player ?: return@on

            when (state) {
                State.SHOOT -> {
                    if (!chargingConfirmed) return@on
                    if (!isHoldingDeathBow() || !player.isUsingItem) {
                        reset()
                        return@on
                    }

                    chargeTicks++
                    if (chargeTicks < 20) return@on

                    KeyMapping.set(mc.options.keyUse.defaultKey, false)

                    state = State.SWAP_SWORD
                }

                State.SWAP_SWORD -> {
                    if (wait) {
                        wait = false
                        return@on
                    }

                    if (!swordSwap) {
                        state = State.OPEN_WORDRABE
                        return@on
                    }

                    player.inventory.selectedSlot = (swordSlot - 1).toInt()

                    state = State.OPEN_WORDRABE
                }

                State.OPEN_WORDRABE -> {
                    sendCommand("loadout")
                    state = State.SWAP_ARMOR
                }

                State.SWAP_ARMOR -> {
                    if (!armorSwap) {
                        reset()
                        return@on
                    }

                    val screen = mc.screen as? AbstractContainerScreen<*> ?: return@on
                    if (!screen.title.string.contains("Loadouts")) return@on

                    val zeroBasedIndex = slotIndex - 1

                    val row = zeroBasedIndex / 3 + 1
                    val col = zeroBasedIndex % 3

                    val targetSlot = (row * 9) + col + 5

                    mc.gameMode?.handleContainerInput(screen.menu.containerId, targetSlot.toInt(), 0,
                        ContainerInput.PICKUP, player)
                    reset()
                }

                else -> Unit
            }
        }
    }

    private fun reset() {
        state = State.IDLE
        KeyMapping.set(mc.options.keyUse.defaultKey, false)
        wait = true
        chargeTicks = 0
        chargingConfirmed = false
    }

    private fun isHoldingDeathBow() : Boolean {
        val player = mc.player ?: return false

        val itemName = player.mainHandItem.hoverName.string.lowercase()

        return itemName.contains("death bow")
    }
}
