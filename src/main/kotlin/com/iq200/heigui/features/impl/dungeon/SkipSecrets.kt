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


                    if (skippedRoomsMap.containsKey(currentRoomName) || lastKnownRoomFound > 0) {
                        val currentSkipped = lastKnownRoomTotal - lastKnownRoomFound
                        if (currentSkipped > 0) {
                            skippedRoomsMap[currentRoomName] = currentSkipped
                        } else {

                            skippedRoomsMap.remove(currentRoomName)
                        }
                    }
                }

                else if (currentRoomTotalSecrets == 0) {
                    lastKnownRoomFound = 0
                    lastKnownRoomTotal = 0

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