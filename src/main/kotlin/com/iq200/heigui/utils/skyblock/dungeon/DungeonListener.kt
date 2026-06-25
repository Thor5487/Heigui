package com.iq200.heigui.utils.skyblock.dungeon

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.events.core.onReceive
import com.iq200.heigui.utils.noControlCodes
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import kotlin.jvm.optionals.getOrNull

object DungeonListener {
    var floor: Floor? = null
    var inBoss = false
    var dungeonStats = DungeonStats()
    private val princeRegex = Regex("^A Prince falls\\. \\+1 Bonus Score$")
    var dungeonTeammates: ArrayList<DungeonPlayer> = ArrayList(5)
    var dungeonTeammatesNoSelf: List<DungeonPlayer> = ArrayList(4)
    private val deathRegex = Regex("☠ (\\w{1,16}) .* and became a ghost\\.")


    // 用於解析計分板上的樓層資訊
    private val floorRegex = Regex("The Catacombs \\((\\w+)\\)$")

    private fun getBoss(): Boolean = with(mc.player) {
        if (this == null || floor?.floorNumber == null) return false
        when (floor?.floorNumber) {
            1 -> x > -71 && z > -39
            in 2..4 -> x > -39 && z > -39
            in 5..6 -> x > -39 && z > -7
            7 -> x > -7 && z > -7
            else -> false
        }
    }

    init {
        on<TickEvent.End> {
            if (DungeonUtils.inDungeons) inBoss = getBoss()
        }

        on<WorldEvent.Load> {
            dungeonStats = DungeonStats()
            dungeonTeammatesNoSelf = emptyList()
            dungeonTeammates.clear()
            inBoss = false
            floor = null
        }

        // 監聽計分板更新來獲取目前樓層 (例如獲取 "F7" 或 "M7")
        onReceive<ClientboundSetPlayerTeamPacket> {
            val text = parameters.getOrNull()?.let { it.playerPrefix.string.plus(it.playerSuffix.string).noControlCodes } ?: return@onReceive

            floorRegex.find(text)?.groupValues?.get(1)?.let {
                floor = Floor.valueOf(it)
            }
        }

        onReceive<ClientboundPlayerInfoUpdatePacket> {
            val tabListEntries = entries().mapNotNull { it.displayName?.string }.ifEmpty { return@onReceive }
            updateDungeonTeammates(tabListEntries)
            updateDungeonStats(tabListEntries)
        }

        on<ChatPacketEvent> { // 這裡替換成你框架中實際的 Chat 事件
            deathRegex.find(value)?.let { match ->
                val deadPlayerName = match.groupValues[1]
                val targetName = if (deadPlayerName == "You") mc.player?.name?.string else deadPlayerName
                dungeonTeammates.find { it.name == targetName }?.deaths?.inc()
            }

            val message = partyMessageRegex.find(value)?.groupValues?.get(1)?.lowercase() ?: return@on

            when (message) {
                "mimic killed", "mimic slain", "mimic killed!", "mimic dead", "mimic dead!" -> {
                    if (DungeonUtils.isFloor(6, 7)) dungeonStats.mimicKilled = true
                }
                "prince killed", "prince slain", "prince killed!", "prince dead", "prince dead!" -> {
                    dungeonStats.princeKilled = true
                }
            }
        }

        onReceive<ClientboundRemoveEntitiesPacket> {
            DungeonUtils.dungeonTeammates.forEach {
                val id = it.entity?.id ?: return@forEach
                if (entityIds.contains(id)) it.entity = null
            }
        }

        onReceive<ClientboundAddEntityPacket> {
            if (type == EntityType.PLAYER)
                DungeonUtils.dungeonTeammates.find { it.entity == null && it.name == mc.level?.getEntity(id)?.name?.string }?.entity =
                    mc.level?.getEntity(id) as? Player
        }

        onReceive<ClientboundEntityEventPacket> {
            if (!DungeonUtils.isFloor(6, 7) || DungeonUtils.inBoss || DungeonUtils.mimicKilled) return@onReceive
            if (eventId != (3).toByte()) return@onReceive

            val entity = getEntity(mc.level as Level) as? Zombie ?: return@onReceive
            if (!entity.isBaby) return@onReceive

            mimicKilled()
        }

        on<ChatPacketEvent> {
            if (value.matches(princeRegex)) princeKilled()
        }
    }

    private val partyMessageRegex = Regex("^Party > .*?: (.+)$")
    private val secretPercentRegex = Regex("^ Secrets Found: ([\\d.]+)%$")
    private val completedRoomsRegex = Regex("^ Completed Rooms: (\\d+)$")
    private val secretCountRegex = Regex("^ Secrets Found: (\\d+)$")
    private val openedRoomsRegex = Regex("^ Opened Rooms: (\\d+)$")
    private val puzzleCountRegex = Regex("^Puzzles: \\((\\d+)\\)$")
    private val deathsRegex = Regex("^Team Deaths: (\\d+)$")
    private val cryptRegex = Regex("^ Crypts: (\\d+)$")
    private val timeRegex = Regex("^ Time: ((?:\\d+h ?)?(?:\\d+m ?)?\\d+s)$")


    data class DungeonStats(
        var secretsFound: Int = 0,
        var secretsPercent: Float = 0f,
        var knownSecrets: Int = 0,
        var crypts: Int = 0,
        var openedRooms: Int = 0,
        var completedRooms: Int = 0,
        var deaths: Int = 0,
        var percentCleared: Int = 0,
        var elapsedTime: String = "0s",
        private var _mimicKilled: Boolean = false,
        var princeKilled: Boolean = false,
        var doorOpener: String = "Unknown",
        var bloodDone: Boolean = false,
        var puzzleCount: Int = 0,
    ) {
        var mimicKilled: Boolean
            get() = _mimicKilled
            set(value) {
                if (value && !DungeonUtils.isFloor(6, 7)) {
                    error("Attempted to set mimicKilled = true on floor that has no mimic")
                }
                _mimicKilled = value
            }
    }


    private fun updateDungeonStats(text: List<String>) {
        for (entry in text) {
            with(dungeonStats) {
                secretsPercent = secretPercentRegex.find(entry)?.groupValues?.get(1)?.toFloatOrNull() ?: secretsPercent
                completedRooms = completedRoomsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: completedRooms
                secretsFound = secretCountRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: secretsFound
                openedRooms = openedRoomsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: openedRooms
                puzzleCount = puzzleCountRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: puzzleCount
                deaths = deathsRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: deaths
                crypts = cryptRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: crypts
                elapsedTime = timeRegex.find(entry)?.groupValues?.get(1) ?: elapsedTime
            }
        }
    }

    private fun mimicKilled() {
        if (DungeonUtils.mimicKilled || DungeonUtils.inBoss) return
        dungeonStats.mimicKilled = true
    }

    private fun princeKilled() {
        if (DungeonUtils.princeKilled || !DungeonUtils.inClear) return
        dungeonStats.princeKilled = true
    }

    private fun updateDungeonTeammates(tabList: List<String>) = mc.execute {
        dungeonTeammates = DungeonUtils.getDungeonTeammates(
            dungeonTeammates, tabList)
        dungeonTeammatesNoSelf = dungeonTeammates.filter { it.name != mc.player?.name?.string }

    }
}