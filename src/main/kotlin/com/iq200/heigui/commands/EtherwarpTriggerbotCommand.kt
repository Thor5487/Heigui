package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.iq200.heigui.features.impl.skyblock.EtherwarpTriggerbot
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.Island

fun LiteralNode.setupEtherwarpTriggerbotCommand() {
    literal("et") {
        runs { showEtherwarpTriggerbotHelp() }

        literal("edit") {
            runs { EtherwarpTriggerbot.toggleEditMode() }
        }

        for (island in ETHERWARP_COMMAND_ISLANDS) {
            literal(island.commandName()) {
                runs { showIslandUsage(island) }

                literal("list") {
                    runs { EtherwarpTriggerbot.listTargets(island) }
                }

                literal("remove") {
                    runs { modMessage("§cUsage: /hg et ${island.commandName()} remove <index>") }
                    runs { index: Int ->
                        val removed = EtherwarpTriggerbot.removeTarget(island, index - 1)
                            ?: return@runs modMessage("§cNo ${island.displayName} target exists at index $index.")
                        modMessage("§aRemoved Etherwarp target §e${removed.x}, ${removed.y}, ${removed.z} §afrom §e${island.displayName}§a.")
                    }
                }

                literal("clear") {
                    runs {
                        val count = EtherwarpTriggerbot.clearTargets(island)
                        if (count == 0) {
                            modMessage("§eThe Etherwarp target list for §6${island.displayName} §eis already empty.")
                        } else {
                            modMessage("§aCleared §e$count §aEtherwarp target${if (count == 1) "" else "s"} from §e${island.displayName}§a.")
                        }
                    }
                }
            }
        }
    }
}

private val ETHERWARP_COMMAND_ISLANDS = Island.entries.filterNot { it == Island.Unknown }

private fun Island.commandName(): String =
    name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

private fun showEtherwarpTriggerbotHelp() {
    modMessage("§aEtherwarp Triggerbot commands:")
    modMessage("§e/hg et edit §8- §7Toggle edit mode and right-click blocks to add targets.", prefix = "")
    modMessage("§e/hg et <island> list §8- §7List the island's saved targets.", prefix = "")
    modMessage("§e/hg et <island> remove <index> §8- §7Remove a saved target.", prefix = "")
    modMessage("§e/hg et <island> clear §8- §7Clear the island's saved targets.", prefix = "")
    modMessage("§7Press Tab after §e/hg et §7to select an island.", prefix = "")
}

private fun showIslandUsage(island: Island) {
    val name = island.commandName()
    modMessage("§e/hg et $name list")
    modMessage("§e/hg et $name remove <index>")
    modMessage("§e/hg et $name clear")
}
