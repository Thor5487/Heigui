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
            // 💀 功能 1: Skull Texture (Item, Block, Marker)
            // ==========================================
            if (skullTexture) {
                var heldTexture: String? = null
                var markerTexture: String? = null

                // A. 手上物品
                if (holdingSkull.`is`(Items.PLAYER_HEAD)) {
                    heldTexture = holdingSkull.texture
                }

                // B. 地上地形方塊
                var blockTexture: String? = null // 改成用 blockTexture 接收 Base64

                if (hrSkullPos != null) {
                    val blockEntity = level.getBlockEntity(hrSkullPos)
                    if (blockEntity is SkullBlockEntity) {
                        val profileComponent = blockEntity.ownerProfile
                        if (profileComponent != null) {
                            val gameProfile = profileComponent.partialProfile()
                            // 🌟 透過 properties 抓取 Base64 貼圖
                            val textureProperty = gameProfile.properties.get("textures").firstOrNull()
                            blockTexture = textureProperty?.value
                        }
                    }
                }

                // C. 空間搜索：抓取隱形 Marker 頭上的頭盔 Texture
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
                    // 只要頭上有戴玩家頭顱的盔甲座 (通常 Marker 會是隱形的，但為了保險起見我們抓所有戴頭顱的)
                    if (headItem.`is`(Items.PLAYER_HEAD)) {
                        markerTexture = headItem.texture
                        break // 抓到第一個就跳出
                    }
                }

                // --- 輸出結果 ---
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
            // 🧍 功能 2: NPC Texture
            // ==========================================
            if (npcTexture && hitEntity != null) {
                var entityTexture: String? = null
                val entityName = hitEntity.name.string.noControlCodes // 去除顏色代碼，取得乾淨的 NPC 名字

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