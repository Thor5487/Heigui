package com.iq200.heigui.api.dungeon

import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils
import com.iq200.heigui.utils.skyblock.dungeon.tiles.RoomType

/**
 * Stable, read-only dungeon state exposed to integrations such as ZPBW.
 *
 * Only JVM and JDK types are returned so consumers do not need to depend on
 * Heigui's internal dungeon model.
 */
object HeiguiDungeonApi {
    @JvmStatic
    fun protocolVersion(): Int = 2

    @JvmStatic
    fun isInDungeon(): Boolean = DungeonUtils.inDungeons

    @JvmStatic
    fun isInBoss(): Boolean = DungeonUtils.inBoss

    @JvmStatic
    fun isInClear(): Boolean = DungeonUtils.inClear

    /** Returns the current floor number, or -1 when no dungeon floor is known. */
    @JvmStatic
    fun getFloorNumber(): Int = DungeonUtils.floor?.floorNumber ?: -1

    @JvmStatic
    fun getCurrentRoomName(): String? = ScanUtils.currentRoom?.data?.name

    @JvmStatic
    fun getCurrentRoomType(): String? = ScanUtils.currentRoom?.data?.type?.name

    /** Returns a copy so integrations cannot mutate Heigui's room data. */
    @JvmStatic
    fun getCurrentRoomCores(): IntArray =
        ScanUtils.currentRoom?.data?.cores?.toIntArray() ?: IntArray(0)

    @JvmStatic
    fun isCurrentRoom(name: String): Boolean =
        getCurrentRoomName()?.equals(name, ignoreCase = true) == true

    /** Shared admission rule for Heigui teleport prediction and dependent ZPBW. */
    @JvmStatic
    fun isTeleportPredictionAllowed(): Boolean {
        val room = ScanUtils.currentRoom
        val roomAllowed = room == null ||
            (!room.data.name.contains("Boulder", ignoreCase = true) &&
                !room.data.name.contains("Teleport Maze", ignoreCase = true) &&
                room.data.type !== RoomType.TRAP)
        return roomAllowed && !(DungeonUtils.isFloor(7) && DungeonUtils.inBoss)
    }
}
