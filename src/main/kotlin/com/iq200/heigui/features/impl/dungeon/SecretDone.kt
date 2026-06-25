package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.HudElement
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.alert
import com.iq200.heigui.utils.render.textDim
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils

object SecretDone : Module (
    name = "Secret Done",
    description = "notification when secrets are enough",
    category = Category.DUNGEON
) {
    private val onlySolo by BooleanSetting("Only Solo", true, desc = "only display needed secrets in solo mode")
    private val notification by BooleanSetting("Notification", true, desc = "make a notification when secrets are enough")
    private var hasNotified = false

    private val hud  by HUD("Needed Secrets", "display how many secrets needed to do") { example ->
        if (!DungeonUtils.inDungeons && !example) return@HUD 0 to 0

        if (!example && onlySolo && !isSolo()) return@HUD  0 to 0

        if (DungeonUtils.totalSecrets == 0 && !example) {
            return@HUD textDim("§7Needed Secrets: §eLoading...", 0, 0)
        }

        val needed = if (example) 3 else DungeonUtils.remainingSecrets

        val textColor = if (needed <= 0) "§a" else "§c"
        val displayNum = needed.coerceAtLeast(0)
        val text = "§7Needed Secrets: $textColor$displayNum"

        return@HUD textDim(text, 0, 0)
    }


    init {
        on<WorldEvent.Load> {
            hasNotified = false
        }

        on<TickEvent.End> {
            if (!notification) return@on
            if (!DungeonUtils.inClear) {
                hasNotified = false
                return@on
            }

            if (hasNotified) return@on
            if (onlySolo && !isSolo()) return@on

            if (DungeonUtils.totalSecrets == 0) return@on

            if (DungeonUtils.remainingSecrets <= 0) {
                hasNotified = true
                alert("§bSecrets Enough!!!")
            }
        }

    }

    private fun isSolo(): Boolean {
        if (!DungeonUtils.inDungeons) return false

        return DungeonUtils.dungeonTeammates.size <= 1
    }
}