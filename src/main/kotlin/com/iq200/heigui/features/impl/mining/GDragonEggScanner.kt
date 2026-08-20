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

        // 2. 如果不在水晶洞 (Crystal Hollows)，就不顯示 HUD
        if (!inCrystalHollows) {
            return@HUD 0 to 0
        }

        // 3. 在水晶洞裡面，如果還沒找到半顆蛋
        if (eggCount == 0) {
            if (eyeBox == null) return@HUD textDim("§6Gdrag: §7Scanning...", 0, 0, Colors.WHITE)
            else return@HUD textDim("§6Gdrag: §c0/§a3", 0, 0, Colors.WHITE)
        }

        // 4. 根據找到的蛋數量，決定數字的顏色
        val countColor = when (eggCount) {
            1 -> "§c" // 紅色
            2 -> "§b" // 淺藍色 (相比深藍 §9 在深色背景下更容易閱讀)
            else -> "§a" // 3 顆 (或以上) 顯示綠色
        }

        // 輸出結果，例如： Gdrag: 2/3 (淺藍色數字)
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
                // 1. 畫出半透明的內部實心方塊 (金色，透明度 80)
                drawStyledBox(
                    aabb = box,
                    color = fillCol,
                    style = 0,    // 0 通常代表 Filled (實心)
                    depth = false // 🌟 depth = false 代表關閉深度測試，這就是能「隔牆透視」的關鍵！
                )

                // 2. 畫出明顯的外部邊框線 (實心金色，透明度 255)
                drawStyledBox(
                    aabb = box,
                    color = outCol,
                    style = 2,    // 2 通常代表 Outline (外框線)
                    depth = false
                )


                if (tracer) {
                    drawTracer(
                        centerVec,
                        tracerColor, // 金色連線
                        false,
                        2.0f // 線條寬度
                    )
                }
            }

            if (structureFinder && eyeBox != null) {
                val eBox = eyeBox!!
                // 如果你的 AABB 擴充方法有 .center 可以直接用，沒有的話就手動算
                val centerX = (eBox.minX + eBox.maxX) / 2
                val centerY = (eBox.minY + eBox.maxY) / 2
                val centerZ = (eBox.minZ + eBox.maxZ) / 2
                val centerVec = Vec3(centerX, centerY, centerZ)


                drawCustomBeacon(
                    title = "§6Dragon's Lair",
                    position = centerVec.toBlockPos(),
                    color = structureColor,
                    increase = true,     // 因為你是用固定的 textScale，所以這裡設為 false
                    distance = false,     // 如果你原本不需要顯示 "(25m)" 這種距離，這裡設為 false
                    scale = textScale.toFloat() // 帶入你自訂的字體大小
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
        // 搜尋：檢查地上的方塊 (SkullBlockEntity)
        // ==========================================
        // 取得玩家目前的區塊座標
        val pChunkX = player.chunkPosition().x
        val pChunkZ = player.chunkPosition().z
        val renderDistance = mc.options.renderDistance().get()

        // 掃描玩家渲染距離內的所有區塊 (Chunk)
        for (x in (pChunkX - renderDistance)..(pChunkX + renderDistance)) {
            for (z in (pChunkZ - renderDistance)..(pChunkZ + renderDistance)) {
                // 確保區塊已加載，避免引發地圖讀取卡頓
                if (level.hasChunk(x, z)) {
                    val chunk = level.getChunk(x, z)

                    // 檢查區塊內所有的 BlockEntity
                    chunk.blockEntities.forEach { (pos, blockEntity) ->
                        if (blockEntity is SkullBlockEntity) {
                            val profileComponent = blockEntity.ownerProfile
                            var blockTexture: String ?= null

                            if (profileComponent != null) {
                                val gameProfile = profileComponent.partialProfile()
                                val textureProperty = gameProfile.properties.get("textures").firstOrNull()
                                blockTexture = textureProperty?.value
                            }

                            // 發現是 Gdrag 蛋！
                            if (blockTexture == GDragBase64) {
                                // 建立一個 1x1x1 的方塊大小的 Box 準備畫透視框
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
        // 結算與更新
        // ==========================================
        eggBoxes.clear()
        eggBoxes.addAll(newBoxes)
        eyeBox = newEyeBox

        // 更新計數器給 HUD 用
        eggCount = eggBoxes.size

        if (structureFinder && eyeBox != null) {
            if (!foundLair) {
                playSoundSettings(sound())
                foundLair = true
            }
        } else {
            // 🌟 當 eyeBox 離開視線變回 null 時，重置提醒開關
            foundLair = false
        }

    }
}