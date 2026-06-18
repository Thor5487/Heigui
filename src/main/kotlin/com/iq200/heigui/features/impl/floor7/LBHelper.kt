package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.createSoundSettings
import com.iq200.heigui.utils.handlers.schedule
import com.iq200.heigui.utils.playSoundSettings
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.phys.AABB

object LBHelper : Module (
    name = "LB Helper",
    description = "auto release LB and recharge",
    category = Category.FLOOR7
) {
    private val RED_TICK by NumberSetting("Red Dragon", 10, 5, 20, 1, "max tick for red dragon", "tick")
    private val GREEN_TICK by NumberSetting("Green Dragon", 9, 5, 20, 1, "max tick for green dragon", "tick")
    private val PURPLE_TICK by NumberSetting("Purple Dragon", 7, 5, 20, 1, "max tick for purple dragon", "tick")
    private val BLUE_TICK by NumberSetting("Blue Dragon", 10, 5, 20, 1, "max tick for blue dragon", "tick")
    private val ORANGE_TICK by NumberSetting("Orange Dragon", 9, 5, 20, 1, "max tick for orange dragon", "tick")
    private val sound by BooleanSetting("Sound", false, "play sound when release")
    private val soundSetting = createSoundSettings("Release Sound", "entity.experience_orb.pickup") { sound }

    private var isHolding : Boolean = false
    private var isCharging : Boolean = false
    private var chargeTick : Int = 0


    init {
        on<InputEvent> {
            val useKey = (mc.options.keyUse as KeyMappingAccessor).key

            if (key != useKey) return@on

            isHolding = isPress
            if (isHolding) return@on
            reset()
        }

        on<PacketEvent.Send> {
            if (packet !is ServerboundUseItemPacket) return@on
            val item = mc.player?.mainHandItem ?: return@on
            val itemName = item.hoverName.string.lowercase()

            if (!itemName.contains("last breath")) return@on
            isCharging = true
            chargeTick = 0
        }


        on<TickEvent.Server> {
            if (mc.screen != null) return@on reset()
            if (!isHolding && !isCharging) return@on

            val itemName = mc.player?.mainHandItem?.hoverName?.string?.lowercase() ?: return@on reset()
            if (!itemName.contains("last breath")) return@on reset()

            chargeTick++

            val ticks = getTicks().takeIf {it > 0} ?: return@on
            if (chargeTick >= ticks) {
                schedule(0, true) {
                    fire()
                }
            }
        }
    }

    private fun reset() {
        isCharging = false
        chargeTick = 0
    }

    private fun fire() {
        if (sound) {
            playSoundSettings(soundSetting())
        }

        mc.options.keyUse.isDown = false
        reset()
        schedule(2, false) {
            if (isHolding && mc.screen == null) mc.options.keyUse.isDown = true

        }

    }

    private fun getTicks(): Int {
        val player = mc.player?.position() ?: return Int.MAX_VALUE
        val inF7Boss = DungeonUtils.isFloor(7) && DungeonUtils.inBoss
        if (!inF7Boss) return Int.MAX_VALUE

        return when {
            AABB(47.0, 8.0, 113.0, 64.0, 28.0, 135.0).contains(player) -> PURPLE_TICK
            AABB(13.0, 5.0, 85.0, 40.0, 27.0, 103.0).contains(player) -> GREEN_TICK
            AABB(13.0, 4.0, 47.0, 40.0, 20.0, 68.0).contains(player) -> RED_TICK
            AABB(72.0, 3.0, 47.0, 97.0, 31.0, 65.0).contains(player) -> ORANGE_TICK
            AABB(72.0, 3.0, 85.0, 97.0, 31.0, 107.0).contains(player) -> BLUE_TICK
            else -> Int.MAX_VALUE
        }
    }
}