package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.utils.DeathTickUtil
import com.iq200.heigui.utils.InputKey
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Vec2
import com.iq200.heigui.utils.fillItemFromSack
import com.iq200.heigui.utils.handlers.schedule
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils
import com.iq200.heigui.utils.skyblock.dungeon.tiles.Room
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.sqrt

object BloodBlink : Module(
    "BloodBlink",
    description = "Auto 0s Blood Rush",
    category = Category.DUNGEON
) {
    private data class Task(
        val name: String,
        val condition: (() -> Boolean)? = null,
        val action: () -> Unit
    )

    private val taskQueue = mutableListOf<Task>()
    private var isRunning = false
    private var hasExecuted = false

    private val OAK_LOG_CHECK_POS = BlockPos(15, 73, 24)
    private val BLINK_SPOT_TYPE_1 = BlockPos(2, 83, 18)
    private val BLINK_SPOT_TYPE_2 = BlockPos(2, 82, 16)
    private var bloodRoom: Room? = null
    private var underBlood = false
    private var canEnterBlood = false
    private var tickCounter = 1
    private var isFastPath = false
    private var enterYaw: Float? = null


    init {
        on<WorldEvent.Load> {
            resetAll()
        }

        on<TickEvent.Server> {
            if (!DungeonUtils.inDungeons) return@on


            if (isRunning || hasExecuted) return@on

            if (taskQueue.isEmpty()) {
                hasExecuted = true
                startBloodRushFlow()
            }
        }

        on<TickEvent.Server> {
            if (!DungeonUtils.inDungeons) return@on

            if (bloodRoom == null) bloodRoom = scanForBloodRoom()

            tickCounter--
            if (tickCounter == 0) {
                fillItemFromSack(16, "ENDER_PEARL", "ENDER_PEARL", false)
                tickCounter = 30
            }
        }

        on<ChatPacketEvent> {
            if (!DungeonUtils.inDungeons) return@on

            val unformattedText = value.replace(Regex("§[0-9a-fk-or]"), "")

            if (unformattedText.trim() == "Starting in 1 second.") {

                schedule(20, true) {
                    if (mc.player != null) {
                        canEnterBlood = true
                    }
                }
            }
        }
    }

    public fun resetAll() {
        taskQueue.clear()
        isRunning = false
        bloodRoom = null
        hasExecuted = false
        underBlood = true
        canEnterBlood = false
        isFastPath = false
        enterYaw = null
        // ... any other flags
    }

    public fun startBloodRushFlow() {
        isRunning = true
        enterYaw = mc.player!!.yRot
        modMessage("§4[BloodBlink] §fStart Scanning Map")


        // Step 1: TP to Blink Spot and Pearl Into Ceiling
        addTask(
            name = "Pearl Clip",
            condition = {
                val player = mc.player ?: return@addTask false

                if (PlayerUtils.countItemInHotbar(Items.ENDER_PEARL) < 8) {
                    return@addTask false
                }

                val targetStandPos = getSmartBlinkSpot() ?: return@addTask false

                val isAtTarget = player.y >= (targetStandPos.y - 1.0)

                if (isAtTarget) {
                    true
                } else {

                    executeInitialTP()

                    false
                }
            }
        ) {
            clipOutEntranceRoomPearls()
        }

        // Step 2: Wait For Deathtick and TP Up
        addTask(
            name = "Wait for Death Tick & Check Height",
            condition = {
                val player = mc.player ?: return@addTask false

                val currentDeathTick = DeathTickUtil.ticks

                if (bloodRoom != null && canEnterBlood && DeathTickUtil.ticks > 10 && player.y >= 97.5) return@addTask true

                else if  (bloodRoom == null && player.y >= 97.5 && currentDeathTick >= 20L) return@addTask true

                false
            }
        ) {
            if (bloodRoom != null) {
                isFastPath = true
                mc.execute {
                    enterBloodInstant()
                }
            } else {
                tpUpAtRoof()
                tpToMid()
            }
        }
        // Step 3: TP to Blink Spot and Pearl to Ceiling
        addTask(
            name = "Wait Respawn & Second Initial TP",
            condition = {
                if (isFastPath) return@addTask true
                val player = mc.player ?: return@addTask false

                if (player.y >= 90.0) {
                    return@addTask false
                }

                val targetStandPos = getSmartBlinkSpot() ?: return@addTask false

                val isAtTarget = player.y >= (targetStandPos.y - 1.0)

                if (isAtTarget) {
                    true
                } else {
                    executeInitialTP()
                    false
                }
            }
        ) {
            if (!isFastPath) clipOutEntranceRoomPearls()
        }

        //Enter Blood
        addTask(
            name = "Wait for Death Tick & Check Height",
            condition = {
                if (isFastPath) return@addTask true
                val player = mc.player ?: return@addTask false

                canEnterBlood && DeathTickUtil.ticks > 10 && player.y >= 97.5
            }
        ) {
            if (!isFastPath) {
                mc.execute {
                    enterBloodInstant()
                }
            }

            canEnterBlood = false
        }

        executeNextTask()
    }


    private fun executeNextTask() {
        if (taskQueue.isEmpty()) {
            isRunning = false
            return
        }

        val task = taskQueue.first()

        if (task.condition?.invoke() == false) {
            schedule(0, true) { mc.execute{executeNextTask()} }
            return
        }

        taskQueue.removeAt(0)
        task.action()

        executeNextTask()
    }

    private fun addTask(name: String, condition: (() -> Boolean)? = null, action: () -> Unit) {
        taskQueue.add(Task(name, condition, action))
    }

    fun scanForBloodRoom(): Room? {
        val startXZ = -185
        val roomSize = 32

        for (gridX in 0..5) {
            for (gridZ in 0..5) {
                val realX = startXZ + (gridX * roomSize)
                val realZ = startXZ + (gridZ * roomSize)

                val vec2 = Vec2(realX, realZ)
                val room = ScanUtils.scanRoom(vec2)

                if (room != null && room.data.name.contains("Blood")) {
                    modMessage("§4[BloodBlink] §fFound Blood Room")
                    return room
                }
            }
        }

        return null
    }

    fun throwPearl() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return

        val pearlSlot = PlayerUtils.findItemInHotbar(Items.ENDER_PEARL)

        if (pearlSlot == null) {
            modMessage("§c[BloodBlink] No Pearls Found In Inventory")
            resetAll()
            return
        }

        PlayerUtils.setHotbarSlot(pearlSlot)

        gameMode.useItem(player, InteractionHand.MAIN_HAND)

        player.swing(InteractionHand.MAIN_HAND)
    }

    fun getSmartBlinkSpot(): BlockPos? {
        val entranceRoom = ScanUtils.currentRoom
        if (entranceRoom == null || entranceRoom.data.name != "Entrance") return null

        val level = mc.level ?: return null
        val absoluteCheckPos = entranceRoom.getRealCoords(OAK_LOG_CHECK_POS)
        val isType1 = level.getBlockState(absoluteCheckPos).block == Blocks.OAK_LOG

        val targetRelativePos = if (isType1) BLINK_SPOT_TYPE_1 else BLINK_SPOT_TYPE_2
        var finalAbsolutePos = entranceRoom.getRealCoords(targetRelativePos)

        if (!isType1) {
            val gridX = (entranceRoom.roomComponents.first().x - (-185)) / 32
            val gridZ = (entranceRoom.roomComponents.first().z - (-185)) / 32

            var offsetX = 0
            var offsetZ = 0

            if (gridX == 0) offsetX = 1
            if (gridZ == 0) offsetZ = 1

            if (offsetX != 0 || offsetZ != 0) {
                finalAbsolutePos = finalAbsolutePos.offset(offsetX, 0, offsetZ)
            }
        }

        return finalAbsolutePos
    }

    fun executeInitialTP() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return

        val aotvSlot = PlayerUtils.findItemInHotbar("aspect of the void", "aotv") ?: run {
            modMessage("§c[BloodBlink] Can't Find AOTV")
            resetAll()
            return
        }
        PlayerUtils.setHotbarSlot(aotvSlot)

        val targetStandPos = getSmartBlinkSpot() ?: return
        val targetFloorBlock = targetStandPos.below()


        val eyePos = player.eyePosition
        val hitVec = Vec3(targetFloorBlock.x + 0.5, targetFloorBlock.y + 1.0, targetFloorBlock.z + 0.5)

        val diffX = hitVec.x - eyePos.x
        val diffY = hitVec.y - eyePos.y
        val diffZ = hitVec.z - eyePos.z
        val dist = sqrt(diffX * diffX + diffZ * diffZ)

        val targetYaw = (Math.toDegrees(atan2(diffZ, diffX)) - 90.0).toFloat()
        val targetPitch = (-Math.toDegrees(atan2(diffY, dist))).toFloat()

        PlayerUtils.setYawPitch(targetYaw, targetPitch)


        val wasSneaking = player.isShiftKeyDown

        if (!wasSneaking) PlayerUtils.setKeyState(InputKey.SNEAK, true)

        gameMode.useItem(player, InteractionHand.MAIN_HAND)
        player.swing(InteractionHand.MAIN_HAND)

        if (!wasSneaking) PlayerUtils.setKeyState(InputKey.SNEAK, false)
    }

    fun clipOutEntranceRoomPearls() {
        val player = mc.player ?: return

        // 1. Find Ender Pearls using your utility
        val pearlSlot = PlayerUtils.findItemInHotbar(Items.ENDER_PEARL) ?: run {
            modMessage("§c[BloodBlink] Missing Ender Pearls!")
            resetAll()
            return
        }

        // 2. Switch to pearls
        PlayerUtils.setHotbarSlot(pearlSlot)

        // 3. Initial aim: Look straight up
        PlayerUtils.setYawPitch(player.yRot, -90f)

        // 4. The Loop: Execute your existing throwPearl() 8 times, once per tick
        for (i in 0 until 8) {
            schedule(i * 3 + 2, true) {
                mc.execute{
                    val p = mc.player ?: return@execute
                    PlayerUtils.setYawPitch(p.yRot, -90f)

                    // 🌟 Reuse your original throwPearl function here!
                    throwPearl()
                }
            }
        }
        checkAndThrowPearl(33)
        modMessage("§4[BloodBlink] §fThrowing 8 pearls")
    }

    fun checkAndThrowPearl(ticks: Int = 5) {
        val player = mc.player ?: return
        schedule(ticks, true) {
            mc.execute {
                val pearlSlot = PlayerUtils.findItemInHotbar(Items.ENDER_PEARL)
                if (player.y <= 97.5 && pearlSlot != null) {
                    throwPearl()
                    checkAndThrowPearl()
                }
            }
        }
    }

    fun tpUpAtRoof() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return

        val aotvSlot = PlayerUtils.findItemInHotbar("aspect of the void", "aotv") ?: run {
            modMessage("§c[BloodBlink] Can't Find AOTV。")
            resetAll()
            return
        }
        PlayerUtils.setHotbarSlot(aotvSlot)

        modMessage("§4[BloodBlink] §fTP Straight Up 5 times")

        for (i in 0 until 10) {

            val p = mc.player ?: return

            PlayerUtils.setYawPitch(p.yRot, -90f)

            // 右鍵使用 AOTV
            gameMode.useItem(p, InteractionHand.MAIN_HAND)
            p.swing(InteractionHand.MAIN_HAND)

        }
    }

    fun tpToMid() {
        modMessage("§4[BloodBlink] §fTP to Center")
        tpToXZ(-89.0, -89.0)
    }

    fun enterBloodInstant() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val targetRoom = bloodRoom ?: run {
            modMessage("§c[BloodBlink] Can't Find Blood")
            resetAll()
            return
        }

        val aotvSlot = PlayerUtils.findItemInHotbar("aspect of the void", "aotv") ?: return
        val pearlSlot = PlayerUtils.findItemInHotbar(Items.ENDER_PEARL) ?: return

        val bloodX = targetRoom.roomComponents.first().x
        val bloodZ = targetRoom.roomComponents.first().z
        val centerPos = ScanUtils.getRoomCenter(bloodX, bloodZ)

        val localYaw = enterYaw // 先拷貝給本地不可變變數

        if (localYaw == null) {
            modMessage("§c[BloodBlink] Can't Get Starting Yaw")
            return
        }

        val snappedYaw = (round(localYaw / 90.0) * 90).toFloat()

        var startYaw = snappedYaw + 180f

        while (startYaw > 180f) startYaw -= 360f
        while (startYaw <= -180f) startYaw += 360f

        var offsetX = 0.0
        var offsetZ = 0.0
        val edgeTpDist = 3 * 12.0 // 飛出邊界 3 次，共移動 36 格

        // 根據算出來的逃脫角度，決定 3D 預測的位移量
        when (startYaw) {
            90f -> offsetX = -edgeTpDist         // 面向正西 (-X)
            -90f -> offsetX = edgeTpDist         // 面向正東 (+X)
            180f -> offsetZ = -edgeTpDist // 面向正北 (-Z)
            0f -> offsetZ = edgeTpDist           // 面向正南 (+Z)
            else -> {
                return
            }
        }

        val simulatedX = player.x + offsetX
        val simulatedZ = player.z + offsetZ

        val diffX = centerPos.x.toDouble() - simulatedX
        val diffZ = centerPos.z.toDouble() - simulatedZ
        val horizontalDist = sqrt(diffX * diffX + diffZ * diffZ)

        val horizontalTpCount = round(horizontalDist / 12.0).toInt()
        val targetYaw = (Math.toDegrees(atan2(diffZ, diffX)) - 90.0).toFloat()

        val finalY = player.y - 357

        val actualHorizontalDist = horizontalTpCount * 12.0
        val ratio = if (horizontalDist > 0) actualHorizontalDist / horizontalDist else 0.0
        val finalX = simulatedX + diffX * ratio
        val finalZ = simulatedZ + diffZ * ratio

        val upDiffX = centerPos.x.toDouble() - finalX
        val upDiffY = 67.0 - finalY - horizontalTpCount
        val upDiffZ = centerPos.z.toDouble() - finalZ

        val upDistXZ = sqrt(upDiffX * upDiffX + upDiffZ * upDiffZ)
        val upTotalDist = sqrt(upDiffX * upDiffX + upDiffY * upDiffY + upDiffZ * upDiffZ)

        val upYaw = if (upDistXZ > 0.1) (Math.toDegrees(atan2(upDiffZ, upDiffX)) - 90.0).toFloat() else targetYaw
        val upPitch = (-Math.toDegrees(atan2(upDiffY, upDistXZ))).toFloat()
        val upTpCount = ceil(upTotalDist / 12.0).toInt()

        modMessage("§4[BloodBlink] §fEnter Blood - Aiming: [X:${centerPos.x.toInt()}, Y: 67, Z:${centerPos.z.toInt()}]")

        PlayerUtils.setHotbarSlot(aotvSlot)

        fun spamAOTV(yaw: Float, pitch: Float, times: Int) {
            PlayerUtils.setYawPitch(yaw, pitch)
            for (i in 0 until times) {
                gameMode.useItem(player, InteractionHand.MAIN_HAND)
                player.swing(InteractionHand.MAIN_HAND)
            }
        }

        spamAOTV(startYaw, 0f, 3)             // 1. tpToEdge
        spamAOTV(startYaw, 90f, 30)           // 2. tpDown
        spamAOTV(targetYaw, 0f, horizontalTpCount) // 3. tpToBlood
        spamAOTV(upYaw, upPitch, upTpCount)         // 4. tpUpToBlood

        schedule(3, true) {
            mc.execute {
                val p = mc.player ?: return@execute
                PlayerUtils.setHotbarSlot(pearlSlot)
                PlayerUtils.setYawPitch(targetYaw, -90f)

                throwPearl()
                schedule(3, true) { mc.execute{
                    throwPearl()
                    spamAOTV(startYaw, 0f, 1)
                } }
            }

        }
    }

    fun tpToXZ(targetX: Double, targetZ: Double) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return

        val aotvSlot = PlayerUtils.findItemInHotbar("aspect of the void", "aotv") ?: run {
            modMessage("§c[BloodBlink] Can't Find AOTV")
            resetAll()
            return
        }
        PlayerUtils.setHotbarSlot(aotvSlot)

        val diffX = targetX - player.x
        val diffZ = targetZ - player.z
        val distance = sqrt(diffX * diffX + diffZ * diffZ)

        if (distance < 1.0) {
            modMessage("§e[BloodBlink] Already CLose to Coords")
            return
        }

        val targetYaw = (Math.toDegrees(atan2(diffZ, diffX)) - 90.0).toFloat()

        val tpCount = ceil(distance / 12.0).toInt()

        modMessage("§4[BloodBlink] §fAiming [X:${targetX.toInt()}, Z:${targetZ.toInt()}]！Distance: ${distance.toInt()}, TP $tpCount time(s)。")

        for (i in 0 until tpCount) {
            val p = mc.player ?: return

            PlayerUtils.setYawPitch(targetYaw, 0f)

            gameMode.useItem(p, InteractionHand.MAIN_HAND)
            p.swing(InteractionHand.MAIN_HAND)
        }
    }
}