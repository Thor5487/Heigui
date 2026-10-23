package com.iq200.heigui.features.impl.dungeon

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.features.impl.skyblock.Vampire
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.itemUUID
import com.iq200.heigui.utils.modMessage
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.world.InteractionHand

data class CustomACData(
    var items: MutableMap<String, String> = mutableMapOf(),
    var rightClickItems: MutableMap<String, String> = mutableMapOf()
)

enum class AutoClickButton {
    LEFT,
    RIGHT
}

object AutoClick : Module (
    name = "AutoClick",
    description = "autoclick",
    category = Category.DUNGEON
) {
    private val customAC by BooleanSetting("Custom Item", false, desc = "Custom Item for Auto Click, /hg cac to view complete commands")
    private val mac by BooleanSetting("MAC", false, "Mage Autoclick")
    private val cps by NumberSetting("CPS", 10, 1, 20, 1, "Clicks per second")
    private val delay by NumberSetting("Hold Delay", 200, 50, 500, 50, unit = "ms", desc = "Delay to Start Autoclick")

    private data class ClickState(
        var isHolding: Boolean = false,
        var pressStartTime: Long = 0L,
        var lastClickTime: Long = 0L
    )

    private val leftClickState = ClickState()
    private val rightClickState = ClickState()

    private val config = JsonConfig(
        fileName = "customac.json",
        typeToken = object : TypeToken<CustomACData>() {}.type,
        defaultData = { CustomACData() }
    )

    init {
        config.load()

        on<WorldEvent.Load> {
            resetClickStates()
        }

        on<InputEvent> {
            if (!enabled || mc.player == null) return@on


            val attackKey = (mc.options.keyAttack as KeyMappingAccessor).key
            val useKey = (mc.options.keyUse as KeyMappingAccessor).key

            when (key) {
                attackKey -> updateClickState(leftClickState)
                useKey -> updateClickState(rightClickState)
            }
        }

        on<TickEvent.Start> {
            if (!enabled || mc.player == null || mc.level == null) {
                resetClickStates()
                return@on
            }

            if (mc.gui.screen() != null) return@on

            if (Vampire.enabled && Vampire.isHandlingKillerSpring) {
                return@on
            }

            val now = System.currentTimeMillis()
            handleAutoClick(AutoClickButton.LEFT, leftClickState, now)
            handleAutoClick(AutoClickButton.RIGHT, rightClickState, now)
        }
    }

    private fun InputEvent.updateClickState(state: ClickState) {
        if (isPress) {
            state.isHolding = true
            val now = System.currentTimeMillis()
            state.pressStartTime = now
            state.lastClickTime = now
        } else if (isRelease) {
            state.isHolding = false
        }
    }

    private fun handleAutoClick(button: AutoClickButton, state: ClickState, now: Long) {
        if (!state.isHolding || now - state.pressStartTime < delay) return

        val cpsDelay = 1000L / cps
        if (now - state.lastClickTime < cpsDelay || !checkItem(button)) return

        when (button) {
            AutoClickButton.LEFT -> {
                mc.missTime = 0
                PlayerUtils.leftClick()
                mc.options.keyAttack.isDown = false
            }

            AutoClickButton.RIGHT -> {
                PlayerUtils.rightClick()
                mc.options.keyUse.isDown = false
            }
        }
        state.lastClickTime = now
    }

    private fun resetClickStates() {
        leftClickState.isHolding = false
        rightClickState.isHolding = false
    }

    private fun checkItem(button: AutoClickButton): Boolean {
        val player = mc.player ?: return false
        val item = player.getItemInHand(InteractionHand.MAIN_HAND)
        val itemName = item.hoverName.string.lowercase()

        if (button == AutoClickButton.LEFT && mac && (itemName.contains("hyperion") || itemName.contains("claymore") || itemName.contains("cleaver"))) {
            return true
        }

        if (customAC) {
            val uuid = item.itemUUID
            if (itemsFor(config.data, button).containsKey(uuid)) {
                return true
            }
        }

        return false
    }

    private fun itemsFor(data: CustomACData, button: AutoClickButton): MutableMap<String, String> =
        when (button) {
            AutoClickButton.LEFT -> data.items
            AutoClickButton.RIGHT -> data.rightClickItems
        }




    fun help() {
        modMessage("§aCustom AutoClick Commands:")
        modMessage("§e/hg cac <lc|rc> add §8- §7Add the held item to the selected click list.", prefix = "")
        modMessage("§e/hg cac <lc|rc> remove [number] §8- §7Remove the held or numbered item.", prefix = "")
        modMessage("§e/hg cac <lc|rc> list §8- §7Show the selected click list.", prefix = "")
        modMessage("§e/hg cac help §8- §7Show this help message.", prefix = "")
    }

    fun addCurrentItem(button: AutoClickButton) {
        val player = mc.player ?: return
        val item = player.getItemInHand(InteractionHand.MAIN_HAND)
        val uuid = item.itemUUID
        val itemName = item.hoverName.string
        val clickName = button.displayName

        config.update { data ->
            val items = itemsFor(data, button)
            if (items.putIfAbsent(uuid, itemName) == null) {
                modMessage("§aSuccessfully added §e'$itemName §8(§7$uuid§8)§e' §ato the $clickName list!")
            } else {
                modMessage("§c'$itemName §8(§7$uuid§8)§c' is already in the $clickName list.")
            }
        }
    }

    fun removeCurrentItem(button: AutoClickButton) {
        val player = mc.player ?: return
        val item = player.getItemInHand(InteractionHand.MAIN_HAND)
        val uuid = item.itemUUID
        val itemName = item.hoverName.string
        val clickName = button.displayName

        config.update { data ->
            val removed = itemsFor(data, button).remove(uuid)
            if (removed != null) {
                modMessage("§aSuccessfully removed §e'$itemName §8(§7$uuid§8)§e' §afrom the $clickName list!")
            } else {
                modMessage("§c'$itemName §8(§7$uuid§8)§c' was not found in the $clickName list.")
            }
        }
    }

    fun removeItem(button: AutoClickButton, index: Int) {
        config.update { data ->
            val items = itemsFor(data, button)
            if (index !in 1..items.size) {
                modMessage("§cInvalid item number '$index'. Use §e/hg cac ${button.commandName} list §cto view valid numbers.")
                return@update
            }

            val (uuid, itemName) = items.entries.elementAt(index - 1)
            items.remove(uuid)
            modMessage("§aSuccessfully removed §e#$index '$itemName §8(§7$uuid§8)§e' §afrom the ${button.displayName} list!")
        }
    }

    fun listItems(button: AutoClickButton) {
        val items = itemsFor(config.data, button)
        if (items.isEmpty()) {
            modMessage("§eThe ${button.displayName} list is currently empty.")
        } else {
            modMessage("§aCustom AutoClick ${button.displayName} List:")
            items.entries.forEachIndexed { index, (uuid, name) ->
                modMessage("§e${index + 1}. §7$name §8(§7$uuid§8)", prefix = "")
            }
        }
    }

    private val AutoClickButton.displayName: String
        get() = when (this) {
            AutoClickButton.LEFT -> "left-click"
            AutoClickButton.RIGHT -> "right-click"
        }

    private val AutoClickButton.commandName: String
        get() = when (this) {
            AutoClickButton.LEFT -> "lc"
            AutoClickButton.RIGHT -> "rc"
        }
}
