package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.utils.DeathTickUtil
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.render.textDim
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils


object DeathTick : Module(
    "DeathTick",
    description = "DeathTick for Dungeons",
    category = Category.DUNGEON
) {
    private val deathtickHud by HUD("DeathTick Hud", "Shows DeathTick") {
        if (!enabled) 0 to 0

        if (it) {
            textDim("§2DeathTick: 35", 0, 0)
        }

        else if (DungeonUtils.inDungeons) {
            textDim("§2DeathTick: ${DeathTickUtil.ticks}", 0, 0)
        }


        else  0 to 0
    }
}