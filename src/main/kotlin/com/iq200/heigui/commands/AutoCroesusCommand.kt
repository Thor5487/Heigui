package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.github.stivais.commodore.utils.GreedyString
import com.iq200.heigui.features.impl.dungeon.AutoCroesus
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.PriceUtils

fun LiteralNode.setupAutoCroesusCommand() {

    literal("ac") {
        runs {
            AutoCroesus.help()
        }

        literal("help") {
            runs {
                AutoCroesus.help()
            }
        }

        literal("update") {
            runs {
                PriceUtils.fetchPrices(notifyPlayer = true)
            }
        }

        // ==========================================

        // ==========================================
        literal("go") {
            runs {
                if (AutoCroesus.enabled) {
                    AutoCroesus.go()
                }

            }
        }

        literal("ignore") {


            literal("add") {
                runs { item: GreedyString ->
                    val keyword = item.toString().trim()


                    val exists = AutoCroesus.ignoreList.any { it.equals(keyword, ignoreCase = true) }

                    if (!exists) {
                        AutoCroesus.ignoreConfig.update { it.ignoreList.add(keyword) }
                        modMessage("§aSuccessfully added §e'$keyword' §ato the ignore list!")
                    } else {
                        modMessage("§c'$keyword' is already in the ignore list.")
                    }
                }
            }


            literal("remove") {
                runs { item: GreedyString ->
                    val keyword = item.toString().trim()

                    if (keyword.isEmpty()) {
                        return@runs modMessage("§cPlease Enter Valid Item Name")
                    }


                    val targetToRemove = AutoCroesus.ignoreList.find { it.equals(keyword, ignoreCase = true) }

                    if (targetToRemove != null) {
                        AutoCroesus.ignoreConfig.update { it.ignoreList.remove(targetToRemove) }
                        modMessage("§aSuccessfully removed §e'$targetToRemove' §afrom the ignore list!")
                    } else {
                        modMessage("§c'$keyword' was not found in the ignore list.")
                    }
                }
            }


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


                    AutoCroesus.resetFloorData(targetFloor)
                }
            }

            runs {
                modMessage("§c[AutoCroesus] Usage: /hg ac loot <floor> (e.g. m6, f7)")
            }

            runs { floor: String ->
                val targetFloor = floor.lowercase().trim()
                val floorRegex = Regex("^[fm][1-7]$")


                if (!floorRegex.matches(targetFloor)) {
                    modMessage("§c[AutoCroesus] Usage: /hg ac loot <floor> (e.g. m6, f7)")
                    return@runs
                }


                val floorData = AutoCroesus.trackerConfig.data.floors[targetFloor]
                if (floorData == null || floorData.runsOpened == 0) {
                    modMessage("§c[AutoCroesus] Error: No data found for floor '§e${targetFloor.uppercase()}§c'")
                    return@runs
                }


                AutoCroesus.displayHoverLootTracker(targetFloor)
            }
        }
    }
}
