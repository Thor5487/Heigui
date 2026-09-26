package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.render.drawStyledBox
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB

object SafariEsp : Module(
    name = "Safari ESP",
    description = "Esp For Safari",
    category = Category.SKYBLOCK
) {
    private val color by ColorSetting("Color", Colors.MINECRAFT_AQUA, desc = "Color for ESP Bounding Box")
    private val sparklingList = mutableSetOf<ArmorStand>()

    init {
        on<RenderEvent.Extract> {
            val pt =  context.gameRenderer().mainCamera().getCameraEntityPartialTicks(mc.deltaTracker)

            sparklingList.forEach { armorStand ->
                val lerpedPos = armorStand.getPosition(pt)

                val aabb = AABB(
                    lerpedPos.x - 0.5,
                    lerpedPos.y,
                    lerpedPos.z - 0.5,
                    lerpedPos.x + 0.5,
                    lerpedPos.y + 1.0,
                    lerpedPos.z + 0.5
                )

                // 3. 畫出外框
                drawStyledBox(
                    aabb = aabb,
                    color = color,
                    style = 0,
                    depth = false
                )
            }
        }

        on<TickEvent.Start> {
            val level = mc.level ?: return@on

            sparklingList.clear()

            level.entitiesForRendering().forEach { entity ->
                if (entity is ArmorStand) {
                    val name = entity.name.string.noControlCodes.lowercase()

                    if (name.contains("sparkling")) {
                        sparklingList.add(entity)
                    }
                }
            }
        }
    }
}