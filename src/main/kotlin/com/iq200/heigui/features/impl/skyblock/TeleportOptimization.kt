package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.PlayerInputEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.*
import com.iq200.heigui.utils.camera.CameraHandler
import com.iq200.heigui.utils.camera.CameraPositionProvider
import com.iq200.heigui.utils.skyblock.EtherUtils
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils
import com.iq200.heigui.utils.skyblock.dungeon.tiles.Room
import com.iq200.heigui.utils.skyblock.dungeon.tiles.RoomType
import com.iq200.mixin.accessors.ILocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.*
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo


//From RSM
object TeleportOptimization : Module (
    name = "TeleportOptimization",
    description = "NoRotate & Zeroping Camera",
    category = Category.SKYBLOCK
), CameraPositionProvider {

    private val noRotateEnabled by BooleanSetting("NoRotate", false, "No Rotation after TP (Hype/AOTV/Etherwarp)")
    private val zpcmEnabled by BooleanSetting("Zeroping Camera", false, "Visually 0 Ping on TP (Hype/AOTV/Etherwarp) Note: Require NoRotate Enabled").withDependency { noRotateEnabled }
    private val timeOutMs by NumberSetting("Timeout", 500, 100, 1000, 50, unit = "ms", desc = "timeout for zeroping camera").withDependency { zpcmEnabled }

    private val noRotatePackets = mutableListOf<ClientboundPlayerPositionPacket>()
    private val noRotateSent = mutableListOf<Long>()

    private var renderPos: Vec3? = null
    private val zpcmSent = mutableListOf<Vec3>()
    private var lastWIMP: Long = 0

    private val ignoredBlocks = listOf(
        ChestBlock::class.java, EnderChestBlock::class.java, TrappedChestBlock::class.java,
        LeverBlock::class.java, ButtonBlock::class.java, HopperBlock::class.java,
        AnvilBlock::class.java, DropperBlock::class.java, DispenserBlock::class.java,
        CauldronBlock::class.java
    )

    init {
        on<TickEvent.Start> {
            val now = System.currentTimeMillis()
            noRotateSent.removeIf{now - it >= timeOutMs}

            if (noRotateSent.isEmpty() && renderPos != null) {
                renderPos = null
            }
        }

        on<PlayerInputEvent.Use> {
            if (!noRotateEnabled || !isRoomAllowed()) return@on
            val stack = mc.player?.mainHandItem ?: return@on

            if (!isTpItem(stack)) return@on

            if (result is BlockHitResult) {
                val hitBlock = mc.level?.getBlockState(result.blockPos)?.block
                if (hitBlock != null && isIgnored(hitBlock)) return@on
            }

            if (zpcmEnabled) {
                checkZpcm(stack, yRot, xRot)
            }
        }

        on<PacketEvent.Send> {
            if (!noRotateEnabled || !isRoomAllowed()) return@on

            if (packet is ServerboundUseItemPacket) {
                val stack = mc.player?.getItemInHand(packet.hand) ?: return@on
                if (isTpItem(stack)) {
                    noRotateSent.add(System.currentTimeMillis())
                }
            } else if (packet is ServerboundUseItemOnPacket) {
                val stack = mc.player?.getItemInHand(packet.hand) ?: return@on
                val level = mc.level ?: return@on
                val block = level.getBlockState(packet.hitResult.blockPos).block
                if (!isIgnored(block) && isTpItem(stack)) {
                    noRotateSent.add(System.currentTimeMillis())
                }
            }
        }

        on<PacketEvent.Receive> {
            if (!noRotateEnabled || packet !is ClientboundPlayerPositionPacket) return@on
            val player = mc.player ?: return@on
            val connection = mc.connection ?: return@on

            val startPos = PositionMoveRotation.of(player)
            val newPos = PositionMoveRotation.calculateAbsolute(startPos, packet.change(), packet.relatives())
            // ZPCM 邏輯：就算不是我們觸發的 TP，只要設定開啟 ZPCM，就去跑比對邏輯
            if (zpcmEnabled) {
                handleZpcm(newPos)
            }

            // NoRotate 邏輯：判斷這是不是我們預期的武器傳送
            if (noRotateSent.isNotEmpty()) {
                noRotateSent.removeAt(0)
                noRotatePackets.add(packet)
            }

        }
    }

    @JvmStatic
    fun handleTp(packet: ClientboundPlayerPositionPacket, connection: Connection, ci: CallbackInfo) {
        // 如果這個封包不在我們的攔截清單裡（例如普通的 /warp），就放行給原版處理
        if (!noRotatePackets.contains(packet)) return

        noRotatePackets.remove(packet)

        val player = mc.player ?: return
        val startPos = PositionMoveRotation.of(player)
        val newPos = PositionMoveRotation.calculateAbsolute(startPos, packet.change, packet.relatives)

        player.setPos(newPos.position())
        player.deltaMovement = newPos.deltaMovement()

        val oldPlayerPos = PositionMoveRotation(player.oldPosition(), Vec3.ZERO, player.yRotO, player.xRotO)
        val newOldPlayerPos = PositionMoveRotation.calculateAbsolute(oldPlayerPos, packet.change, packet.relatives)

        player.setOldPosAndRot(newOldPlayerPos.position(), player.yRotO, player.xRotO)

        connection.send(ServerboundAcceptTeleportationPacket(packet.id))
        connection.send(ServerboundMovePlayerPacket.PosRot(player.x, player.y, player.z, newPos.yRot(), newPos.xRot(), false, false))

        val accessor = player as ILocalPlayer
        accessor.setYRotLast(newPos.yRot())
        accessor.setXRotLast(newPos.xRot())

        ci.cancel()
    }

    private fun handleZpcm(newPos : PositionMoveRotation) {
        if (zpcmSent.isEmpty()) {
            renderPos = null
            return
        }

        val old : Vec3 = zpcmSent.removeFirst()
        val correct =
            old.x() == newPos.position().x() && old.y() == newPos.position().y() && old.z() == newPos.position().z()
        if (!correct || zpcmSent.isEmpty()) {
            zpcmSent.clear()
            renderPos = null
        }
    }



    private fun isIgnored(block: Block): Boolean {
        return ignoredBlocks.any { it.isInstance(block) }
    }

    private fun isTpItem(stack: ItemStack): Boolean {
        val id = stack.itemId
        if (id.containsOneOf(
            "ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID", "ETHERWARP_CONDUIT",
            "ASPECT_OF_THE_LEECH_1", "ASPECT_OF_THE_LEECH_2", "ASPECT_OF_THE_LEECH_3",
        )) return true

        if (id.containsOneOf("NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA")
            && stack.customData.getListOrEmpty("ability_scroll").size == 3)
            return true

        return false
    }

    private fun checkZpcm(stack: ItemStack, yaw: Float, pitch: Float) {
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (!isTpItem(stack) || !isRoomAllowedZPEW()) return

        if (mc.hitResult is BlockHitResult) {
            val hitBlock = level.getBlockState((mc.hitResult as BlockHitResult).blockPos).block
            if (isIgnored(hitBlock)) return
        }

        val sneaking = player.isShiftKeyDown
        val currentPos = renderPos ?: player.position()
        val eyePos = currentPos.add(0.0, EtherUtils.getEyeHeight().toDouble(), 0.0)

        if (sneaking && stack.isEtherwarpItem() && zpcmEnabled) {
            val ether = EtherUtils.getEtherPosFromOrigin(eyePos, yaw, pitch, 57 + stack.getTunerDistance())
            val targetBlock = ether.first ?: return
            if (!ether.second) return

            renderPos = Vec3(targetBlock.x + 0.5, targetBlock.y + 1.05, targetBlock.z + 0.5)

            CameraHandler.registerProvider(this)
            zpcmSent.add(renderPos!!)
        } else if (!sneaking && zpcmEnabled) {
            val wimp = isWitherImpactItem(stack)
            val now = System.currentTimeMillis()
            if (wimp && (now - lastWIMP < 125L)) return

            val distance = getTpDistance(stack)
            if (distance == 0) return

            // 預測傳送目標
            val prediction = EtherUtils.predictTeleport(distance.toInt(), currentPos, yaw, pitch) ?: return

            // 修正目標 (將 feet 位置轉為可站立的中心)
            var target = prediction.subtract(0.0, 1.0, 0.0)
            target = resolveZptpTarget(target) ?: return

            // 如果位置沒變，直接 return
            if (target.distanceToSqr(currentPos) < 1.0) return

            renderPos = target
            CameraHandler.registerProvider(this)
            zpcmSent.add(renderPos!!)

            if (wimp) lastWIMP = now
        }
    }


    private fun isWitherImpactItem(item: ItemStack): Boolean {
        val itemId: String = item.itemId
        if (!itemId.containsOneOf("NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA")) {
            return false
        }

        return item.customData.getListOrEmpty("ability_scroll").size == 3
    }

    private fun getTpDistance(item: ItemStack): Int {
        return when (item.itemId) {
            "ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID" -> 8 + item.getTunerDistance()
            "ASPECT_OF_THE_LEECH_1" -> 3
            "ASPECT_OF_THE_LEECH_2" -> 4
            "ASPECT_OF_THE_LEECH_3" -> 5
            "NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA" -> if (item.customData.getListOrEmpty("ability_scroll").size == 3
            ) 10 else 0

            else -> 0
        }
    }

    private fun resolveZptpTarget(target: Vec3): Vec3? {
        if (isSafeZptpTarget(target)) return target

        val above: Vec3 = target.add(0.0, 1.0, 0.0)
        return if (isSafeZptpTarget(above)) above else null
    }

    private fun isSafeZptpTarget(target: Vec3): Boolean {
        if (mc.level == null) return false

        val feet: BlockPos = target.toBlockPos()
        if (!mc.level!!.hasChunk(feet.x shr 4, feet.z shr 4)) return false

        val head = feet.above()
        return mc.level!!.getBlockState(feet).getCollisionShape(mc.level!!, feet).isEmpty
                && mc.level!!.getBlockState(head).getCollisionShape(mc.level!!, head).isEmpty
    }

    private fun isRoomAllowedZPEW(): Boolean {
        val room: String? = if (ScanUtils.currentRoom == null) null else ScanUtils.currentRoom!!.data.name
        return room == null || !room.containsOneOf("Boulder", "Teleport Maze") && ScanUtils.currentRoom!!.data
            .type !== RoomType.TRAP
    }

    private fun isRoomAllowed(): Boolean {
        val room = ScanUtils.currentRoom
        return room == null || !room.data.name.containsOneOf(
            "Boulder",
            "Teleport Maze"
        ) && room.data.type !== RoomType.TRAP
    }

    private fun isRoomAllowing(room: Room?): Boolean {
        return room == null || !room.data.name.containsOneOf("Teleport Maze", "Boulder")
    }

    // ==========================================
    // CameraPositionProvider 介面實作區
    // ==========================================

    override fun isActive(): Boolean {
        return shouldOverridePosition()
    }

    override fun shouldOverridePosition(): Boolean {
        // 嚴格遵守條件：必須開啟 ZPCM + 開啟 NoRotate + 已經有算好預判座標
        return enabled && zpcmEnabled && renderPos != null
    }

    override fun getCameraPosition(): Vec3? {
        return renderPos
    }

    override fun shouldOverrideHitPos(): Boolean {
        // 讓你在預判的相機位置可以左鍵/右鍵方塊與實體
        return zpcmEnabled && renderPos != null
    }

    override fun shouldOverrideHitRot(): Boolean {
        return false
    }

    override fun shouldBlockKeyboardMovement(): Boolean {
        return false
    }

    override fun getPosForHit(): Vec3? {
        return renderPos
    }

    override fun getRotForHit(): Vec3 {
        return Vec3.ZERO
    }
}