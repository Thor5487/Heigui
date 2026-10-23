package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.iq200.heigui.features.impl.dungeon.AutoClick
import com.iq200.heigui.features.impl.dungeon.AutoClickButton


fun LiteralNode.setupCustomAC() {
    literal("cac") {
        runs {
            AutoClick.help()
        }

        literal("help") {
            runs {
                AutoClick.help()
            }
        }

        literal("lc") {
            setupCustomACList(AutoClickButton.LEFT)
        }

        literal("rc") {
            setupCustomACList(AutoClickButton.RIGHT)
        }
    }
}

private fun LiteralNode.setupCustomACList(button: AutoClickButton) {
    literal("add") {
        runs {
            AutoClick.addCurrentItem(button)
        }
    }

    literal("remove") {
        runs {
            AutoClick.removeCurrentItem(button)
        }

        runs { index: Int ->
            AutoClick.removeItem(button, index)
        }
    }

    literal("list") {
        runs {
            AutoClick.listItems(button)
        }
    }
}
