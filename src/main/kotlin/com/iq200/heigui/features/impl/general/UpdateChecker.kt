package com.iq200.heigui.features.impl.general

import com.google.gson.JsonParser
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.config.BuildConfig
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.alert
import com.iq200.heigui.utils.modMessage
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.concurrent.thread

object UpdateChecker : Module(
    name = "Update Checker",
    description = "Check Update on Opening Game",
    category = Category.GENERAL
) {
    private const val GITHUB_REPO = "Thor5487/Heigui"
    private val profileRegex = Regex("Profile ID:\\s*(.{36})")

    // 正式版是 "1.3.9"，測試版是 "1.3.9-beta.5+2c1a205" (由 build.gradle.kts 依 git tag 決定)
    // ⚠️ GitHub Release 的 Tag 必須以數字版本開頭 (例如 "v1.3.9")，比對才能正確進行
    val CURRENT_VERSION = FabricLoader.getInstance()
        .getModContainer("heigui")
        .map { it.metadata.version.friendlyString }
        .orElse("1.0.0")

    // 確保每開啟一次遊戲只會檢查一次，避免每次換地圖都跳通知
    private var hasChecked = false

    init {
        on<ChatPacketEvent> {
            if (!profileRegex.matches(value)) return@on

            if (!hasChecked) {
                checkForUpdates()
                hasChecked = true
            }
        }
    }

    private fun checkForUpdates() {
        // 開啟一個新的背景執行緒，避免卡死 Minecraft 主執行緒
        thread(start = true) {
            try {
                // 呼叫 GitHub API 取得最新 Release 的資料
                val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 5000 // 連線超時 5 秒
                connection.readTimeout = 5000    // 讀取超時 5 秒

                if (connection.responseCode == 200) {
                    val reader = InputStreamReader(connection.inputStream)
                    val jsonObject = JsonParser.parseReader(reader).asJsonObject

                    // 取得 GitHub 上的 Tag 與 網址
                    val latestVersion = jsonObject.get("tag_name").asString
                    val releaseUrl = jsonObject.get("html_url").asString

                    reader.close()

                    // 比對版本號
                    if (isUpdateAvailable(CURRENT_VERSION, latestVersion)) {
                        // 切回 Minecraft 主執行緒發送訊息 (避免跨執行緒操作 GUI 報錯)
                        mc.execute {
                            sendUpdateMessage(latestVersion, releaseUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                // 如果沒有網路或 API 限制，默默失敗就好，不要拿報錯洗玩家的畫面
                e.printStackTrace()
            }
        }
    }

    // 只抓開頭的數字版本段，後面的 prerelease / build metadata 交給 isPrerelease 處理
    private val versionNumberRegex = Regex("""^\d+(?:\.\d+)*""")

    private class ParsedVersion(val numbers: List<Int>, val isPrerelease: Boolean)

    private fun isUpdateAvailable(current: String, latest: String): Boolean {
        // 解析不出來就不通知，避免奇怪的 Tag 名稱洗玩家畫面
        val currentVersion = parseVersion(current) ?: return false
        val latestVersion = parseVersion(latest) ?: return false

        val result = compareVersions(latestVersion.numbers, currentVersion.numbers)

        // 數字段就分出勝負：只有線上嚴格較新才算有更新
        // (本機是開發版、版本號比線上 Release 新的時候就不會誤報)
        if (result != 0) return result > 0

        // 數字段相同，例如本機 1.3.9-beta.5 對上線上正式版 v1.3.9。
        // semver 規定 prerelease 小於正式版，所以這種情況要通知玩家正式版已發布。
        // 不需要比較 beta.5 vs beta.6，因為 releases/latest 本來就會跳過 prerelease。
        return currentVersion.isPrerelease && !latestVersion.isPrerelease
    }

    /**
     * "v1.3.9" -> numbers=[1, 3, 9], isPrerelease=false
     * "1.3.9-beta.5+2c1a205" -> numbers=[1, 3, 9], isPrerelease=true
     * 解析失敗回傳 null
     */
    private fun parseVersion(version: String): ParsedVersion? {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val numeric = versionNumberRegex.find(cleaned)?.value ?: return null
        val numbers = numeric.split('.').map { it.toIntOrNull() ?: 0 }

        // 數字段後面剩下的東西以 "-" 開頭就是 semver 的 prerelease 標記；
        // 正式版剩下空字串，帶 build metadata 的 "+..." 不算 prerelease
        val isPrerelease = cleaned.substring(numeric.length).startsWith("-")

        return ParsedVersion(numbers, isPrerelease)
    }

    /** 逐段比對，長度不同時缺的那段補 0 (1.4 == 1.4.0) */
    private fun compareVersions(left: List<Int>, right: List<Int>): Int {
        for (i in 0 until maxOf(left.size, right.size)) {
            val result = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    private fun sendUpdateMessage(latestVersion: String, url: String) {
        // 建立可點擊的 Component
        val clickableLink = Component.literal("§b§n[Click Here to Download]")
            .withStyle { style ->
                // 修正 1：使用 ClickEvent.OpenUrl，並傳入 URI 物件
                style.withClickEvent(ClickEvent.OpenUrl(URI(url)))
                    // 修正 2：使用 HoverEvent.ShowText，並直接傳入 Component
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("§eOpen GitHub Release Page")))
            }

        // 讓跑測試版的玩家知道自己現在不是正式版
        val channelTag = if (BuildConfig.isBeta) " §6§l[BETA]§r" else ""

        // 組合完整訊息
        val message = Component.literal("§eA new update is available!$channelTag §7(§cv$CURRENT_VERSION §7-> §a$latestVersion§7) ")
            .append(clickableLink)

        modMessage(message)
        alert("Heigui Update Available!")
    }
}