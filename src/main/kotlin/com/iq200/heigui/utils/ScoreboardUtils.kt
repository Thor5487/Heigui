package com.iq200.heigui.utils

import com.iq200.heigui.Heigui.mc
import net.minecraft.world.scores.DisplaySlot

fun getScoreboardLines(): List<String> {

    val scoreboard = mc.level?.scoreboard ?: return emptyList()


    val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()

    val entries = scoreboard.listPlayerScores(objective)
        .filter { !it.owner.startsWith("#") }
        .sortedBy { it.value }


    val maxEntries = if (entries.size > 15) entries.takeLast(15) else entries


    return maxEntries.map { entry ->
        val team = scoreboard.getPlayersTeam(entry.owner)



        val prefix = team?.playerPrefix?.string ?: ""
        val suffix = team?.playerSuffix?.string ?: ""


        prefix + entry.owner + suffix
    }.reversed()
}