package com.iq200.heigui.utils.skyblock.dungeon

import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.Colors
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerSkin


data class DungeonPlayer(
    val name: String,
    var clazz: DungeonClass,
    var clazzLvl: Int,
    val playerSkin: PlayerSkin?,
    var entity: Player? = null, // 做 ESP (透視外框) 或是判斷距離時會用到
    var isDead: Boolean = false,
    var deaths: Int = 0         // 計算分數時會用到
)

enum class DungeonClass(
    val color: Color,
    val colorCode: Char,
    val defaultQuadrant: Int,
    var priority: Int,
) {
    ARCHER(Colors.MINECRAFT_GOLD, '6', 0, 2),
    BERSERK(Colors.MINECRAFT_DARK_RED, '4', 1, 0),
    HEALER(Colors.MINECRAFT_LIGHT_PURPLE, 'd', 2, 2),
    MAGE(Colors.MINECRAFT_AQUA, 'b', 3, 2),
    TANK(Colors.MINECRAFT_DARK_GREEN, '2', 3, 1),
    EMPTY(Colors.WHITE, 'f', 0, 0)
}


/**
 * Enumeration representing different floors in a dungeon.
 */
enum class Floor(val requiredPercentage: Float = 1f) {
    E(0.3f),
    F1(0.3f),
    F2(0.4f),
    F3(0.5f),
    F4(0.6f),
    F5(0.7f),
    F6(0.85f),
    F7,
    M1,
    M2,
    M3,
    M4,
    M5,
    M6,
    M7;

    inline val floorNumber: Int
        get() {
            return when (this) {
                E -> 0
                F1, M1 -> 1
                F2, M2 -> 2
                F3, M3 -> 3
                F4, M4 -> 4
                F5, M5 -> 5
                F6, M6 -> 6
                F7, M7 -> 7
            }
        }

    inline val isMM: Boolean
        get() {
            return when (this) {
                E, F1, F2, F3, F4, F5, F6, F7 -> false
                M1, M2, M3, M4, M5, M6, M7 -> true
            }
        }
}

enum class M7Phases(val displayName: String) {
    P1("P1"), P2("P2"), P3("P3"), P4("P4"), P5("P5"), Unknown("Unknown");
}