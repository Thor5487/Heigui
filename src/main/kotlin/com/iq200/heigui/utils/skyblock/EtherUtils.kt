package com.iq200.heigui.utils.skyblock

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.multiply
import com.iq200.heigui.utils.toBlockPos
import com.iq200.heigui.utils.vec3FromRotation
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.function.Consumer
import kotlin.math.*

object EtherUtils {
    const val STAND_EYE_HEIGHT: Double = 1.6200000047683716
    const val SNEAK_EYE_HEIGHT: Double = 1.27 // Change to 1.27d when update to 1.21.10
    const val SNEAK_HEIGHT_INVERTED: Double = 0.0800000429153443
    const val DEGREES_TO_RADIAN: Double = Math.PI / 180.0
    const val EPSILON: Double = 0.001

    private val validTypes: MutableSet<Class<out Block?>> = HashSet<Class<out Block?>>(
        listOf<Class<out Block?>?>(
            ButtonBlock::class.java, SkullBlock::class.java, DoublePlantBlock::class.java,
            WallSkullBlock::class.java, LadderBlock::class.java, SaplingBlock::class.java,
            FlowerBlock::class.java, StemBlock::class.java, CropBlock::class.java,
            RailBlock::class.java, BubbleColumnBlock::class.java, SnowLayerBlock::class.java,
            TripWireBlock::class.java, TripWireHookBlock::class.java, FireBlock::class.java,
            AirBlock::class.java, TorchBlock::class.java, FlowerPotBlock::class.java,
            TallFlowerBlock::class.java, TallDryGrassBlock::class.java, BushBlock::class.java,
            SeagrassBlock::class.java, TallSeagrassBlock::class.java, SugarCaneBlock::class.java,
            LiquidBlock::class.java, VineBlock::class.java, MushroomBlock::class.java, TallGrassBlock::class.java,
            PistonHeadBlock::class.java, WebBlock::class.java, ShortDryGrassBlock::class.java,
            DryVegetationBlock::class.java, SmallDripleafBlock::class.java, LeverBlock::class.java,
            NetherWartBlock::class.java, NetherPortalBlock::class.java, RedStoneWireBlock::class.java,
            ComparatorBlock::class.java, RedstoneTorchBlock::class.java, RepeaterBlock::class.java
        )
    )

    private val invalidTypes: MutableSet<Class<out Block?>> = HashSet<Class<out Block?>>(
        listOf<Class<out Block?>?>(
            LadderBlock::class.java, VineBlock::class.java,
            SkullBlock::class.java, FlowerPotBlock::class.java
        )
    )

    private val aboveTypes: MutableSet<Class<out Block?>> = HashSet<Class<out Block?>>(
        listOf<Class<out Block?>?>(
            FenceBlock::class.java, FenceGateBlock::class.java, WallBlock::class.java
        )
    )

    // teleport
    private const val STEPS: Double = 1000.0

    private val IGNORED_BLOCKS_CLASSES: MutableSet<Class<out Block?>?> = HashSet<Class<out Block?>?>(
        listOf<Class<out Block?>?>(
            ButtonBlock::class.java,
            AirBlock::class.java,
            CarpetBlock::class.java,
            RedStoneWireBlock::class.java,
            MushroomBlock::class.java,
            FlowerBlock::class.java,
            StemBlock::class.java,
            CropBlock::class.java,
            TripWireBlock::class.java,
            RailBlock::class.java
        )
    )

    private val IGNORED_BLOCKS: MutableList<Block?> = listOf<Block?>(
        Blocks.LAVA,
        Blocks.WATER
    ) as MutableList<Block?>

    private val SPECIAL_BLOCKS: MutableSet<Class<out Block?>?> = HashSet<Class<out Block?>?>(
        listOf<Class<out Block?>?>(
            LadderBlock::class.java, WaterlilyBlock::class.java
        )
    )

    private val validEtherwarpSpaceIds = BitSet(0)
    private val invalidEtherwarpSpaceIds = BitSet(0)
    private val aboveEtherwarpIds = BitSet(0)

    init {
        initIDs()
    }

    fun initIDs() {
        BuiltInRegistries.BLOCK.forEach(Consumer { block: Block? ->
            val blockId = Block.getId(block!!.defaultBlockState())
            for (type in validTypes) {
                if (type.isInstance(block)) {
                    validEtherwarpSpaceIds.set(blockId)
                    break
                }
            }

            for (type in invalidTypes) {
                if (type.isInstance(block)) {
                    invalidEtherwarpSpaceIds.set(blockId)
                    break
                }
            }
            for (type in aboveTypes) {
                if (type.isInstance(block)) {
                    aboveEtherwarpIds.set(blockId)
                    break
                }
            }
        })
    }

