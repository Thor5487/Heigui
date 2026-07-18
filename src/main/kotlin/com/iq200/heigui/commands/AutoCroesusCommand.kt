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

                    if (AutoCroesus.ignoreList.add(internalId)) {
                        Heigui.autoCroesusConfig.save()
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
                        modMessage("Please Enter Valid Item Namee")
                    }

                    if (AutoCroesus.ignoreList.remove(internalId)) {
                        Heigui.autoCroesusConfig.save()
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
    }
}
