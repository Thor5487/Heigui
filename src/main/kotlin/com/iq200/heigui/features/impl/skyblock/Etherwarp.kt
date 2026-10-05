package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.events.core.onReceive
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color.Companion.withAlpha
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.createSoundSettings
import com.iq200.heigui.utils.getBlockBounds
import com.iq200.heigui.utils.getTunerDistance
import com.iq200.heigui.utils.isEtherwarpItem
import com.iq200.heigui.utils.itemId
import com.iq200.heigui.utils.playSoundSettings
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.skyblock.EtherUtils
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB

object Etherwarp : Module(
    name = "Etherwarp",
    description = "Highlights the block targeted by Etherwarp.",
    category = Category.SKYBLOCK
) {
    private const val BASE_DISTANCE = 57
    private const val ETHERWARP_VOLUME = 1f
    private const val ETHERWARP_PITCH = 0.53968257f

    private val RENDER_STYLES = listOf("Filled", "Outline", "Both")
    private val ETHERWARP_SWORDS = setOf("ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID")

    private val successColor by ColorSetting(
        "Success Color",
        Colors.MINECRAFT_GREEN.withAlpha(0.85f),
        allowAlpha = true,
        desc = "Color used when Etherwarp can succeed."
    )
    private val failColor by ColorSetting(
        "Fail Color",
        Colors.MINECRAFT_RED.withAlpha(0.85f),
        allowAlpha = true,
        desc = "Color used when Etherwarp cannot succeed."
    )
    private val renderStyle by SelectorSetting(
        "Render Style",
        "Both",
        RENDER_STYLES,
        desc = "How the targeted block is highlighted."
    )
    private val fullBlock by BooleanSetting(
        "Full Block",
        false,
        desc = "Highlights a full block instead of its collision shape."
    )
    private val depth by BooleanSetting(
        "Depth",
        false,
        desc = "Renders the highlight through walls."
    )
    private val customSound by BooleanSetting(
        "Sound",
        true,
        desc = "Replaces the Etherwarp sound with the selected sound."
    )
    private val soundSettings = createSoundSettings(
        "Etherwarp Sound",
        "entity.experience_orb.pickup"
    ) { customSound }

    init {
        on<RenderEvent.Extract> {
            if (mc.gui.screen() != null) return@on

            val player = mc.player ?: return@on
            val stack = player.mainHandItem
            if (!player.isShiftKeyDown || !stack.isAoteOrAotvWithEtherwarp()) return@on

            val position = TeleportOptimization.activeZpcmPosition ?: player.position()
            val eyePosition = position.add(0.0, EtherUtils.getEyeHeight().toDouble(), 0.0)
            val (target, canEtherwarp) = EtherUtils.getEtherPosFromOrigin(
                eyePosition,
                player.yRot,
                player.xRot,
                BASE_DISTANCE + stack.getTunerDistance()
            )
            target ?: return@on

            val box = if (fullBlock) {
                AABB(target)
            } else {
                target.getBlockBounds()?.move(target) ?: AABB(target)
            }

            drawStyledBox(
                aabb = box,
                color = if (canEtherwarp) successColor else failColor,
                style = renderStyle,
                depth = depth
            )
        }

        onReceive<ClientboundSoundPacket> {
            if (!customSound || sound.value() != SoundEvents.ENDER_DRAGON_HURT ||
                volume != ETHERWARP_VOLUME || pitch != ETHERWARP_PITCH
            ) return@onReceive

            playSoundSettings(soundSettings())
            it.cancel()
        }
    }

    private fun ItemStack.isAoteOrAotvWithEtherwarp(): Boolean =
        itemId in ETHERWARP_SWORDS && isEtherwarpItem()
}
