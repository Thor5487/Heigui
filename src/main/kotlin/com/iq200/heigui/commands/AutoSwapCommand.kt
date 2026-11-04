package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.github.stivais.commodore.utils.GreedyString
import com.iq200.heigui.features.impl.skyblock.AutoSwap
import com.iq200.heigui.utils.modMessage

fun LiteralNode.setupAutoSwapCommand() {
    literal("autoswap") {
        runs { showAutoSwapHelp() }

        literal("add") {
            literal("position") {
                runs { showPositionUsage() }
                runs { slot: Int -> showPositionUsage() }
                runs { slot: Int, radius: Double ->
                    val hotbarSlot = parseHotbarSlot(slot) ?: return@runs showPositionUsage()
                    if (!radius.isFinite() || radius <= 0.0) {
                        return@runs modMessage("§cRadius must be greater than 0.")
                    }
                    AutoSwap.addPositionRule(hotbarSlot, radius)
                }
            }

            literal("chat") {
                runs { showChatUsage() }
                runs { slot: Int, text: GreedyString ->
                    val hotbarSlot = parseHotbarSlot(slot) ?: return@runs showChatUsage()
                    AutoSwap.addChatRule(hotbarSlot, text.toString())
                }
            }
        }

        literal("list") {
            runs {
                val rules = AutoSwap.getRules()
                if (rules.isEmpty()) {
                    modMessage("§eNo Auto Swap rules configured.")
                    return@runs
                }

                modMessage("§aAuto Swap rules:")
                rules.forEachIndexed { index, rule ->
                    modMessage("§8${index + 1}. §7${AutoSwap.describeRule(rule)}", prefix = "")
                }
            }
        }

        literal("remove") {
            runs { modMessage("§cUsage: /hg autoswap remove <index>") }
            runs { index: Int ->
                val removed = AutoSwap.removeRule(index - 1)
                    ?: return@runs modMessage("§cNo Auto Swap rule exists at index $index.")
                modMessage("§aRemoved Auto Swap rule: §e${AutoSwap.describeRule(removed)}")
            }
        }

        literal("clear") {
            runs {
                val count = AutoSwap.clearRules()
                if (count == 0) {
                    modMessage("§eThe Auto Swap rule list is already empty.")
                } else {
                    modMessage("§aCleared §e$count §aAuto Swap rule${if (count == 1) "" else "s"}.")
                }
            }
        }
    }
}

private fun parseHotbarSlot(slot: Int): Int? = (slot - 1).takeIf { it in 0..8 }

private fun showAutoSwapHelp() {
    modMessage("§aAuto Swap commands:")
    modMessage("§e/hg autoswap add position <1-9> <radius> §8- §7Use the held item and current position.", prefix = "")
    modMessage("§e/hg autoswap add chat <1-9> <text> §8- §7Use the held item and matching chat text.", prefix = "")
    modMessage("§e/hg autoswap list §8- §7List configured rules.", prefix = "")
    modMessage("§e/hg autoswap remove <index> §8- §7Remove a rule.", prefix = "")
    modMessage("§e/hg autoswap clear §8- §7Clear all rules.", prefix = "")
}

private fun showPositionUsage() {
    modMessage("§cUsage: /hg autoswap add position <hotbar slot 1-9> <radius>")
    modMessage("§7Hold the item to swap before running the command.", prefix = "")
}

private fun showChatUsage() {
    modMessage("§cUsage: /hg autoswap add chat <hotbar slot 1-9> <text>")
    modMessage("§7Hold the item to swap before running the command.", prefix = "")
}
