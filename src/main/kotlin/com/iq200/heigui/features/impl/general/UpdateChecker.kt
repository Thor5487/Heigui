package com.iq200.heigui.features.impl.general

import com.google.gson.JsonParser
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
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

    // ⚠️ 這個字串必須和你 GitHub Release 的 Tag 名稱格式一致 (例如 "v1.0.0")
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

    private fun isUpdateAvailable(current: String, latest: String): Boolean {
        // 去除版本號可能帶有的 "v" 前綴再進行比對
        val cleanCurrent = current.replace("v", "", ignoreCase = true).trim()
        val cleanLatest = latest.replace("v", "", ignoreCase = true).trim()

        // 最簡單的比對法：只要字串不一樣就當作有更新
        // 如果你需要嚴格比對 (例如 1.0.1 > 1.0.0)，需要把字串用 split(".") 拆成數字陣列來比較
        return cleanCurrent != cleanLatest
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

        // 組合完整訊息
        val message = Component.literal("§eA new update is available! §7(§cv$CURRENT_VERSION §7-> §a$latestVersion§7) ")
            .append(clickableLink)

        modMessage(message)
        alert("Heigui Update Available!")
    }
}