package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items

object InstaMid : Module(
    name = "InstaMid",
    description = "Instant Middle",
    category = Category.FLOOR7
) {
    private val delay by NumberSetting("Delay", 1, 0, 10, desc = "Delay to Trigger After Getting Pulled", unit = "tick")
    private val clazz by SelectorSetting("Class", "Berserk", listOf("Archer", "Berserk", "Healer", "Mage", "Tank"), desc = "class to leap")
    private val sneak by BooleanSetting("Sneak", false, desc = "Use Sneak or not to InstaMid")

    // 騎乘狀態追蹤
    private var isRiding = false
    private var rideTicks = 0

    // 🌟 單局鎖：確保每場地城只會觸發一次
    private var hasTriggeredThisRun = false

    // Sneak 控制
    private var isHoldingSneak = false
    private var sneakHoldTicks = 0
    private val SNEAK_DURATION = 2

    // Leap 等待控制
    private var isWaitingToLeap = false

    private val playerRegex = Regex("(?:\\[.+?] )?(?<name>\\w+)")

    init {
        on<TickEvent.Start> {
            if (!DungeonUtils.isFloor(7) || !DungeonUtils.inBoss) return@on

            val player = mc.player ?: return@on

            // ==========================================
            // 1. 放開與維持 Shift 的邏輯 (背景獨立倒數計時)
            // ==========================================
            if (isHoldingSneak) {
                sneakHoldTicks++
                if (sneakHoldTicks >= SNEAK_DURATION) {
                    mc.options.keyShift.isDown = false
                    isHoldingSneak = false
                    sneakHoldTicks = 0
                } else {
                    mc.options.keyShift.isDown = true
                }
            }

            // ==========================================
            // 2. Leap 耐心等待邏輯 (等待伺服器將頭顱放入介面)
            // ==========================================
            if (isWaitingToLeap) {
                val screen = mc.screen as? AbstractContainerScreen<*> ?: return@on
                val title = screen.title.string.lowercase().replace(Regex("§[0-9a-fk-or]"), "")

                if (!title.contains("spirit leap") && !title.contains("teleport to player")) return@on

                val menu = screen.menu
                var targetSlot = -1
                var headsFound = 0

                for (i in 0 until (menu.slots.size - 36)) {
                    val slot = menu.slots.getOrNull(i) ?: continue
                    val item = slot.item

                    if (item.isEmpty || !item.`is`(Items.PLAYER_HEAD)) continue
                    headsFound++

                    val hoverName = item.hoverName.string.replace(Regex("§[0-9a-fk-or]"), "")
                    val headName = playerRegex.find(hoverName)?.groups?.get("name")?.value ?: continue

                    val teammate = DungeonUtils.dungeonTeammates.find { it.name.equals(headName, ignoreCase = true) }

                    if (teammate != null && teammate.clazz.name.equals(clazz.toString(), ignoreCase = true)) {
                        if (!teammate.isDead) {
                            targetSlot = i
                            break
                        }
                    }
                }

                if (targetSlot != -1) {
                    mc.gameMode?.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player)
                    modMessage("§a[InstaMid] Auto Leaped to $clazz!")
                    isWaitingToLeap = false
                } else if (headsFound > 0) {
                    modMessage("§c[InstaMid] Target class ($clazz) not found or is dead!")
                    isWaitingToLeap = false
                }
            }

            // ==========================================
            // 3. 騎乘狀態偵測與觸發
            // ==========================================
            // 🌟 如果這局已經觸發過了，就不再進行騎乘計時
            if (hasTriggeredThisRun) return@on

            val currentlyRiding = player.isPassenger

            if (currentlyRiding && !isRiding) {
                isRiding = true
                rideTicks = 0
            } else if (!currentlyRiding) {
                isRiding = false
            }

            if (isRiding) {
                rideTicks++

                if (rideTicks == delay.toInt()) {
                    // 🌟 達到 delay 瞬間，立刻把鎖鎖上！
                    hasTriggeredThisRun = true

                    if (sneak) {
                        mc.options.keyShift.isDown = true
                        isHoldingSneak = true
                        sneakHoldTicks = 0
                        modMessage("§a[InstaMid] Sneaking to InstaMid!")
                    } else {
                        isWaitingToLeap = true
                        modMessage("§e[InstaMid] Waiting for Leap menu...")
                    }
                }
            }
        }

        on<WorldEvent.Load> {
            // 🌟 換世界時，解開單局鎖，一切重置
            isRiding = false
            rideTicks = 0
            isWaitingToLeap = false
            hasTriggeredThisRun = false

            if (isHoldingSneak) {
                mc.options.keyShift.isDown = false
                isHoldingSneak = false
                sneakHoldTicks = 0
            }
        }
    }

    override fun onDisable() {
        if (isHoldingSneak) {
            mc.options.keyShift.isDown = false
            isHoldingSneak = false
            sneakHoldTicks = 0
        }
        isWaitingToLeap = false
        super.onDisable()
    }
}