package com.iq200.heigui.features.impl.dungeon.icefill

import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.render.drawLine
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

object IceFillRenderer {
    private val pathColor = Color(0, 255, 0, 255)

    fun render(event: RenderEvent.Extract, path: List<BlockPos>) {
        if (path.size < 2) return

        event.drawLine(
            points = path.map { Vec3(it.x + 0.5, it.y.toDouble(), it.z + 0.5) },
            color = pathColor,
            depth = true,
            thickness = 5f
        )
    }
}