    fun getYawAndPitch(dx: Double, dy: Double, dz: Double): FloatArray {
        val horizontalDistance = sqrt(dx * dx + dz * dz)

        val yaw = Math.toDegrees(atan2(-dx, dz))
        val pitch = -Math.toDegrees(atan2(dy, horizontalDistance))

        val normalizedYaw = if (yaw < -180) yaw + 360 else yaw

        return floatArrayOf(normalizedYaw.toFloat(), pitch.toFloat())
    }

    fun getYawAndPitch(pos: Vec3, sneaking: Boolean, playerSP: LocalPlayer, doY: Boolean): FloatArray {
        val dx = pos.x - playerSP.x
        val dy =
            if (!doY) 0.0 else (pos.y - (playerSP.y + getEyeHeight(if (sneaking) Pose.CROUCHING else Pose.STANDING)))
        val dz = pos.z - playerSP.z
        return getYawAndPitch(dx, dy, dz)
    }

    fun fastGetEtherFromOrigin(start: Vec3, yaw: Float, pitch: Float, dist: Int): BlockPos? {
        return fastGetEtherFromOrigin(start, yaw, pitch, dist, false)
    }

    fun fastGetEtherFromOrigin(start: Vec3, yaw: Float, pitch: Float, dist: Int, fullOnly: Boolean): BlockPos? {
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return null
        val end = Minecraft.getInstance().player!!.calculateViewVector(pitch, yaw).scale(dist.toDouble()).add(start)
        val world: ClientLevel = Minecraft.getInstance().level!!

        val direction = end.subtract(start)

        val step = IntArray(3)
        for (i in 0..2) {
            step[i] = sign(getCoord(direction, i)).toInt()
        }

        val invDirection = DoubleArray(3)
        for (i in 0..2) {
            val d = getCoord(direction, i)
            invDirection[i] = if (d != 0.0) (1.0 / d) else Double.Companion.MAX_VALUE
        }

        val tDelta = DoubleArray(3)
        for (i in 0..2) {
            tDelta[i] = invDirection[i] * step[i]
        }

        val currentPos = IntArray(3)
        val endPos = IntArray(3)
        for (i in 0..2) {
            currentPos[i] = floor(getCoord(start, i)).toInt()
            endPos[i] = floor(getCoord(end, i)).toInt()
        }

        val tMax = DoubleArray(3)
        for (i in 0..2) {
            val startCoord = getCoord(start, i)
            tMax[i] = abs((floor(startCoord) + max(step[i], 0) - startCoord) * invDirection[i])
        }

        val pos = MutableBlockPos()
        repeat(1000) {
            pos.set(currentPos[0], currentPos[1], currentPos[2])

            if (!Minecraft.getInstance().level!!.hasChunk(pos.x shr 4, pos.z shr 4)) return null
            val chunk = world.getChunk(pos)

            val blockState = chunk.getBlockState(pos)
            val currentBlock = blockState.block
            val currentBlockId = Block.getId(currentBlock.defaultBlockState())

            if (aboveEtherwarpIds.get(currentBlockId)) {
                pos.set(pos.x, pos.y + 1, pos.z)
            }

            if (!validEtherwarpSpaceIds.get(currentBlockId)) {
                val footBlockId = Block.getId(
                    chunk.getBlockState(
                        BlockPos(
                            pos.x,
                            pos.y + 1,
                            pos.z
                        )
                    )
                        .block.defaultBlockState()
                )
                if (!validEtherwarpSpaceIds.get(footBlockId) || invalidEtherwarpSpaceIds.get(footBlockId) || (fullOnly && !blockState.isCollisionShapeFullBlock(
                        Minecraft.getInstance().level!!,
                        pos
                    ))
                ) return null

                val headBlockId = Block.getId(
                    chunk.getBlockState(
                        BlockPos(
                            pos.getX(),
                            pos.getY() + 2,
                            pos.getZ()
                        )
                    )
                        .getBlock().defaultBlockState()
                )
                if (!validEtherwarpSpaceIds.get(headBlockId) || invalidEtherwarpSpaceIds.get(headBlockId)) return null

                return pos
            }

            if (currentPos[0] == endPos[0] && currentPos[1] == endPos[1] && currentPos[2] == endPos[2]) {
                return null
            }
            val minIndex: Int = if (tMax[0] <= tMax[1]) {
                if (tMax[0] <= tMax[2]) 0 else 2
            } else {
                if (tMax[1] <= tMax[2]) 1 else 2
            }

            tMax[minIndex] += tDelta[minIndex]
            currentPos[minIndex] += step[minIndex]
        }

        return null
    }

