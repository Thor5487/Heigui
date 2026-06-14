package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.mixin.accessors.ILocalPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3

object NoRotate : Module (
    name = "NoRotate",
    description = "No Rotation For Etherwarp/Instant Transmission/Hyperion",
    category = Category.SKYBLOCK
) {
    private var lastAoteClickTime: Long = 0

    private const val VALID_WINDOW_MS = 500L

    init {
        on<PacketEvent.Send> {
            if (!enabled) return@on

            if (DungeonUtils.inDungeons && DungeonUtils.isFloor(7) && DungeonUtils.inBoss) return@on

            if (packet is ServerboundUseItemPacket) {
                val heldItemName = mc.player!!.mainHandItem.hoverName.string.lowercase()
                if (heldItemName.contains("aspect of the void") || heldItemName.contains("aspect of the end") || heldItemName.contains("hyperion")) {
                    lastAoteClickTime = System.currentTimeMillis()
                }
            }
        }

        on<PacketEvent.Receive> {
            if (!enabled) return@on

            if (packet is ClientboundPlayerPositionPacket) {
                val timeSinceClick = System.currentTimeMillis() - lastAoteClickTime

                if (timeSinceClick > VALID_WINDOW_MS) return@on

                val player = mc.player ?: return@on

                val old = PositionMoveRotation.of(player)
                val new = PositionMoveRotation.calculateAbsolute(old, packet.change, packet.relatives)

                player.setPos(new.position())
                player.deltaMovement = new.deltaMovement()

                val newOldPos = PositionMoveRotation.calculateAbsolute(
                    PositionMoveRotation(player.oldPosition(), Vec3.ZERO, player.yRotO, player.xRotO), packet.change, packet.relatives
                )

                player.xo = newOldPos.position().x.also { player.xOld = it }
                player.yo = newOldPos.position().y.also { player.yOld = it }
                player.zo = newOldPos.position().z.also { player.zOld = it }

                mc.connection?.send(ServerboundAcceptTeleportationPacket(packet.id))
                mc.connection?.send(ServerboundMovePlayerPacket.PosRot(player.x, player.y, player.z, new.yRot, new.xRot, false, false))

                (player as ILocalPlayer).setLastYaw(new.yRot)
                (player as ILocalPlayer).setLastPitch(new.xRot)

                cancel()

                lastAoteClickTime = 0
            }
        }
    }
}