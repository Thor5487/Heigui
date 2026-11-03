package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.clickgui.settings.impl.DropdownSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.SecretPickupEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color.Companion.withAlpha
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.createSoundSettings
import com.iq200.heigui.utils.getBlockBounds
import com.iq200.heigui.utils.handlers.schedule
import com.iq200.heigui.utils.playSoundSettings
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import java.util.concurrent.CopyOnWriteArrayList

object SecretClicked : Module(
    name = "Secret Clicked",
    description = "Provides visual and audio feedback when a dungeon secret is clicked.",
    category = Category.DUNGEON
) {
    private val boxesDropdown by DropdownSetting(
        "Secret Boxes",
        desc = "Settings for boxes drawn around clicked secrets."
    )
    private val boxes by BooleanSetting(
        "Show Boxes",
        true,
        desc = "Draw a box around clicked secrets."
    ).withDependency { boxesDropdown }
    private val style by SelectorSetting(
        "Box Style",
        "Filled Outline",
        listOf("Filled", "Outline", "Filled Outline"),
        desc = "The style used to draw clicked secret boxes."
    ).withDependency { boxesDropdown && boxes }
    private val color by ColorSetting(
        "Box Color",
        Colors.MINECRAFT_GOLD.withAlpha(0.8f),
        allowAlpha = true,
        desc = "The color of clicked secret boxes."
    ).withDependency { boxesDropdown && boxes }
    private val depthCheck by BooleanSetting(
        "Depth Check",
        false,
        desc = "Hide boxes behind walls."
    ).withDependency { boxesDropdown && boxes }
    private val lockedColor by ColorSetting(
        "Locked Color",
        Colors.MINECRAFT_RED.withAlpha(0.8f),
        allowAlpha = true,
        desc = "The box color used when a chest is locked."
    ).withDependency { boxesDropdown && boxes }
    private val timeToStay by NumberSetting(
        "Time To Stay",
        7f,
        1f,
        120f,
        0.5f,
        desc = "How long clicked secret boxes remain visible.",
        unit = "s"
    ).withDependency { boxesDropdown && boxes }
    private val boxInBoss by BooleanSetting(
        "Box In Boss",
        false,
        desc = "Draw clicked secret boxes inside boss rooms."
    ).withDependency { boxesDropdown && boxes }
    private val itemBoxes by BooleanSetting(
        "Item Boxes",
        true,
        desc = "Draw boxes for collected secret items."
    ).withDependency { boxesDropdown && boxes }

    private val chimeDropdown by DropdownSetting(
        "Secret Chime",
        desc = "Settings for the sound played when a secret is clicked."
    )
    private val chime by BooleanSetting(
        "Play Chime",
        true,
        desc = "Play a sound when a secret is clicked."
    ).withDependency { chimeDropdown }
    private val soundSettings = createSoundSettings("Chime Sound", "entity.blaze.hurt") {
        chimeDropdown && chime
    }
    private val chimeInBoss by BooleanSetting(
        "Chime In Boss",
        false,
        desc = "Play the secret chime inside boss rooms."
    ).withDependency { chimeDropdown && chime }

    private data class Secret(val pos: BlockPos, val aabb: AABB, var locked: Boolean = false)

    private val clickedSecrets = CopyOnWriteArrayList<Secret>()
    private var lastPlayed = 0L

    init {
        on<SecretPickupEvent.Interact> {
            addSecret(blockPos)
            playChime()
        }

        on<SecretPickupEvent.Bat> {
            addSecret(BlockPos.containing(packet.x, packet.y, packet.z))
            playChime()
        }

        on<SecretPickupEvent.Item> {
            if (itemBoxes) addSecret(entity.blockPosition())
            playChime()
        }

        on<ChatPacketEvent> {
            if (value == "That chest is locked!") clickedSecrets.lastOrNull()?.locked = true
        }

        on<RenderEvent.Extract> {
            if (!boxes || !DungeonUtils.inDungeons || (DungeonUtils.inBoss && !boxInBoss)) return@on

            clickedSecrets.forEach { secret ->
                drawStyledBox(
                    secret.aabb,
                    if (secret.locked) lockedColor else color,
                    style = style,
                    depth = depthCheck
                )
            }
        }

        on<WorldEvent.Load> {
            clickedSecrets.clear()
        }
    }

    private fun playChime() {
        val now = System.currentTimeMillis()
        if (!chime || (DungeonUtils.inBoss && !chimeInBoss) || now - lastPlayed <= 10L) return

        playSoundSettings(soundSettings())
        lastPlayed = now
    }

    private fun addSecret(pos: BlockPos) {
        if (!boxes || (DungeonUtils.inBoss && !boxInBoss) || clickedSecrets.any { it.pos == pos }) return

        val secret = Secret(pos, pos.getBlockBounds()?.move(pos) ?: AABB(pos))
        clickedSecrets.add(secret)
        schedule((timeToStay * 20f).toInt()) { clickedSecrets.remove(secret) }
    }
}