    fun getEtherPosFromOrigin(origin: Vec3, yaw: Float, pitch: Float, dist: Int): Pair<BlockPos?, Boolean> {
        if (mc.player == null) return Pair(null, false)

        val endPos = mc.player!!.calculateViewVector(pitch, yaw).scale(dist.toDouble()).add(origin)
        return traverseVoxels(origin, endPos)
    }

    fun getEtherPosFromOrigin(origin: Vec3, distance: Int): Pair<BlockPos?, Boolean> {
        if (mc.player == null) return Pair(null, false)

        val endPos = mc.player!!.lookAngle.scale(distance.toDouble()).add(origin)
        return traverseVoxels(origin, endPos)
    }

    private fun getCoord(vec: Vec3, i: Int): Double {
        return when (i) {
            0 -> vec.x
            1 -> vec.y
            2 -> vec.z
            else -> 0.0
        }
    }

    private fun traverseVoxels(start: Vec3, end: Vec3): Pair<BlockPos?, Boolean> {
        if (mc.level == null) return Pair(null, false)
        val world: ClientLevel = mc.level!!

        val direction = end.subtract(start)

        val step = IntArray(3)
        for (i in 0..2) {
            step[i] = sign(getCoord(direction, i)).toInt()
        }

        val invDirection = DoubleArray(3)
        for (i in 0..2) {
            val d = getCoord(direction, i)
            invDirection[i] = if (d != 0.0) (1.0 / d) else Double.Companion.MAX_VALUE
        }

        val tDelta = DoubleArray(3)
        for (i in 0..2) {
            tDelta[i] = invDirection[i] * step[i]
        }

        val currentPos = IntArray(3)
        val endPos = IntArray(3)
        for (i in 0..2) {
            currentPos[i] = floor(getCoord(start, i)).toInt()
            endPos[i] = floor(getCoord(end, i)).toInt()
        }

        val tMax = DoubleArray(3)
        for (i in 0..2) {
            val startCoord = getCoord(start, i)
            tMax[i] = abs((floor(startCoord) + max(step[i], 0) - startCoord) * invDirection[i])
        }

        val pos = MutableBlockPos()

        repeat(1000) {
            pos.set(currentPos[0], currentPos[1], currentPos[2])

            if (!Minecraft.getInstance().level!!.hasChunk(pos.x shr 4, pos.z shr 4)) return Pair(null, false)
            val chunk = world.getChunk(pos)

            val blockState = chunk.getBlockState(pos)
            val currentBlock = blockState.getBlock()
            val currentBlockId = Block.getId(currentBlock.defaultBlockState())

            if (aboveEtherwarpIds.get(currentBlockId)) {
                pos.set(pos.x, pos.y + 1, pos.z)
            }

            if (!validEtherwarpSpaceIds.get(currentBlockId)) {
                val footPos = BlockPos(
                    pos.x,
                    pos.y + 1,
                    pos.z
                )

                val footState = chunk.getBlockState(footPos)
                val footBlock = footState.block
                val footBlockId = Block.getId(footBlock.defaultBlockState())

                if (!validEtherwarpSpaceIds.get(footBlockId) || invalidEtherwarpSpaceIds.get(footBlockId)) return Pair(
                    pos,
                    false
                )

                val headPos = BlockPos(
                    pos.x,
                    pos.y + 2,
                    pos.z
                )

                val headState = chunk.getBlockState(headPos)
                val headBlock = headState.block
                val headBlockId = Block.getId(headBlock.defaultBlockState())

                if (!validEtherwarpSpaceIds.get(headBlockId) || invalidEtherwarpSpaceIds.get(headBlockId)) return Pair(
                    pos,
                    false
                )

                return Pair(pos, true)
            }

            if (currentPos.contentEquals(endPos)) {
                return Pair(null, false)
            }

            val minIndex: Int
            if (tMax[0] <= tMax[1]) {
                minIndex = if (tMax[0] <= tMax[2]) 0 else 2
            } else {
                minIndex = if (tMax[1] <= tMax[2]) 1 else 2
            }

            tMax[minIndex] += tDelta[minIndex]
            currentPos[minIndex] += step[minIndex]
        }

        return Pair(null, false)
    }

