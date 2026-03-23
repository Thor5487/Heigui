package com.iq200.heigui.utils.skyblock

import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.utils.skyblock.dungeon.DungeonListener

data class Split(val regex: Regex, val name: String, var time: Long = 0L, var ticks: Long = 0L)
data class SplitsGroup(val splits: List<Split>)

object SplitsManager {

    var currentSplits: SplitsGroup = SplitsGroup(emptyList())
    private var tickCounter: Long = 0L

    init {
        // 監聽聊天訊息來觸發計時點
        on<ChatPacketEvent> {
            if (value == "Starting in 1 second.") {
                tickCounter = 0L
                currentSplits = when (LocationUtils.currentArea) {
                    Island.Dungeon -> {
                        val floor = DungeonListener.floor ?: return@on
                        with(dungeonSplits[floor.floorNumber].toMutableList()) {
                            addAll(0, listOf(
                                Split(MORT_REGEX, "§2Blood Open"),
                                Split(BLOOD_OPEN_REGEX, "§bBlood Clear"),
                                Split(Regex("\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\."), "§dPortal Entry")
                            ))
                            add(Split(Regex("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$"), "§1Total"))
                            SplitsGroup(map { it.copy(time = 0L, ticks = 0L) })
                        }
                    }
                    else -> SplitsGroup(emptyList())
                }
            } else {
                val currentSplit = currentSplits.splits.find { it.regex.matches(value) } ?: return@on
                if (currentSplit.time != 0L) return@on

                currentSplit.time = System.currentTimeMillis()
                currentSplit.ticks = tickCounter
                // 這裡不再呼叫任何發送訊息的代碼，僅更新數據
            }
        }

        on<TickEvent.Server> { tickCounter++ }
        on<WorldEvent.Load> {
            currentSplits = SplitsGroup(emptyList())
            tickCounter = 0L
        }
    }

    /**
     * 計算並回傳目前的分割時間
     */
    fun getAndUpdateSplitsTimes(currentSplits: SplitsGroup): Triple<List<Long>, List<Long>, Int> {
        if (currentSplits.splits.isEmpty() || currentSplits.splits[0].time == 0L)
            return Triple(List(currentSplits.splits.size) { 0L }, List(currentSplits.splits.size) { 0L }, -1)

        val latestTime = if (currentSplits.splits.last().time == 0L) System.currentTimeMillis() else currentSplits.splits.last().time
        val latestTick = if (currentSplits.splits.last().ticks == 0L) tickCounter else currentSplits.splits.last().ticks

        val times = MutableList(currentSplits.splits.size) { 0L }.apply { this[size - 1] = latestTime - currentSplits.splits.first().time }
        val tickTimes = MutableList(currentSplits.splits.size) { 0L }.apply { this[size - 1] = latestTick - currentSplits.splits.first().ticks }

        var current = currentSplits.splits.size
        for (i in 0 until currentSplits.splits.size - 1) {
            if (currentSplits.splits[i + 1].time != 0L) {
                times[i] = currentSplits.splits[i + 1].time - currentSplits.splits[i].time
                tickTimes[i] = currentSplits.splits[i + 1].ticks - currentSplits.splits[i].ticks
            } else {
                current = i
                times[i] = latestTime - currentSplits.splits[i].time
                tickTimes[i] = latestTick - currentSplits.splits[i].ticks
                break
            }
        }
        return Triple(times, tickTimes, current)
    }
}


private val entryRegexes = listOf(
    Regex("^\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.$"),
    Regex("^\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.$"),
    Regex("^\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.$"),
    Regex("^\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!$"),
    Regex("^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$"),
    Regex("^\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!$"),
    Regex("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$")
)

private val entranceSplitGroup = mutableListOf<Split>()

private val floor1SplitGroup = mutableListOf(
    Split(entryRegexes[0], "§cBonzo's Sike"),
    Split(Regex("\\[BOSS] Bonzo: Oh I'm dead!"), "§4Cleared"),
)

private val floor2SplitGroup = mutableListOf(
    Split(entryRegexes[1], "§cScarf's minions"),
    Split(Regex("^\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.$"), "§4Cleared"),
)

private val floor3SplitGroup = mutableListOf(
    Split(entryRegexes[2], "§cThe Guardians"),
    Split(Regex("^\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?$"), "§aThe Professor"),
    Split(Regex("^\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!$"), "§4Cleared"),
)

private val floor4SplitGroup = mutableListOf(
    Split(entryRegexes[3], "§4Cleared"),
)

private val floor5SplitGroup = mutableListOf(
    Split(entryRegexes[4], "§4Cleared"),
)

private val floor6SplitGroup = mutableListOf(
    Split(entryRegexes[5], "§cTerracottas"),
    Split(Regex("^\\[BOSS] Sadan: ENOUGH!$"), "§aGiants"),
    Split(Regex("^\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.$"), "§4Cleared"),
)

private val floor7SplitGroup = mutableListOf(
    Split(entryRegexes[6], "§5Maxor"),
    Split(Regex("\\[BOSS] Storm: Pathetic Maxor, just like expected\\."), "§3Storm"),
    Split(Regex("\\[BOSS] Goldor: Who dares trespass into my domain\\?"), "§6Terminals"),
    Split(Regex("The Core entrance is opening!"), "§7Goldor"),
    Split(Regex("\\[BOSS] Necron: You went further than any human before, congratulations\\."), "§cNecron"),
    Split(Regex("\\[BOSS] Necron: All this, for nothing\\.\\.\\."), "§4Cleared"),
)

private val dungeonSplits = listOf(
    entranceSplitGroup,
    floor1SplitGroup,
    floor2SplitGroup,
    floor3SplitGroup,
    floor4SplitGroup,
    floor5SplitGroup,
    floor6SplitGroup,
    floor7SplitGroup,
)

// https://regex101.com/r/BXKhOI/1
private val BLOOD_OPEN_REGEX = Regex("^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$|^The BLOOD DOOR has been opened!$")
private val MORT_REGEX = Regex("\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.|\\[NPC] Mort: Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!")