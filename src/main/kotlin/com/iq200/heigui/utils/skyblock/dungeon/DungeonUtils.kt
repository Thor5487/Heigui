package com.iq200.heigui.utils.skyblock.dungeon

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.Vec2
import com.iq200.heigui.utils.equalsOneOf
import com.iq200.heigui.utils.romanToInt
import com.iq200.heigui.utils.rotateAroundNorth
import com.iq200.heigui.utils.rotateToNorth
import com.iq200.heigui.utils.skyblock.Island
import com.iq200.heigui.utils.skyblock.LocationUtils
import com.iq200.heigui.utils.skyblock.dungeon.tiles.Room
import com.iq200.heigui.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

object DungeonUtils {

    private const val WITHER_ESSENCE_ID = "e0f3e929-869e-3dca-9504-54c666ee6f23"
    private const val REDSTONE_KEY = "fed95410-aba1-39df-9b95-1d4f361eb66e"

    /**
     * 判斷玩家目前是否在地下城中 (依賴 LocationUtils 解析 Scoreboard)。
     */
    inline val inDungeons: Boolean
        get() = LocationUtils.isCurrentArea(Island.Dungeon)

    /**
     * 判斷玩家目前是否處於 Boss 房間 (依賴 DungeonListener 的事件追蹤)。
     */
    inline val inBoss: Boolean
        get() = DungeonListener.inBoss


    inline val inClear: Boolean
        get() = inDungeons && !inBoss

    /**
     * 取得當前的樓層資訊 (依賴 DungeonListener 解析 Scoreboard)。
     */
    inline val floor: Floor?
        get() = DungeonListener.floor

    inline val secretPercentage: Float
        get() = DungeonListener.dungeonStats.secretsPercent

    inline val totalSecrets: Int
        get() = if (secretCount == 0 || secretPercentage == 0f) 0 else floor(100 / secretPercentage * secretCount + 0.5).toInt()

    inline val secretCount: Int
        get() = DungeonListener.dungeonStats.secretsFound

    inline val cryptCount: Int
        get() = DungeonListener.dungeonStats.crypts


    inline val mimicKilled: Boolean
        get() = DungeonListener.dungeonStats.mimicKilled

    inline val princeKilled: Boolean
        get() = DungeonListener.dungeonStats.princeKilled

    inline val deathCount: Int
        get() = DungeonListener.dungeonStats.deaths

    inline val dungeonTeammates: List<DungeonPlayer>
        get() = DungeonListener.dungeonTeammates

    /**
     * 檢查當前樓層是否符合指定的樓層之一。
     *
     * @param options 允許的樓層數字 (例如 7 代表 F7/M7)。
     * @return 如果當前樓層在傳入的選項中則回傳 true，否則回傳 false。
     */
    fun isFloor(vararg options: Int): Boolean {
        return floor?.floorNumber?.let { it in options } ?: false
    }

    /**
     * 取得 F7/M7 Boss 戰的當前階段。
     * 依賴於玩家的 Y 座標來判斷，對於 BloodBlink 來說可能不是必須的，
     * 但保留此功能以備未來其他 Floor 7 相關功能使用。
     *
     * @return M7Phases 列舉，若不在 F7 或是 Boss 房則回傳 Unknown。
     */
    fun getF7Phase(): M7Phases {
        if ((!isFloor(7) || !inBoss) && !LocationUtils.isCurrentArea(Island.SinglePlayer)) return M7Phases.Unknown

        val yPos = mc.player?.y ?: return M7Phases.Unknown
        return when {
            yPos > 210 -> M7Phases.P1
            yPos > 155 -> M7Phases.P2
            yPos > 100 -> M7Phases.P3
            yPos > 45 -> M7Phases.P4
            else -> M7Phases.P5
        }
    }

    /**
     * [BloodBlink 核心依賴]
     * 將絕對座標轉換為相對於房間 Clay 基準點且面朝北的相對座標。
     */
    fun Room.getRelativeCoords(pos: BlockPos): BlockPos =
        pos.subtract(clayPos.atY(0)).rotateToNorth(rotation)


