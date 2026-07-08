package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.render.textDim
import com.iq200.heigui.utils.skyblock.ActionBarParser
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils

object SkipSecrets : Module (
    name = "Skip Secrets",
    description = "display how many secrets player has skipped in solo clear and how many secrets can player skip in total",
    category = Category.DUNGEON
) {
    private val skippedRoomsMap = mutableMapOf<String, Int>()

    private var currentRoomId: String? = null
    private var lastKnownRoomFound = 0
    private var lastKnownRoomTotal = 0

    private val hud by HUD("Skipped Secrets", "Displays remaining skippable secrets.") { example ->
        if (!DungeonUtils.inDungeons && !example) return@HUD 0 to 0

        if (!example && !isSolo()) return@HUD 0 to 0

        if (DungeonUtils.totalSecrets == 0 && !example) {
            return@HUD textDim("§7Skippable: §eLoading...", 0, 0)
        }

        val maxSkippable = DungeonUtils.totalSecrets - DungeonUtils.idealNeededSecretsAmoount

        // 🌟 直接將 Map 裡面所有的 values 加總，就是目前的總 Skipped 數量！
        val totalSkippedSecrets = skippedRoomsMap.values.sum()


        val color = when {
            totalSkippedSecrets < maxSkippable - 2 -> "§a"
            totalSkippedSecrets <= maxSkippable -> "§e"
            else -> "§c"
        }

        val text = "§7Skippable: $color$totalSkippedSecrets §8/ $maxSkippable"
        return@HUD textDim(text, 0, 0)
    }

    init {
        on<WorldEvent.Load> {
            skippedRoomsMap.clear()
            currentRoomId = null
            lastKnownRoomFound = 0
            lastKnownRoomTotal = 0
        }

        on<TickEvent.End> {
            if (!DungeonUtils.inDungeons || !isSolo()) return@on

            val currentRoom = ScanUtils.currentRoom ?: return@on
            val currentRoomTotalSecrets = currentRoom.data.secrets
            val currentRoomName = currentRoom.data.name

            if (currentRoomName != currentRoomId) {
                if (currentRoomId != null) {
                    val skippedInLastRoom = lastKnownRoomTotal - lastKnownRoomFound

                    if (skippedInLastRoom > 0) {
                        skippedRoomsMap[currentRoomId!!] = skippedInLastRoom
                    } else {
                        skippedRoomsMap.remove(currentRoomId!!)
                    }
                }

                currentRoomId = currentRoomName

                lastKnownRoomFound = 0
                lastKnownRoomTotal = 0
            }
            else {
                if (ActionBarParser.maxSecrets == currentRoomTotalSecrets && currentRoomTotalSecrets > 0) {
                    lastKnownRoomFound = ActionBarParser.currentSecrets
                    lastKnownRoomTotal = ActionBarParser.maxSecrets

                    // 🌟 新增機制：如果是第二次回來的房間 (本來就存在於 Map 中)，隨時即時更新數值！
                    if (skippedRoomsMap.containsKey(currentRoomName) || lastKnownRoomFound > 0) {
                        val currentSkipped = lastKnownRoomTotal - lastKnownRoomFound
                        if (currentSkipped > 0) {
                            skippedRoomsMap[currentRoomName] = currentSkipped
                        } else {
                            // 如果回頭把這間完全清乾淨了 (變為 0)，立刻從 Map 拔除，減少已跳過計數
                            skippedRoomsMap.remove(currentRoomName)
                        }
                    }
                }
                // 🌟 如果地圖掃描器明確表示這間房根本沒有 Secret (例如 Miniboss 房)
                else if (currentRoomTotalSecrets == 0) {
                    lastKnownRoomFound = 0
                    lastKnownRoomTotal = 0
                    // 確保絕對不會有髒資料殘留在這間房
                    skippedRoomsMap.remove(currentRoomName)
                }
            }

        }
    }


    private fun isSolo(): Boolean {
        if (!DungeonUtils.inDungeons) return false

        return DungeonUtils.dungeonTeammates.size <= 1
    }
}