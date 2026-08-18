package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.github.stivais.commodore.utils.GreedyString
import com.iq200.heigui.Heigui
import com.iq200.heigui.features.impl.dungeon.AutoCroesus
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.PriceUtils
import net.minecraft.network.chat.Component

fun LiteralNode.setupAutoCroesusCommand() {

    literal("ac") {
        literal("update") {
            runs {
                PriceUtils.fetchPrices(notifyPlayer = true)
            }
        }

        // ==========================================
        // 2. 啟動開箱指令: /hg ac go
        // ==========================================
        literal("go") {
            runs {
                if (AutoCroesus.enabled) {
                    AutoCroesus.go()
                }

            }
        }

        literal("ignore") {

            // 新增黑名單
            literal("add") {
                runs { item: GreedyString ->
                    val internalId = item.toString().trim().replace(" ", "_").uppercase()

                    if (!AutoCroesus.ignoreList.contains(internalId)) {
                        AutoCroesus.ignoreConfig.update { it.ignoreList.add(internalId) }
                        modMessage("§aSuccessfully added §e'$internalId' §ato the ignore list!")
                    } else {
                        modMessage("§c'$internalId' is already in the ignore list.")
                    }
                }
            }

            // 移除黑名單
            literal("remove") {
                runs { item: GreedyString ->
                    val internalId = item.toString().trim().replace(" ", "_").uppercase()

                    if (internalId.isEmpty()) {
                        return@runs modMessage("Please Enter Valid Item Namee")
                    }

                    if (AutoCroesus.ignoreList.contains(internalId)) {
                        AutoCroesus.ignoreConfig.update { it.ignoreList.remove(internalId) }
                        modMessage("§aSuccessfully removed §e'$internalId' §afrom the ignore list!")
                    } else {
                        modMessage("§c'$internalId' was not found in the ignore list.")
                    }
                }
            }

            // 列出所有黑名單
            literal("list") {
                runs {
                    if (AutoCroesus.ignoreList.isEmpty()) {
                        modMessage("§eIgnore list is currently empty.")
                    } else {
                        modMessage("§aAutoCroesus Ignore List:")
                        AutoCroesus.ignoreList.forEach { item ->
                            modMessage("§8- §7$item", prefix = "")
                        }
                    }
                }
            }
        }

        literal("loot") {
            literal("reset") {
                runs {
                    modMessage("§c[AutoCroesus] Usage: /hg ac loot reset <floor> (e.g. m6, f7)")
                }

                runs { floor: String ->
                    val targetFloor = floor.lowercase().trim()
                    val floorRegex = Regex("^[fm][1-7]$")

                    if (!floorRegex.matches(targetFloor)) {
                        modMessage("§c[AutoCroesus] Usage: /hg ac loot reset <floor> (e.g. m6, f7)")
                        return@runs
                    }

                    // 呼叫我們剛剛在 AutoCroesus 寫好的重置函式
                    AutoCroesus.resetFloorData(targetFloor)
                }
            }

            runs {
                modMessage("§c[AutoCroesus] Usage: /hg ac loot <floor> (e.g. m6, f7)")
            }

            runs { floor: String ->
                val targetFloor = floor.lowercase().trim()
                val floorRegex = Regex("^[fm][1-7]$")

                // 檢查格式是否正確
                if (!floorRegex.matches(targetFloor)) {
                    modMessage("§c[AutoCroesus] Usage: /hg ac loot <floor> (e.g. m6, f7)")
                    return@runs
                }

                // 檢查該樓層是否有紀錄
                val floorData = AutoCroesus.trackerConfig.data.floors[targetFloor]
                if (floorData == null || floorData.runsOpened == 0) {
                    modMessage("§c[AutoCroesus] Error: No data found for floor '§e${targetFloor.uppercase()}§c'")
                    return@runs
                }

                // 正常執行顯示
                AutoCroesus.displayHoverLootTracker(targetFloor)
            }
        }
    }
}
