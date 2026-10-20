package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.iq200.heigui.features.impl.dungeon.BlockWrongBreak
import com.iq200.heigui.utils.modMessage

fun LiteralNode.setupBlockWrongBreakCommand() {
    literal("bwb") {
        runs {
            modMessage("§e/hg bwb edit §8- §7Toggle Block Wrong Break edit mode.")
            modMessage("§e/hg bwb list §8- §7Show all protected block coordinates.")
            modMessage("§e/hg bwb clear §8- §7Clear all protected block coordinates.")
        }

        literal("edit") {
            runs {
                BlockWrongBreak.toggleEditMode()
            }
        }

        literal("list") {
            runs {
                BlockWrongBreak.listBlocks()
            }
        }

        literal("clear") {
            runs {
                BlockWrongBreak.clearBlocks()
            }
        }
    }
}
