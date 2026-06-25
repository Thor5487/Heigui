package com.iq200.heigui.events

import com.iq200.heigui.events.core.CancellableEvent
import com.iq200.heigui.events.core.Event
import com.iq200.heigui.utils.render.RenderConsumer
import com.iq200.heigui.utils.skyblock.dungeon.tiles.Room
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.rendering.v1.world.AbstractWorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.util.SmoothDouble
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW

class InputEvent(val key: InputConstants.Key, val action : Int) : CancellableEvent() {
    val isPress: Boolean
        get() = action == GLFW.GLFW_PRESS

    val isRelease: Boolean
        get() = action == GLFW.GLFW_RELEASE

    val isRepeat: Boolean
        get() = action == GLFW.GLFW_REPEAT
} // better mixin is prob ideal no need for cancellable

class BlockUpdateEvent(val pos: BlockPos, val old: BlockState, val updated: BlockState) : Event

class BlockInteractEvent(val pos: BlockPos) : CancellableEvent()
class EntityInteractEvent(val pos: Vec3, val entity: Entity) : CancellableEvent()

class CameraSetupEvent : Event

class TurnPlayerEvent(val d: Double = 0.0, val dx: Double = 0.0, val dy: Double = 0.0, val smoothTurnX: SmoothDouble? = null,
                      val smoothTurnY: SmoothDouble? = null) : CancellableEvent()

class ChatPacketEvent(val value: String, val component: Component) : Event // mixin instead of packet (still needs to run before vanilla processing for hideMessage()
class MessageSentEvent(val message: String) : CancellableEvent()

class RenderBossBarEvent(val bossBar: BossEvent) : CancellableEvent()

class RoomEnterEvent(val room: Room?) : CancellableEvent()

interface SecretPickupEvent : Event { // all are currently packet based but can probably use mixins
    class Interact(val blockPos: BlockPos, val blockState: BlockState) : SecretPickupEvent
    class Item(val entity: ItemEntity) : SecretPickupEvent
    class Bat(val packet: ClientboundSoundPacket) : SecretPickupEvent
}

interface TickEvent : Event {
    class Start(val world: ClientLevel) : TickEvent
    class End(val world: ClientLevel) : TickEvent
    object Server : TickEvent
}

interface WorldEvent : Event {
    object Load : WorldEvent
    object Unload : WorldEvent
}

abstract class RenderEvent(open val context: AbstractWorldRenderContext) : Event {
    class Extract(override val context: WorldExtractionContext, val consumer: RenderConsumer) : RenderEvent(context)
    class Last(override val context: WorldRenderContext) : RenderEvent(context)
}

abstract class PartyEvent(val members: List<String>) : Event {
    class Leave(members: List<String>) : PartyEvent(members)
}

abstract class PacketEvent(val packet: Packet<*>) : CancellableEvent() { // ideally used less
    class Receive(packet: Packet<*>) : PacketEvent(packet)
    class Send(packet: Packet<*>) : PacketEvent(packet)
}

abstract class PlayerInputEvent : CancellableEvent() {
    class Attack(
        val result : HitResult
    ) : PlayerInputEvent()

    class Use(
        val result : HitResult?,
        val yRot : Float,
        val xRot : Float
    ) : PlayerInputEvent()
}
