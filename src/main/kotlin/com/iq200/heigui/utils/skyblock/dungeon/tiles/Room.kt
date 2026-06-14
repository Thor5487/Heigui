package com.iq200.heigui.utils.skyblock.dungeon.tiles

import com.google.gson.annotations.SerializedName
import com.iq200.heigui.utils.Vec2i
import net.minecraft.core.BlockPos

data class Room(
    var rotation: Rotations = Rotations.NONE,
    var data: RoomData,
    var clayPos: BlockPos = BlockPos(0, 0, 0),
    val roomComponents: MutableSet<RoomComponent>,
    // 🗑️ 已移除 waypoints 屬性
)

data class RoomComponent(val x: Int, val z: Int, val core: Int = 0) {
    val vec2 = Vec2i(x, z)
    val blockPos = BlockPos(x, 70, z)
}

data class RoomData(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: RoomType,
    @SerializedName("cores") val cores: List<Int>,
    @SerializedName("crypts") val crypts: Int,
    @SerializedName("secrets") val secrets: Int,
    @SerializedName("trappedChests") val trappedChests: Int,
    @SerializedName("shape") val shape: RoomShape
)