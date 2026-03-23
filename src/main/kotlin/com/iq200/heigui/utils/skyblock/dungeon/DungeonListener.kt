package com.iq200.heigui.utils.skyblock.dungeon

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.events.core.onReceive
import com.iq200.heigui.utils.noControlCodes
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import kotlin.jvm.optionals.getOrNull

object DungeonListener {

    var floor: Floor? = null
    var inBoss = false

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
    }
}