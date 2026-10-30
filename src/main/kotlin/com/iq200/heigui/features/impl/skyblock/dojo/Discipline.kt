package com.iq200.heigui.features.impl.skyblock.dojo

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.PlayerUtils
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult

internal object Discipline {
    fun update() {
        val player = mc.player ?: return
        if (mc.gui.screen() != null) return

        val zombie = (mc.hitResult as? EntityHitResult)?.entity as? Zombie ?: return
        val requiredSword = swordFor(zombie.getItemBySlot(EquipmentSlot.HEAD).item) ?: return
        val swordSlot = PlayerUtils.findItemInHotbar(requiredSword) ?: return

        if (player.inventory.selectedSlot != swordSlot) {
            PlayerUtils.setHotbarSlot(swordSlot)
        }
    }

    private fun swordFor(helmet: Item): Item? = when (helmet) {
        Items.LEATHER_HELMET -> Items.WOODEN_SWORD
        Items.IRON_HELMET -> Items.IRON_SWORD
        Items.GOLDEN_HELMET -> Items.GOLDEN_SWORD
        Items.DIAMOND_HELMET -> Items.DIAMOND_SWORD
        else -> null
    }
}
