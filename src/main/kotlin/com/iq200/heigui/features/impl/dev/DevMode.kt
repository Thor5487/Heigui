package com.iq200.heigui.features.impl.dev

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.events.PlayerInputEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.texture
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object DevMode : Module(
    name = "Dev Mode",
    description = "Useful for Developers",
    category = Category.DEV
) {
    private val skullTexture by BooleanSetting("Skull Texture", false, desc = "Get Skull Texture with Right Click on Item, Block or Marker")
    private val npcTexture by BooleanSetting("NPC Texture", false, desc = "Get NPC Texture Base64 with Right Click on NPC")

    init {
        on<PlayerInputEvent.Use> {
            val player = mc.player ?: return@on
            val level = mc.level ?: return@on
            val holdingSkull = player.getItemInHand(InteractionHand.MAIN_HAND)

            val hrSkullPos = if (result?.type == HitResult.Type.BLOCK) {
                val blockHit = result as BlockHitResult
                val pos = blockHit.blockPos
                val block = level.getBlockState(pos).block
                if (block is AbstractSkullBlock) pos else null
            } else null

            val hitEntity = if (result?.type == HitResult.Type.ENTITY) {
                (result as EntityHitResult).entity
            } else null

            // ==========================================

            // ==========================================
            if (skullTexture) {
                var heldTexture: String? = null
                var markerTexture: String? = null


                if (holdingSkull.`is`(Items.PLAYER_HEAD)) {
                    heldTexture = holdingSkull.texture
                }


                var blockTexture: String? = null

                if (hrSkullPos != null) {
                    val blockEntity = level.getBlockEntity(hrSkullPos)
                    if (blockEntity is SkullBlockEntity) {
                        val profileComponent = blockEntity.ownerProfile
                        if (profileComponent != null) {
                            val gameProfile = profileComponent.partialProfile()

                            val textureProperty = gameProfile.properties.get("textures").firstOrNull()
                            blockTexture = textureProperty?.value
                        }
                    }
                }


                val searchPos = when (result?.type) {
                    HitResult.Type.BLOCK -> {
                        val pos = (result as BlockHitResult).blockPos
                        Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                    }
                    HitResult.Type.ENTITY -> (result as EntityHitResult).entity.position()
                    else -> player.eyePosition.add(player.lookAngle.scale(3.0))
                }

                val box = AABB.ofSize(searchPos, 1.5, 1.5, 1.5)
                val nearbyStands = level.getEntitiesOfClass(ArmorStand::class.java, box)

                for (stand in nearbyStands) {
                    val headItem = stand.getItemBySlot(EquipmentSlot.HEAD)

                    if (headItem.`is`(Items.PLAYER_HEAD)) {
                        markerTexture = headItem.texture
                        break
                    }
                }


                var foundAny = false

                if (!heldTexture.isNullOrEmpty()) {
                    modMessage("§a[DevMode] §fFound Skull Texture from §e[Held Item]§f:")
                    modMessage("§7$heldTexture")
                    foundAny = true
                }

                if (!blockTexture.isNullOrEmpty()) {
                    modMessage("§a[DevMode] §fFound Texture from §e[Block]§f:")
                    modMessage("§7$blockTexture")
                    foundAny = true
                }

                if (!markerTexture.isNullOrEmpty()) {
                    modMessage("§a[DevMode] §fFound Texture from §e[Marker ArmorStand]§f:")
                    modMessage("§7$markerTexture")
                    foundAny = true
                }

                if (!foundAny && (holdingSkull.`is`(Items.PLAYER_HEAD) || hrSkullPos != null)) {
                    modMessage("§c[DevMode] No texture property found on this skull.")
                }
            }

            // ==========================================

            // ==========================================
            if (npcTexture && hitEntity != null) {
                var entityTexture: String? = null
                val entityName = hitEntity.name.string.noControlCodes

                if (hitEntity is Player) {
                    val gameProfile = hitEntity.gameProfile
                    val textureProperty = gameProfile.properties.get("textures").firstOrNull()
                    entityTexture = textureProperty?.value
                }

                if (!entityTexture.isNullOrEmpty()) {
                    modMessage("§a[DevMode] §fFound Texture from §e[NPC: $entityName]§f:")
                    modMessage("§7$entityTexture")
                } else if (hitEntity is Player) {
                    modMessage("§c[DevMode] No texture property found on NPC: $entityName.")
                }
            }
        }
    }
}