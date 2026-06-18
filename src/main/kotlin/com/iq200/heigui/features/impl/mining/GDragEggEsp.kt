package com.iq200.heigui.features.impl.mining

import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.render.drawCustomBeacon
import com.iq200.heigui.utils.skyblock.LocationUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.SkullBlockEntity
import java.util.concurrent.ConcurrentHashMap

object GdragEggEsp : Module (
    name = "Gdrag Egg Esp",
    description = "Esp for golden dragon egg in crystal hollows",
    category = Category.MINING
) {
    private val color by ColorSetting("Color", Colors.MINECRAFT_AQUA, desc = "Color for Esp")
    private val tickDelay by NumberSetting("Scan Tick", 10, 1, 20, unit = "tick", desc = "Ticks Between Scanning")

    private var scanTicks = 0
    private var posList = ConcurrentHashMap.newKeySet<BlockPos>()
    private const val gdragBase64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTEzYmRmMmQyYjAwNjA1NjA2ODI2ZGY3NmUyMTFlYTI4OGFhMDUwZWRjOWQ3MWNiMDk5ODZjNDg4Y2EwNDExYyJ9fX0="
    private var first = true

    init {
        on<WorldEvent.Load> {
            posList.clear()
            first = true
            scanTicks = 0
        }

        on<TickEvent.Start> {
            if (!enabled || mc.player == null || mc.level == null) return@on

            val currentLocation = LocationUtils.currentArea
            if (currentLocation.name != "CrystalHollows") return@on
            scanTicks++

            if (scanTicks < tickDelay) return@on
            scanTicks = 0
            val player = mc.player ?: return@on
            val level = mc.level ?: return@on

            val playerChunkX = player.chunkPosition().x
            val playerChunkZ = player.chunkPosition().z

            val scanRadius = 16

            for (x in -scanRadius..scanRadius) {
                for (z in -scanRadius..scanRadius) {
                    val chunkX = playerChunkX + x
                    val chunkZ = playerChunkZ + z

                    if (level.hasChunk(chunkX, chunkZ)) {
                        val chunk = level.getChunk(chunkX, chunkZ)

                        for ((pos, blockEntity) in chunk.blockEntities) {
                            if (blockEntity is SkullBlockEntity) {
                                val profile = blockEntity.ownerProfile ?: continue
                                val properties = profile.partialProfile().properties.get("textures")

                                val textureProperty = properties.firstOrNull() ?: continue
                                if (textureProperty.value == gdragBase64) {
                                    if (first) modMessage("Found Egg")
                                    first = false
                                    posList.add(pos)
                                }
                            }
                        }
                    }
                }
            }
        }

        on<RenderEvent.Extract> {
            if (!enabled) return@on
            posList.forEach { it ->
                this.drawCustomBeacon("gdrag egg", it, color, true, false)
            }
        }
    }
}