    private fun getBlockId(pos: BlockPos, chunk: ChunkAccess): Int {
        return Block.getId(chunk.getBlockState(pos).block.defaultBlockState())
    }

    fun isValidEtherwarpPosition(pos: BlockPos): Boolean {
        if (Minecraft.getInstance().level == null) return false
        val chunk = Minecraft.getInstance().level!!.getChunk(pos)

        if (validEtherwarpSpaceIds.get(getBlockId(pos, chunk))) return false

        val footBlockId = getBlockId(pos.above(1), chunk)
        if (!validEtherwarpSpaceIds.get(footBlockId) || invalidEtherwarpSpaceIds.get(footBlockId)) return false

        val headBlockId = getBlockId(pos.above(2), chunk)
        return validEtherwarpSpaceIds.get(headBlockId) && !invalidEtherwarpSpaceIds.get(headBlockId)
    }

    fun rayTraceBlock(maxDistance: Int, yaw: Float, pitch: Float, playerEyePos: Vec3): Vec3? {
        val roundedYaw = round(yaw.toDouble(), 14) * DEGREES_TO_RADIAN
        val roundedPitch = round(pitch.toDouble(), 14) * DEGREES_TO_RADIAN

        val cosPitch = cos(roundedPitch)
        val dx = -cosPitch * sin(roundedYaw)
        val dy = -sin(roundedPitch)
        val dz = cosPitch * cos(roundedYaw)

        var x = floor(playerEyePos.x()).toInt()
        var y = floor(playerEyePos.y()).toInt()
        var z = floor(playerEyePos.z()).toInt()

        val stepX = if (dx < 0) -1 else 1
        val stepY = if (dy < 0) -1 else 1
        val stepZ = if (dz < 0) -1 else 1

        val tDeltaX = abs(1.0 / dx)
        val tDeltaY = abs(1.0 / dy)
        val tDeltaZ = abs(1.0 / dz)

        var tMaxX = (if (dx < 0) playerEyePos.x() - x else x + 1 - playerEyePos.x()) * tDeltaX
        var tMaxY = (if (dy < 0) playerEyePos.y() - y else y + 1 - playerEyePos.y()) * tDeltaY
        var tMaxZ = (if (dz < 0) playerEyePos.z() - z else z + 1 - playerEyePos.z()) * tDeltaZ

        if (!isAir(BlockPos(x, y, z))) {
            return Vec3(playerEyePos.x(), playerEyePos.y(), playerEyePos.z())
        }

        var i = 0
        while (i < maxDistance) {
            i++

            val c = min(tMaxX, min(tMaxY, tMaxZ))

            val hitX = ((playerEyePos.x() + dx * c) * 1e10).roundToInt() * 1e-10
            val hitY = ((playerEyePos.y() + dy * c) * 1e10).roundToInt() * 1e-10
            val hitZ = ((playerEyePos.z() + dz * c) * 1e10).roundToInt() * 1e-10

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX
                tMaxX += tDeltaX
            } else if (tMaxY < tMaxZ) {
                y += stepY
                tMaxY += tDeltaY
            } else {
                z += stepZ
                tMaxZ += tDeltaZ
            }

            if (!isAir(BlockPos(x, y, z))) {
                return Vec3(hitX, hitY, hitZ)
            }
        }

