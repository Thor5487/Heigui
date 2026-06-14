package com.iq200.heigui.features.impl.mining

import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import net.minecraft.world.level.block.StainedGlassPaneBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

object BigPane : Module(
    "BigPane",
    description = "Full Block for Glass Pane, Useful for Gemstone Mining",
    category = Category.MINING
) {
    fun getShape(state: BlockState): VoxelShape? {
        if (!enabled) return null

        return when (state.block) {
            is StainedGlassPaneBlock -> Shapes.block()
            else -> null
        }
    }
}