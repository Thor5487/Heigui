package com.iq200.heigui.utils

import com.iq200.heigui.Heigui.mc
import net.minecraft.world.scores.DisplaySlot

fun getScoreboardLines(): List<String> {
    // 這裡使用 mc.theWorld，如果你們的框架有自訂 mc.level 屬性，請自行替換
    val scoreboard = mc.level?.scoreboard ?: return emptyList()

    // 1 代表右側的 Sidebar
    val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()

    val entries = scoreboard.listPlayerScores(objective)
        .filter { !it.owner.startsWith("#") }
        .sortedBy { it.value }

    // 3. Minecraft 計分板最多顯示 15 行
    val maxEntries = if (entries.size > 15) entries.takeLast(15) else entries

    // 4. 將分數條目與 Team 資訊組裝成完整字串
    return maxEntries.map { entry ->
        val team = scoreboard.getPlayersTeam(entry.owner)

        // 在高版本中，Hypixel 依然習慣將顏色與文字放在 Team 的 Prefix 和 Suffix 中
        // .string 可以將 Component 轉換為不帶顏色代碼的純文字，這會直接滿足你後續的文字比對需求
        val prefix = team?.playerPrefix?.string ?: ""
        val suffix = team?.playerSuffix?.string ?: ""

        // 如果你需要保留顏色碼，可以改用 .visualOrderText 或尋找其他的 Component 擴充方法
        prefix + entry.owner + suffix
    }.reversed()
}