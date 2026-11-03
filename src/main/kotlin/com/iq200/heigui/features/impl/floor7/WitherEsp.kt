package com.iq200.heigui.features.impl.floor7

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.ServerUtils
import com.iq200.heigui.utils.render.drawWireFrameBox
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.phys.Vec3

object WitherEsp : Module(
    name = "Wither ESP",
    description = "Draws the predicted hitboxes of withers in F7 and M7.",
    category = Category.FLOOR7
) {
    private val pingCorrection by BooleanSetting(
        "Ping Correction",
        true,
        "Moves hitboxes to their latency-predicted positions."
    )
    private val pingAdjustment by NumberSetting(
        "Ping Adjustment",
        0,
        -500,
        500,
        10,
        "Adds or subtracts latency from hitbox prediction.",
        "ms"
    ).withDependency { pingCorrection }
    private val color by ColorSetting(
        "Color",
        Colors.MINECRAFT_RED,
        allowAlpha = true,
        desc = "Wither hitbox color."
    )

    init {
        on<RenderEvent.Extract> {
            val level = mc.level ?: return@on
            if (!DungeonUtils.isFloor(7) || !DungeonUtils.inBoss) return@on

            val partialTick = context.gameRenderer().mainCamera().getCameraEntityPartialTicks(mc.deltaTracker)
            level.entitiesForRendering().forEach { entity ->
                if (entity !is WitherBoss || !entity.isAlive || entity.invulnerableTicks >= 800) return@forEach

                val renderPosition = getRenderPosition(entity, partialTick)
                val renderedHitbox = entity.boundingBox.move(renderPosition.subtract(entity.position()))
                drawWireFrameBox(renderedHitbox, color, thickness = 3f, depth = false)
            }
        }
    }

    private fun getRenderPosition(entity: Entity, partialTick: Float): Vec3 {
        val lerpedPosition = entity.getPosition(partialTick)
        if (!pingCorrection) return lerpedPosition

        val movementPerTick = Vec3(
            entity.x - entity.xo,
            entity.y - entity.yo,
            entity.z - entity.zo
        )
        val adjustedPing = (ServerUtils.currentPing + pingAdjustment).coerceAtLeast(0)
        val ticksAhead = adjustedPing / MILLIS_PER_TICK
        return lerpedPosition.add(movementPerTick.scale(ticksAhead))
    }

    private const val MILLIS_PER_TICK = 50.0
}
