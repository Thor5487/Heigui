package com.iq200.heigui.features.impl.skyblock

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.ActionSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.PlayerInputEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.getScoreboardLines
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.render.text
import com.iq200.heigui.utils.render.textDim
import com.iq200.heigui.utils.texture
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import kotlin.concurrent.thread


data class TrackerData(
    var bossesKilled: Int = 0,
    var totalUptimeMs: Long = 0L,
    var loots: MutableMap<String, Int> = mutableMapOf()
)


object VampireTracker: Module(
    name = "Vampire Tracker",
    description = "Track Vampire Kills and Loots",
    category = Category.SKYBLOCK
) {
    private val afkTimeout by NumberSetting(
        "AFK Timeout",
        30,
        5,
        120,
        1,
        unit = "s",
        desc = "Pause uptime after certain amount of time without activity"
    )


    private var bossesKilled = 0
    private var totalUptimeMs = 0L

    private var loots: MutableMap<String, Int> = mutableMapOf()

    private var lastTickTime = 0L
    private var lastActivityTime = 0L
    private var lastSaveTime = 0L
    private var lastKills = -1
    private var wasBossSpawned = false

    private var isPaused = false
    private var currentBph = 0.0

    private var wasQuestActive = false
    private var isTaskActiveOnBoard = false
    private val trackerConfig = JsonConfig(
        "vampire_tracker.json",
        object : TypeToken<TrackerData>() {}.type,
        { TrackerData() }
    )

    private val trackerHud by HUD("Tracker Hud", "Show Session Tracker") { it ->
        if (!it && !isTaskActiveOnBoard) return@HUD 0 to 0

        val displayKills = if (it && totalUptimeMs == 0L) 8 else bossesKilled
        val displayUptime = if (it && totalUptimeMs == 0L) "30m 26s" else {
            formatUptime(totalUptimeMs) + if (isPaused) " §c(Paused)" else ""
        }
        val displayBph = if (it && totalUptimeMs == 0L) "15.8" else String.format("%.1f", currentBph)

        var y = 0
        var maxWidth = textDim("§eRiftstalker Bloodfiend Tracker", 0, y, Colors.WHITE).first

        val displayLoots = if (it && loots.isEmpty()) {
            mapOf(
                "Quantum III" to 1,
                "The One IV" to 2
            )
        } else {
            loots
        }

        val sortedLoots = displayLoots.entries.sortedByDescending { getLootPriority(it.key) }

        // 遍歷所有記錄的掉落物並畫在 HUD 上
        for ((itemName, count) in sortedLoots) {
            y += 10

            val itemComp = createColoredComponent(itemName)

            val fullComponent = Component.empty()
                .append(" ").withStyle(ChatFormatting.RESET)
                .append(itemComp)
                .append(Component.literal(": $count").withStyle(ChatFormatting.GRAY))

            // 渲染
            text(fullComponent, 0, y, Colors.WHITE)

            val w = mc.font.width(fullComponent)
            if (w > maxWidth) maxWidth = w
        }

        text("§7Bosses killed: §f$displayKills", 0, y + 10, Colors.WHITE)
        text("§eBosses Per Hour: §6$displayBph", 0, y + 20, Colors.WHITE)
        text("§eTotal Uptime: §b$displayUptime", 0, y + 30, Colors.WHITE)

        y += 40


        maxWidth to y
    }
    private val resetAction by ActionSetting(
        "Reset Tracker",
        "Reset all kills, uptime, and loots"
    ) {
        resetTracker()
    }.withDependency { trackerHud.enabled }


    init {
        loadTrackerData()

        on<TickEvent.Start> { // 或是 TickEvent.Start，依據你的架構
            if (mc.player == null || mc.level == null) {
                lastTickTime = 0L
                return@on
            }

            val now = System.currentTimeMillis()
            if (lastTickTime == 0L) {
                lastTickTime = now
                lastActivityTime = now
            }
            val dt = now - lastTickTime
            lastTickTime = now

            val lines = getScoreboardLines()

            var currentlyHasQuest = false // 本回合是否在計分板找到任務
            var currentKills = -1
            var currentlyBossSpawned = false

            // --- 解析計分板 ---
            for (i in lines.indices) {
                val cleanLine = lines[i].noControlCodes.trim()

                if (cleanLine == "Slayer Quest") {
                    // 往下找 1~3 行確認是不是 Bloodfiend 以及進度
                    for (j in 1..3) {
                        if (i + j >= lines.size) break
                        val subLine = lines[i + j].noControlCodes.trim()

                        // 👇 確認任務是 Riftstalker Bloodfiend
                        if (subLine.contains("Riftstalker Bloodfiend")) {
                            currentlyHasQuest = true
                        }

                        // 確認狀態
                        if (subLine.contains("Slay the boss!")) {
                            currentlyBossSpawned = true
                        } else {
                            val match = Regex("(\\d+)/\\d+").find(subLine)
                            if (match != null) {
                                currentKills = match.groupValues[1].toInt()
                            }
                        }
                    }
                    break // 找到 Slayer Quest 區塊就可以跳出迴圈了
                }
            }

            isTaskActiveOnBoard = currentlyHasQuest

            if (currentlyHasQuest) {
                if (!wasQuestActive) {
                    lastActivityTime = now
                    if (currentKills != -1) {
                        lastKills = currentKills
                    }
                } else {
                    if (currentlyBossSpawned) {
                        lastActivityTime = now
                    } else if (currentKills != -1 && currentKills != lastKills) {
                        lastActivityTime = now
                        lastKills = currentKills
                    }
                }

                val afkTimeoutMillis = afkTimeout * 1000L
                val timeSinceLastActivity = now - lastActivityTime

                if (timeSinceLastActivity <= afkTimeoutMillis) {
                    totalUptimeMs += dt
                    isPaused = false
                } else {
                    isPaused = true
                }

                if (wasBossSpawned && !currentlyBossSpawned) {
                    bossesKilled++
                    lastKills = currentKills

                    val hours = totalUptimeMs / 3600000.0
                    if (hours > 0) {
                        currentBph = bossesKilled / hours
                    }

                    saveTrackerData()
                }
                wasBossSpawned = currentlyBossSpawned
                wasQuestActive = true
            } else {
                wasBossSpawned = false
                wasQuestActive = false

                if (totalUptimeMs > 0L) {
                    isPaused = true
                }
            }

            if (!isPaused && now - lastSaveTime >= 5000L) {
                saveTrackerData()
                lastSaveTime = now
            }
        }

        on<ChatPacketEvent> {
            if (totalUptimeMs == 0L || !isTaskActiveOnBoard) return@on

            val cleanMsg = component.string
            if (!cleanMsg.contains("DROP!")) return@on

            var itemName: String? = null

            // 特殊處理：這兩本書必須強制定色（依據你的指示：第二個是 Quantum III，最後一個是 The One IV）
            if (cleanMsg.contains("DROP!") && cleanMsg.contains("(Enchanted Book Bundle)")) {
                var isGold = false

                // 拆解整句聊天訊息，尋找包含 "Enchanted Book Bundle" 的片段
                for (part in component.toFlatList()) {
                    if (part.string.contains("Enchanted Book Bundle")) {

                        // 取得該片段的顏色
                        val textColor = part.style.color

                        // 比對顏色值是否等於 ChatFormatting.GOLD (整數值)
                        if (textColor != null && textColor.value == ChatFormatting.GOLD.color) {
                            isGold = true
                        }

                        break // 找到了就跳出迴圈，不用繼續往下找
                    }
                }

                if (isGold) {
                    itemName = "The One IV" // 金色書是 The One IV
                } else {
                    itemName = "Quantum III" // 其他顏色 (例如藍色) 則是 Quantum III
                }
            }
            else if (cleanMsg.contains("(")) {
                val dropIndex = cleanMsg.indexOf("DROP!")
                val startIndex = cleanMsg.indexOf('(', dropIndex) + 1
                val endIndex = cleanMsg.indexOf(')', startIndex)

                if (startIndex in 1..<endIndex) {
                    val extracted = Component.empty()
                    var currentIndex = 0

                    for (part in component.toFlatList()) {
                        val partText = part.string
                        val partLen = partText.length
                        val partStart = currentIndex
                        val partEnd = currentIndex + partLen

                        if (partEnd > startIndex) {
                            val overlapStart = maxOf(0, startIndex - partStart)
                            val overlapEnd = minOf(partLen, endIndex - partStart)

                            val subText = partText.substring(overlapStart, overlapEnd)
                            extracted.append(Component.literal(subText))
                        }

                        currentIndex += partLen
                        if (currentIndex >= endIndex) break
                    }

                    val rawName = extracted.string.trim()
                    if (rawName.isNotBlank()) {
                        itemName = rawName
                    }
                }
            }

            // 3. 寫入以 String 為 Key 的 Map 與 Config
            if (itemName != null) {
                loots[itemName] = loots.getOrDefault(itemName, 0) + 1
                saveTrackerData()
            }
        }

    }


    private fun saveTrackerData() {
        // 先複製目前的狀態，避免在多執行緒環境下讀寫同一個 Map 導致 ConcurrentModificationException
        val currentKills = bossesKilled
        val currentUptime = totalUptimeMs
        val currentLoots = loots.toMutableMap()

        // 放到背景執行，防止卡頓主執行緒
        thread {
            trackerConfig.update { data ->
                data.bossesKilled = currentKills
                data.totalUptimeMs = currentUptime
                data.loots = currentLoots
            }
        }
    }

    private fun loadTrackerData() {
        trackerConfig.load()
        val data = trackerConfig.data

        bossesKilled = data.bossesKilled
        totalUptimeMs = data.totalUptimeMs
        loots = data.loots.toMutableMap()

        val hours = totalUptimeMs / 3600000.0
        if (hours > 0) {
            currentBph = bossesKilled / hours
        }
    }

    fun resetTracker() {
        bossesKilled = 0
        totalUptimeMs = 0L
        lastTickTime = 0L
        lastActivityTime = 0L
        lastSaveTime = System.currentTimeMillis()
        currentBph = 0.0
        loots.clear()

        wasBossSpawned = false
        isPaused = true
        wasQuestActive = false

        saveTrackerData()
    }


    private fun formatUptime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60

        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0 || h > 0) append("${m}m ")
            append("${s}s")
        }.trim()
    }

    private fun createColoredComponent(name: String): Component {
        return when {
            name.contains("Sangria Dye") -> Component.literal(name).withStyle(ChatFormatting.DARK_RED)
            name.contains("The One IV") -> Component.literal(name).withStyle(ChatFormatting.GOLD)
            name.contains("Unfanged Vampire Part") -> Component.literal(name).withStyle(ChatFormatting.GOLD)
            name.contains("McGrubber's Burger") -> Component.literal(name).withStyle(ChatFormatting.DARK_PURPLE)
            name.contains("Guardian Lucky Block") -> Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE)
            name.contains("Fang-tastic Chocolate Chip") -> Component.literal(name).withStyle(ChatFormatting.GOLD)
            name.contains("Bubba Blister") -> Component.literal(name).withStyle(ChatFormatting.GOLD)
            name.contains("Soultwist Rune I") -> Component.literal(name).withStyle(ChatFormatting.DARK_PURPLE)
            name.contains("Quantum III") -> Component.literal(name).withStyle(ChatFormatting.GREEN)
            else -> Component.literal(name).withStyle(ChatFormatting.WHITE)
        }
    }

    fun getLootPriority(name: String): Int {
        return when {
            name.contains("Sangria Dye") -> 900
            name.contains("The One IV") -> 800
            name.contains("Unfanged Vampire Part") -> 700
            name.contains("McGrubber's Burger") -> 600
            name.contains("Guardian Lucky Block") -> 500
            name.contains("Fang-tastic Chocolate Chip") -> 400
            name.contains("Bubba Blister") -> 300
            name.contains("Soultwist Rune I") -> 200
            name.contains("Quantum III") -> 100
            else -> 10
        }
    }
}