    fun Room.getRelativeCoords(realPos: Vec3): Vec3 {
        val pivotX = this.clayPos.x.toDouble() + 0.5
        val pivotZ = this.clayPos.z.toDouble() + 0.5

        val relativeToPivot = Vec3(realPos.x - pivotX, realPos.y, realPos.z - pivotZ)

        val rotated = relativeToPivot.rotateToNorth(rotation)

        return Vec3(rotated.x + 0.5, rotated.y, rotated.z + 0.5)
    }
    /**
     * [BloodBlink 核心依賴]
     * 將相對於房間的局部座標，根據房間的旋轉與 Clay 基準點，轉換為世界中的絕對座標。
     */
    fun Room.getRealCoords(pos: BlockPos): BlockPos =
        pos.rotateAroundNorth(rotation).offset(clayPos.x, 0, clayPos.z)


    fun Room.getRealCoords(pos: Vec3): Vec3 {
        val relativeToPivot = Vec3(pos.x - 0.5, pos.y, pos.z - 0.5)

        val rotated = relativeToPivot.rotateAroundNorth(rotation)

        val pivotX = this.clayPos.x.toDouble() + 0.5
        val pivotZ = this.clayPos.z.toDouble() + 0.5

        return Vec3(rotated.x + pivotX, rotated.y, rotated.z + pivotZ)
    }

    fun Room.getRealDirection(localDir: Vec3): Vec3 {
        return localDir.rotateAroundNorth(rotation)
    }

    fun isSecret(state: BlockState, pos: BlockPos): Boolean {
        return when {
            state.block.equalsOneOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER) -> true
            state.block is SkullBlock ->
                (mc.level?.getBlockEntity(pos) as? SkullBlockEntity)?.ownerProfile?.partialProfile()?.id
                    ?.toString()?.equalsOneOf(WITHER_ESSENCE_ID, REDSTONE_KEY) ?: false

            else -> false
        }
    }

    inline val getBonusScore: Int
        get() {
            var score = cryptCount.coerceAtMost(5)
            if (mimicKilled) score += 2
            if (princeKilled) score += 1
            return score
        }

    inline val neededSecretsAmount: Int
        get() =
            DungeonListener.floor?.let {
                ceil(
                    (totalSecrets * it.requiredPercentage) * (40 - getBonusScore + (deathCount * 2 - 1).coerceAtLeast(0)) / 40.0
                ).toInt()
            } ?: 0


    inline val idealNeededSecretsAmoount: Int
        get() =
            DungeonListener.floor?.let {
                ceil(
                    (totalSecrets * it.requiredPercentage) * (40 - 7 - if (princeKilled) 1 else 0) / 40.0
                ).toInt()
            } ?: 0


    inline val remainingSecrets: Int
        get() {
            if (totalSecrets == 0) return 999

            return neededSecretsAmount - secretCount
        }

    private val tablistRegex = Regex("^\\[(\\d+)] (?:\\[\\w+] )*(\\w+) .*?\\((\\w+)(?: (\\w+))*\\)$")


    fun getDungeonTeammates(previousTeammates: ArrayList<DungeonPlayer>, tabList: List<String>): ArrayList<DungeonPlayer> {
        for (line in tabList) {
            val (_, name, clazz, clazzLevel) = tablistRegex.find(line)?.destructured ?: continue

            previousTeammates.find { it.name == name }?.let { player ->
                if (player.clazz == DungeonClass.EMPTY) {
                    player.clazz = DungeonClass.entries.find { it.name.equals(clazz, ignoreCase = true) } ?: DungeonClass.EMPTY
                    player.clazzLvl = romanToInt(clazzLevel)
                }

                player.isDead = clazz == "DEAD"
            } ?: run {
                val player = mc.connection?.getPlayerInfo(name) ?: continue
                previousTeammates.add(
                    DungeonPlayer(
                        name, DungeonClass.entries.find { it.name.equals(clazz, ignoreCase = true) } ?: continue,
                        if (clazzLevel.isEmpty()) -1 else romanToInt(clazzLevel), player.skin,
                        entity = mc.level?.getPlayerByUUID(player.profile.id)
                    )
                )
            }
        }
        return previousTeammates
    }
}