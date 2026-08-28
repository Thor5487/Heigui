package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.events.LevelEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.dungeon.DungeonClass
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items


object InstaMid : Module(
    name = "InstaMid",
    description = "Instant Middle",
    category = Category.FLOOR7
) {
    private val delay by NumberSetting("Delay", 1, 0 .. 10, desc = "Delay to Trigger After Getting Pulled", unit = "tick")
    private val clazz by SelectorSetting("Class", DungeonClass.BERSERK, desc = "class to leap")

    private val currentClassName: String
        get() = clazz.name

    // 騎乘狀態追蹤
    private var isRiding = false
    private var rideTicks = 0

    // 🌟 單局鎖：確保每場地城只會觸發一次
    private var hasTriggeredThisRun = false

    private val playerRegex = Regex("(?:\\[.+?] )?(?<name>\\w+)")

    init {
        on<TickEvent.Start> {
            if (!DungeonUtils.isFloor(7) || !DungeonUtils.inBoss) return@on

            val player = mc.player ?: return@on

            // ==========================================
            // 3. 騎乘狀態偵測與即時觸發
            // ==========================================
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

                // 🌟 改成 >= 0 兼容 delay=0 的情況，且在達標的「當下」立刻判定
                if (rideTicks > delay) {
                    hasTriggeredThisRun = true // 鎖上，這場絕對不會再觸發第二次

                    // 🎯 當下立刻檢查畫面
                    val screen = mc.gui.screen() as? AbstractContainerScreen<*>
                    if (screen != null) {
                        val title = screen.title.string.lowercase().replace(Regex("§[0-9a-fk-or]"), "")

                        if (title.contains("spirit leap") || title.contains("teleport to player")) {
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

                                if (teammate != null && teammate.clazz.name.equals(currentClassName, ignoreCase = true)) {
                                    if (!teammate.isDead) {
                                        targetSlot = i
                                        break
                                    }
                                }
                            }

                            if (targetSlot != -1) {
                                mc.gameMode?.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player)
                                modMessage("§a[InstaMid] Auto Leaped to ${currentClassName}!")
                            } else {
                                modMessage("§c[InstaMid] Target class (${currentClassName}) not found or is dead!")
                            }
                        } else {
                            modMessage("§c[InstaMid] Triggered, but Leap menu is not open. Skipped.")
                        }
                    } else {
                        modMessage("§c[InstaMid] Triggered, but no GUI is open. Skipped.")
                    }
                }
            }
        }

        on<LevelEvent.Load> {
            isRiding = false
            rideTicks = 0
            hasTriggeredThisRun = false
        }
    }
}