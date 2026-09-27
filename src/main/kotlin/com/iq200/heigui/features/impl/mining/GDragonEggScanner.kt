package com.iq200.heigui.features.impl.mining

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.createSoundSettings
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.playSoundSettings
import com.iq200.heigui.utils.render.drawBeaconBeam
import com.iq200.heigui.utils.render.drawCustomBeacon
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.render.drawText
import com.iq200.heigui.utils.render.drawTracer
import com.iq200.heigui.utils.render.textDim
import com.iq200.heigui.utils.skyblock.Island
import com.iq200.heigui.utils.skyblock.LocationUtils
import com.iq200.heigui.utils.toBlockPos
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

object GDragonEggScanner : Module(
    name = "GDrag Egg Esp",
    description = "Esp for Gdrag Egg in Crystal Hollows",
    category = Category.MINING
) {

    private val eggColor by ColorSetting("Egg Color", Colors.MINECRAFT_GOLD, desc = "Color for Eggs")
    private val scanDelay by NumberSetting("Scan Delay", 2, 1, 20, 1, desc = "Delay Between Each Scan", unit = "tick")
    private val tracer by BooleanSetting("Tracer", false, desc = "Tracer to Eggs")
    private val tracerColor by ColorSetting("Tracer Color", Colors.MINECRAFT_GOLD, desc = "Color for Tracer").withDependency { tracer }
    private val structureFinder by BooleanSetting("Structure Finder", false, desc = "Find Lair Structure")
    private val textScale by NumberSetting("Text Scale", 1, 0, 20, 1, desc = "Text Scale for Structure Title").withDependency { structureFinder }
    private val structureColor by ColorSetting("Structure Color", Colors.MINECRAFT_RED, desc = "Color for Structure Beam").withDependency { structureFinder }
    private val sound = createSoundSettings("Sound", "entity.experience_orb.pickup") { structureFinder }

    private val GDragBase64 = "ewogICJ0aW1lc3RhbXAiIDogMTYyMDM1MDExNzgyMiwKICAicHJvZmlsZUlkIiA6ICJkMGI4MjE1OThmMTE0NzI1ODBmNmNiZTliOGUxYmU3MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJqYmFydHl5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzExM2JkZjJkMmIwMDYwNTYwNjgyNmRmNzZlMjExZWEyODhhYTA1MGVkYzlkNzFjYjA5OTg2YzQ4OGNhMDQxMWMiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="
    private val EyeBase64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDA5MjVjNDhiMDU2NjI4NDhlYzlmMDY4NWY4NThkODg5ZDNkYTExYjA3MTc4OGVhYTM2Y2NkOGYxZjMxZGUifX19"

    var eggCount = 0
    var inCrystalHollows = false

    private val eggBoxes = ConcurrentHashMap.newKeySet<AABB>()
    private var eyeBox : AABB ?= null
    private var scanTimer = 0
    private var foundLair = false


    private val hud by HUD("HUD", desc = "Display Gdrag Egg Count in Current Lobby", false) { example ->
        if (example) {
            return@HUD textDim("§6Gdrag: §7Scanning...", 0, 0, Colors.WHITE)
        }


        if (!inCrystalHollows) {
            return@HUD 0 to 0
        }


        if (eggCount == 0) {
            if (eyeBox == null) return@HUD textDim("§6Gdrag: §7Scanning...", 0, 0, Colors.WHITE)
            else return@HUD textDim("§6Gdrag: §c0/§a3", 0, 0, Colors.WHITE)
        }


        val countColor = when (eggCount) {
            1 -> "§c"
            2 -> "§b"
            else -> "§a"
        }


        return@HUD textDim("§6Gdrag: $countColor$eggCount§7/§a3", 0, 0, Colors.WHITE)
    }

    init {
        on<TickEvent.Start> {
            if (LocationUtils.currentArea != Island.CrystalHollows) {
                inCrystalHollows = false
                return@on
            }

            inCrystalHollows = true
            scanTimer++
            if (scanTimer >= scanDelay) {
                scanTimer = 0
                scanForGdragEggs()
            }
        }

        on<RenderEvent.Extract> {
            if (!inCrystalHollows) return@on

            for (box in eggBoxes) {
                val centerX = (box.minX + box.maxX) / 2
                val centerY = (box.minY + box.maxY) / 2
                val centerZ = (box.minZ + box.maxZ) / 2
                val centerVec = net.minecraft.world.phys.Vec3(centerX, centerY, centerZ)

                val fillCol = Color(eggColor.red, eggColor.green, eggColor.blue, 80)
                val outCol = Color(eggColor.red, eggColor.green, eggColor.blue, 255)

                drawStyledBox(
                    aabb = box,
                    color = fillCol,
                    style = 0,
                    depth = false
                )


                drawStyledBox(
                    aabb = box,
                    color = outCol,
                    style = 2,
                    depth = false
                )


                if (tracer) {
                    drawTracer(
                        centerVec,
                        tracerColor,
                        false,
                        2.0f
                    )
                }
            }

            if (structureFinder && eyeBox != null) {
                val eBox = eyeBox!!

                val centerX = (eBox.minX + eBox.maxX) / 2
                val centerY = (eBox.minY + eBox.maxY) / 2
                val centerZ = (eBox.minZ + eBox.maxZ) / 2
                val centerVec = Vec3(centerX, centerY, centerZ)


                drawCustomBeacon(
                    title = "§6Dragon's Lair",
                    position = centerVec.toBlockPos(),
                    color = structureColor,
                    increase = true,
                    distance = false,
                    scale = textScale.toFloat()
                )
            }
        }

        on<WorldEvent.Load> {
            foundLair = false
        }

    }



    private fun scanForGdragEggs() {
        val level = mc.level ?: return
        val player = mc.player ?: return

        val newBoxes = mutableSetOf<AABB>()
        var newEyeBox: AABB? = null

        // ==========================================

        // ==========================================

        val pChunkX = player.chunkPosition().x
        val pChunkZ = player.chunkPosition().z
        val renderDistance = mc.options.renderDistance().get()


        for (x in (pChunkX - renderDistance)..(pChunkX + renderDistance)) {
            for (z in (pChunkZ - renderDistance)..(pChunkZ + renderDistance)) {

                if (level.hasChunk(x, z)) {
                    val chunk = level.getChunk(x, z)


                    chunk.blockEntities.forEach { (pos, blockEntity) ->
                        if (blockEntity is SkullBlockEntity) {
                            val profileComponent = blockEntity.ownerProfile
                            var blockTexture: String ?= null

                            if (profileComponent != null) {
                                val gameProfile = profileComponent.partialProfile()
                                val textureProperty = gameProfile.properties.get("textures").firstOrNull()
                                blockTexture = textureProperty?.value
                            }


                            if (blockTexture == GDragBase64) {

                                val box = AABB(
                                    pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                                    pos.x.toDouble() + 1.0, pos.y.toDouble() + 1.0, pos.z.toDouble() + 1.0
                                )
                                newBoxes.add(box)
                            }
                            else if (newEyeBox == null && blockTexture == EyeBase64) {
                                val box = AABB(
                                    pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                                    pos.x.toDouble() + 1.0, pos.y.toDouble() + 1.0, pos.z.toDouble() + 1.0
                                )
                                newEyeBox = box
                            }
                        }
                    }
                }
            }
        }

        // ==========================================

        // ==========================================
        eggBoxes.clear()
        eggBoxes.addAll(newBoxes)
        eyeBox = newEyeBox


        eggCount = eggBoxes.size

        if (structureFinder && eyeBox != null) {
            if (!foundLair) {
                playSoundSettings(sound())
                foundLair = true
            }
        } else {

            foundLair = false
        }

    }
}