        return null
    }

    private fun isAir(pos: BlockPos): Boolean {
        if (Minecraft.getInstance().level == null
            || !Minecraft.getInstance().level!!.hasChunk(pos.x shr 4, pos.z shr 4)
        ) return true
        val block = Minecraft.getInstance().level!!.getBlockState(pos).block
        val currentBlockId = Block.getId(block.defaultBlockState())
        // ChatUtils.chat(block.getName() + " : " + bl);
        return validEtherwarpSpaceIds.get(currentBlockId)
    }

    private fun round(value: Double, places: Int): Double {
        val scale = 10.0.pow(places.toDouble())
        return (value * scale).roundToInt() / scale
    }

    fun predictTeleport(distance: Int, start: Vec3, yaw: Float, pitch: Float): Vec3? {
        val forward: Vec3 = vec3FromRotation(pitch, yaw).multiply(1.0 / STEPS)
        val player: Vec3 = start.add(0.0, getEyeHeight().toDouble(), 0.0)
        var cur: Vec3 = player
        var i = 0

        while (true) {
            if (i.toDouble() < distance.toDouble() * STEPS) {
                // full block

                if (i.toDouble() % STEPS == 0.0 && !isSpecial(cur) && !isIgnored(cur)) {
                    cur = cur.add(forward.multiply(-STEPS))
                    return if (i != 0 && isIgnored(cur))
                        Vec3(floor(cur.x()) + 0.5, floor(cur.y()), floor(cur.z()) + 0.5)
                    else
                        null
                }

                //
                if ((isIgnored2(cur) || !inBB(cur)) && (isIgnored2(cur.add(0.0, 1.0, 0.0)) || !inBB(
                        cur.add(
                            0.0,
                            1.0,
                            0.0
                        )
                    ))
                ) {
                    cur = cur.add(forward)
                    ++i
                    continue
                }

                cur = cur.add(forward.multiply(-STEPS))
                if (i == 0 || !isIgnored(cur) && inBB(cur) || !isIgnored(cur.add(0.0, 1.0, 0.0)) && inBB(
                        cur.add(
                            0.0,
                            1.0,
                            0.0
                        )
                    )
                ) {
                    return null
                }
            }

            val pos: Vec3 = player.add(vec3FromRotation(pitch, yaw).multiply(floor(i.toDouble() / STEPS)))
            if ((isIgnored(cur) || !inBB(cur)) && (isIgnored(cur.add(0.0, 1.0, 0.0)) || !inBB(
                    cur.add(
                        0.0,
                        1.0,
                        0.0
                    )
                ))
            ) {
                return Vec3(floor(pos.x()) + 0.5, floor(pos.y()), floor(pos.z()) + 0.5)
            }

            return null
        }
    }

    // hypixel probably isnt using the other poses yet
    fun getPose(): Pose {
        if (isSneaking()) return Pose.CROUCHING
        if (isSwimming()) return Pose.SWIMMING
        return Pose.STANDING
    }

    fun getSneakHeight(): Float {
        return mc.player!!.getEyeHeight(Pose.CROUCHING)
    }

    fun getEyeHeight(): Float {
        return mc.player!!.getEyeHeight(getPose())
    }

    fun getEyeHeight(pose: Pose?): Float {
        return mc.player!!.getEyeHeight(pose!!)
    }

    fun isSneaking(): Boolean {
        if (mc.player == null) return false
        return mc.player!!.lastSentInput.shift()
    }

    fun isSwimming(): Boolean {
        if (mc.player == null) return false
        return mc.player!!.isVisuallySwimming
    }


    // so like do we just use pose now orrr
    // yes
    /**
     * Deprecated
     * Use [EtherUtils.getEyeHeight] instead
     * @param sneak If the player should be sneaking
     * @return The eye height
     */
    @Deprecated("")
    fun getEyeHeight(sneak: Boolean): Double {
        return (if (sneak) getSneakHeight() else STAND_EYE_HEIGHT) as Double
    }

    fun predictTeleportNoCheck(distance: Int, start: Vec3, yaw: Float, pitch: Float): Vec3 {
        val player: Vec3 = start.add(0.0, STAND_EYE_HEIGHT, 0.0)
        val dir: Vec3 = vec3FromRotation(pitch, yaw)
        val end: Vec3 = player.add(dir.multiply(distance.toDouble()))
        return Vec3(
            floor(end.x()) + 0.5,
            floor(end.y()),
            floor(end.z()) + 0.5
        )
    }

    private fun isIgnored(pos: Vec3): Boolean {
        val state = mc.level!!.getBlockState(pos.toBlockPos())
        return isIgnored(state)
    }

    private fun isIgnored(state: BlockState): Boolean {
        return IGNORED_BLOCKS.contains(state.getBlock())
                || IGNORED_BLOCKS_CLASSES.stream()
            .anyMatch { c: Class<out Block?>? -> c!!.isInstance(state.getBlock()) }
    }

    private fun isIgnored2(pos: Vec3): Boolean {
        val state = mc.level!!.getBlockState(pos.toBlockPos())
        return isIgnored(state) || state.block is SlabBlock
    }

    fun isSpecial(pos: Vec3): Boolean {
        val state = mc.level!!.getBlockState(pos.toBlockPos())
        return SPECIAL_BLOCKS.stream().anyMatch { c: Class<out Block?>? -> c!!.isInstance(state.getBlock()) }
    }

    // todo: verify if this is even correct
    fun inBB(pos: Vec3): Boolean {
        // if (!isSpecial(x, y, z)) return true;
        val block = mc.level!!.getBlockState(pos.toBlockPos())
        val bb = block.getShape(mc.level!!, pos.toBlockPos()).bounds()
        return bb.contains(pos)
    }
}

