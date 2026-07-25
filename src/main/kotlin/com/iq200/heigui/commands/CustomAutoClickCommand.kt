package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.iq200.heigui.features.impl.dungeon.AutoClick


fun LiteralNode.setupCustomAC() {
    literal("cac") {
        literal("add") {
            runs {
                AutoClick.addCurrentItem()
            }
        }

        literal("remove") {
            runs {
                AutoClick.removeCurrentItem()
            }
        }

        literal("list") {
            runs {
                AutoClick.listItems()
            }
        }
    }
}