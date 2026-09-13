package com.iq200.heigui.features.impl.general

import com.google.gson.JsonParser
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.config.BuildConfig
import com.iq200.heigui.events.ChatPacketEvent
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
    private val checkAction by BooleanSetting("Check Actions", false, desc = "Check Updates for Actions")

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
                checkForUpdates() // 統一呼叫一個檢查函數
                hasChecked = true
            }
        }
    }

    private fun checkForUpdates() {
        thread(start = true) {
            try {
                // ==========================================
                // 第一階段：優先檢查 Release (正式版)
                // ==========================================
                val releaseUrl = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val releaseConn = releaseUrl.openConnection() as HttpURLConnection
                releaseConn.requestMethod = "GET"
                releaseConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                releaseConn.connectTimeout = 5000
                releaseConn.readTimeout = 5000

                var hasReleaseUpdate = false

                if (releaseConn.responseCode == 200) {
                    val reader = InputStreamReader(releaseConn.inputStream)
                    val jsonObject = JsonParser.parseReader(reader).asJsonObject

                    val latestReleaseVersion = jsonObject.get("tag_name").asString
                    val releaseHtmlUrl = jsonObject.get("html_url").asString
                    reader.close()

                    if (isUpdateAvailable(CURRENT_VERSION, latestReleaseVersion)) {
                        mc.execute { sendUpdateMessage(latestReleaseVersion, releaseHtmlUrl, false) }
                        hasReleaseUpdate = true // 標記已有正式版更新，後續不需再查 Action
                    }
                }

                // ==========================================
                // 第二階段：如果沒有正式版更新，且玩家開啟了 Action 檢查，再去查 Action
                // ==========================================
                if (!hasReleaseUpdate && checkAction) {
                    val runUrl = URL("https://api.github.com/repos/$GITHUB_REPO/actions/runs?branch=main&status=success&per_page=1")
                    val runConn = runUrl.openConnection() as HttpURLConnection
                    runConn.requestMethod = "GET"
                    runConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    runConn.connectTimeout = 5000
                    runConn.readTimeout = 5000

                    if (runConn.responseCode == 200) {
                        val runReader = InputStreamReader(runConn.inputStream)
                        val runJson = JsonParser.parseReader(runReader).asJsonObject
                        val runs = runJson.getAsJsonArray("workflow_runs")
                        runReader.close()

                        if (runs.size() > 0) {
                            val latestRun = runs.get(0).asJsonObject
                            val runId = latestRun.get("id").asLong
                            val shortSha = latestRun.getAsJsonObject("head_commit").get("id").asString.take(7)
                            val actionUrl = latestRun.get("html_url").asString

                            val localHash = BuildConfig.commit.take(7)
                            // 注意這裡改回 BuildConfig.commitHash 以符合你之前的設定
                            if (localHash.isNotEmpty() && localHash != shortSha && localHash != "unknown") {

                                // 呼叫 Artifacts API 抓詳細檔名的邏輯
                                val artifactsUrl = URL("https://api.github.com/repos/$GITHUB_REPO/actions/runs/$runId/artifacts")
                                val artConn = artifactsUrl.openConnection() as HttpURLConnection
                                artConn.requestMethod = "GET"
                                artConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                                artConn.connectTimeout = 5000
                                artConn.readTimeout = 5000

                                var latestActionVersion = "unknown-beta ($shortSha)"

                                if (artConn.responseCode == 200) {
                                    val artReader = InputStreamReader(artConn.inputStream)
                                    val artJson = JsonParser.parseReader(artReader).asJsonObject
                                    val artifacts = artJson.getAsJsonArray("artifacts")
                                    artReader.close()

                                    if (artifacts.size() > 0) {
                                        val artifactName = artifacts.get(0).asJsonObject.get("name").asString
                                        val versionRegex = Regex("""\d+\.\d+\.\d+-beta\.\d+""")
                                        val matchResult = versionRegex.find(artifactName)
                                        if (matchResult != null) {
                                            latestActionVersion = matchResult.value
                                        }
                                    }
                                }

                                mc.execute { sendUpdateMessage(latestActionVersion, actionUrl, true) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
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

    private fun sendUpdateMessage(latestVersion: String, url: String, isAction: Boolean) {
        // 建立可點擊的 Component
        val linkText = if (isAction) "§b§n[Open Action Page]" else "§b§n[Click Here to Download]"
        val hoverText = if (isAction) "§eOpen GitHub Actions Page" else "§eOpen GitHub Release Page"

        val clickableLink = Component.literal(linkText)
            .withStyle { style ->
                style.withClickEvent(ClickEvent.OpenUrl(URI(url)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(hoverText)))
            }

        val updateType = if (isAction) "action" else "release"

        // 組合完整訊息
        val message = Component.literal("§eA new $updateType is available! §7(§cv$CURRENT_VERSION §7-> §a$latestVersion§7) ")
            .append(clickableLink)

        modMessage(message)
        alert("Heigui Update Available!")
    }
}