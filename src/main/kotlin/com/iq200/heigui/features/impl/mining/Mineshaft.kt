package com.iq200.heigui.features.impl.mining

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.events.core.on
import com.iq200.heigui.events.*
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.render.textDim
import net.minecraft.world.inventory.ClickType

object Mineshaft : Module (
    name = "Mineshaft",
    description = "Features for Mineshaft",
    category = Category.MINING
) {
    private val redirectClick by BooleanSetting("Redirect Click", false, "Redirect Click and Block Clicks in Cooldown")
    private var triggerTime = 0L;
    private val cooldownDuration = 30_000L;
    private val pickSlot = 13;

    init {
        on<ChatPacketEvent> {
            if (value.matches(Regex(".+\n.+entered Glacite Mineshafts!\n.+"))) {
                triggerTime = System.currentTimeMillis();
            }
        }

        on<GuiEvent.MouseClick> {
            if (!redirectClick) return@on

            val containerName = mc.screen?.title?.string ?: return@on

            if (containerName.contains("Glacite Mineshaft", ignoreCase = true)) {
                val currentTime = System.currentTimeMillis()
                val timePassed = currentTime - triggerTime
                val timeLeft = cooldownDuration - timePassed

                cancel()

                if (timeLeft < 0) {
                    val player = mc.player ?: return@on
                    val gameMode = mc.gameMode ?: return@on

                    val containerId = player.containerMenu.containerId

                    gameMode.handleInventoryMouseClick(
                        containerId,
                        pickSlot,
                        0,
                        ClickType.PICKUP,
                        player
                    )
                }
            }
        }
    }

    private val timerHud by HUD("Mineshaft CD Hud", "Shows cooldown for mineshaft") {
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - triggerTime
        val timeLeft = cooldownDuration - timePassed

        if (!enabled) 0 to 0

        if (it) {
            textDim("§bMineshaft CD: §a30.0s", 0, 0, Colors.WHITE)
        }
        else if (timeLeft > 0) {
            val secondsLeft = String.format("%.1f", timeLeft / 1000f)
            textDim("§bMineshaft CD: §a${secondsLeft}s", 0, 0, Colors.WHITE)
        }
        else {
            0 to 0
        }
    }
}