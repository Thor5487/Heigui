package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.iq200.heigui.features.impl.floor7.TermInfo
import com.iq200.heigui.features.impl.floor7.TermRule
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.dungeon.DungeonClass

fun LiteralNode.setupTermRulesCommand() {
    literal("termrules") {
        runs { showTermRulesHelp() }

        literal("add") {
            runs { showTermRulesAddUsage() }
            runs { className: String, trigger: String, preDevice: String ->
                val dungeonClass = parseDungeonClass(className)
                    ?: return@runs modMessage("§cUnknown class '$className'. Use a, b, h, m, or t.")
                val triggerSection = parseSection(trigger, "s")
                    ?: return@runs showTermRulesAddUsage()
                val deviceSection = parseSection(preDevice, "pre")
                    ?: return@runs showTermRulesAddUsage()

                if (deviceSection <= triggerSection) {
                    return@runs modMessage("§cThe pre-device section must be after the trigger section.")
                }

                if (!TermInfo.addRule(dungeonClass, triggerSection, deviceSection)) {
                    return@runs modMessage("§eThat term rule already exists.")
                }

                modMessage("§aAdded term rule: §e${formatRule(TermRule(dungeonClass, triggerSection, deviceSection))}")
            }
        }

        literal("list") {
            runs {
                val rules = TermInfo.getRules()
                if (rules.isEmpty()) {
                    modMessage("§eNo term rules configured.")
                    return@runs
                }

                modMessage("§aTerm Rules:")
                rules.forEachIndexed { index, rule ->
                    modMessage("§8${index + 1}. §7${formatRule(rule)}", prefix = "")
                }
            }
        }

        literal("remove") {
            runs { modMessage("§cUsage: /hg termrules remove <index>") }
            runs { index: Int ->
                val removed = TermInfo.removeRule(index - 1)
                    ?: return@runs modMessage("§cNo term rule exists at index $index.")
                modMessage("§aRemoved term rule: §e${formatRule(removed)}")
            }
        }
    }
}

private fun showTermRulesHelp() {
    modMessage("§aTerm Rules commands:")
    modMessage("§e/hg termrules add <class> <section> <pre-device> §8- §7Example: /hg termrules add b s1 pre4", prefix = "")
    modMessage("§e/hg termrules list §8- §7List rules in evaluation order.", prefix = "")
    modMessage("§e/hg termrules remove <index> §8- §7Remove a listed rule.", prefix = "")
}

private fun showTermRulesAddUsage() {
    modMessage("§cUsage: /hg termrules add <a|b|h|m|t> <s1-s4> <pre1-pre4>")
}

private fun parseDungeonClass(value: String): DungeonClass? = when (value.lowercase()) {
    "a", "archer" -> DungeonClass.ARCHER
    "b", "bers", "berserk" -> DungeonClass.BERSERK
    "h", "healer" -> DungeonClass.HEALER
    "m", "mage" -> DungeonClass.MAGE
    "t", "tank" -> DungeonClass.TANK
    else -> null
}

private fun parseSection(value: String, prefix: String): Int? {
    if (!value.startsWith(prefix, ignoreCase = true)) return null
    return value.substring(prefix.length).toIntOrNull()?.takeIf { it in 1..4 }
}

private fun formatRule(rule: TermRule): String =
    "${rule.dungeonClass.name.lowercase().replaceFirstChar(Char::titlecase)} s${rule.triggerSection} -> pre${rule.deviceSection}"
