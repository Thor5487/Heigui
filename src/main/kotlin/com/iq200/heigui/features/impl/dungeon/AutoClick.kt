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
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.itemUUID
import com.iq200.heigui.utils.modMessage
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.world.InteractionHand

data class CustomACData(
    var items: MutableMap<String, String> = mutableMapOf()
)

object AutoClick : Module (
    name = "AutoClick",
    description = "autoclick",
    category = Category.DUNGEON
) {
    private val customAC by BooleanSetting("Custom Item", false, desc = "Custom Item for Auto Click, /hg cac to view complete commands")
    private val mac by BooleanSetting("MAC", false, "Mage Autoclick")
    private val cps by NumberSetting("CPS", 10, 1, 20, 1, "Clicks per second")
    private val delay by NumberSetting("Hold Delay", 200, 50, 500, 50, unit = "ms", desc = "Delay to Start Autoclick")

    private var isHolding = false
    private var pressStartTime = 0L
    private var lastClickTime = 0L

    private val config = JsonConfig(
        fileName = "customac.json",
        typeToken = object : TypeToken<CustomACData>() {}.type,
        defaultData = { CustomACData() }
    )

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
                            mc.missTime = 0
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
        val item = player.getItemInHand(InteractionHand.MAIN_HAND)
        val itemName = item.hoverName.string.lowercase()

        if (mac && (itemName.contains("hyperion") || itemName.contains("claymore") || itemName.contains("cleaver"))) {
            return true
        }

        if (customAC) {
            val uuid = item.itemUUID
            if (uuid != null && config.data.items.containsKey(uuid)) {
                return true
            }
        }

        return false
    }




    fun addCurrentItem() {
        val player = mc.player ?: return
        val item = player.getItemInHand(InteractionHand.MAIN_HAND)
        val uuid = item.itemUUID
        val itemName = item.hoverName.string

        if (uuid == null) {
            return modMessage("§cCurrent held item does not have a valid Skyblock UUID or ID!")
        }

        config.update { data ->
            if (!data.items.containsKey(uuid)) {
                data.items[uuid] = itemName // 將 UUID 與物品名稱存入 Map
                modMessage("§aSuccessfully added §e'$itemName §8(§7$uuid§8)§e' §ato the custom AC list!")
            } else {
                modMessage("§c'$itemName §8(§7$uuid§8)§c' is already in the custom AC list.")
            }
        }
    }


    fun removeCurrentItem() {
        val player = mc.player ?: return
        val item = player.getItemInHand(InteractionHand.MAIN_HAND)
        val uuid = item.itemUUID
        val itemName = item.hoverName.string

        if (uuid == null) {
            return modMessage("§cCurrent held item does not have a valid Skyblock UUID or ID!")
        }

        config.update { data ->
            if (data.items.containsKey(uuid)) {
                data.items.remove(uuid) // 從 Map 中移除
                modMessage("§aSuccessfully removed §e'$itemName §8(§7$uuid§8)§e' §afrom the custom AC list!")
            } else {
                modMessage("§c'$itemName §8(§7$uuid§8)§c' was not found in the custom AC list.")
            }
            data
        }
    }

    fun listItems() {
        val items = config.data.items
        if (items.isEmpty()) {
            modMessage("§eCustom AC list is currently empty.")
        } else {
            modMessage("§aAutoClick Custom AC List:")
            items.forEach { (uuid, name) ->
                modMessage("§8- §7$name §8(§7$uuid§8)", prefix = "")
            }
        }
    }
}