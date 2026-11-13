package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.iq200.heigui.features.impl.dungeon.LeapMenu
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.dungeon.DungeonClass

fun LiteralNode.setupLeapCommand() {
    literal("leap") {
        runs { showLeapHelp() }

        literal("add") {
            runs { showLeapAddUsage() }
            runs { className: String, playerName: String ->
                val fakeClass = parseLeapClass(className)
                    ?: return@runs modMessage("§cUnknown class '$className'. Use archer, berserk, healer, mage, or tank.")
                LeapMenu.addRule(fakeClass, playerName)
            }
        }

        literal("list") {
            runs {
                val rules = LeapMenu.getRuleDisplays()
                if (rules.isEmpty()) {
                    modMessage("§eNo leap rules configured.")
                    return@runs
                }

                modMessage("§aLeap rules:")
                rules.forEachIndexed { index, rule ->
                    val name = rule.name ?: "Name loading/unavailable"
                    modMessage(
                        "§8${index + 1}. §e$name §7→ §e${formatLeapClass(rule.fakeClass)}",
                        prefix = ""
                    )
                }
            }
        }

        literal("remove") {
            runs { modMessage("§cUsage: /hg leap remove <index>") }
            runs { index: Int ->
                val removed = LeapMenu.removeRule(index - 1)
                    ?: return@runs modMessage("§cNo leap rule exists at index $index.")
                val name = removed.name ?: "unresolved player"
                modMessage("§aRemoved leap rule: §e$name §7→ §e${formatLeapClass(removed.fakeClass)}§a.")
            }
        }
    }
}

private fun showLeapHelp() {
    modMessage("§aLeap commands:")
    modMessage("§e/hg leap add <class> <name> §8- §7Add or update a fake-class rule.", prefix = "")
    modMessage("§e/hg leap list §8- §7List configured leap rules.", prefix = "")
    modMessage("§e/hg leap remove <index> §8- §7Remove a listed rule.", prefix = "")
}

private fun showLeapAddUsage() {
    modMessage("§cUsage: /hg leap add <archer|berserk|healer|mage|tank> <name>")
}

private fun parseLeapClass(value: String): DungeonClass? = when (value.lowercase()) {
    "a", "archer" -> DungeonClass.ARCHER
    "b", "bers", "berserk" -> DungeonClass.BERSERK
    "h", "healer" -> DungeonClass.HEALER
    "m", "mage" -> DungeonClass.MAGE
    "t", "tank" -> DungeonClass.TANK
    else -> null
}

private fun formatLeapClass(clazz: DungeonClass): String =
    clazz.name.lowercase().replaceFirstChar(Char::titlecase)
