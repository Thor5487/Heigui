package com.iq200.heigui.utils

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.EventBus
import com.iq200.heigui.events.core.on
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.util.StringUtil
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.Item
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

enum class InputKey {
    FORWARD, BACKWARD, LEFT, RIGHT, JUMP, SNEAK, SPRINT
}


fun playSoundSettings(soundSettings: Triple<String, Float, Float>) {
    val (soundName, volume, pitch) = soundSettings

    val identifier = Identifier.tryParse(StringUtil.filterText(soundName)) ?: return
    playSoundAtPlayer(SoundEvent.createVariableRangeEvent(identifier), volume, pitch)
}

fun playSoundAtPlayer(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) = mc.execute {
    mc.soundManager.playDelayed(SimpleSoundInstance.forUI(event, pitch, volume), 0)
}

fun setTitle(title: String) {
    mc.gui.hud.setTimes(0, 20, 5)
    mc.gui.hud.setTitle(Component.literal(title))
}

fun alert(title: String, playSound: Boolean = true) {
    setTitle(title)
    if (playSound) playSoundAtPlayer(SoundEvents.NOTE_BLOCK_PLING.value())
}

fun getPositionString(): String {

    with(mc.player?.blockPosition() ?: BlockPos(0, 0, 0)) {
        return "x: $x, y: $y, z: $z"
    }
}



object PlayerUtils {
    const val SNEAK_EYE_HEIGHT = 1.54

    fun findItemInHotbar(vararg itemNames: String): Int? {
        val player = mc.player ?: return null
        return (0..8).find { slot ->
            val name = player.inventory.getItem(slot).hoverName.string.lowercase()
            itemNames.any { keyword -> name.contains(keyword.lowercase()) }
        }
    }

    fun findItemInHotbar(vararg items: Item): Int? {
        val player = mc.player ?: return null
        return (0..8).find { slot ->
            // Get the base Item instance of the stack in this slot
            val slotItem = player.inventory.getItem(slot).item

            // Check if the item in the slot matches any of the requested items
            items.contains(slotItem)
        }
    }

    fun setHotbarSlot(slot: Int) {
        val player = mc.player ?: return
        if (slot in 0..8) {
            player.inventory.selectedSlot = slot
        }
    }





    fun setKeyState(key: InputKey, state: Boolean) {
        val options = mc.options
        val player = mc.player ?: return


        val keyMapping: KeyMapping? = when (key) {
            InputKey.FORWARD -> options.keyUp
            InputKey.BACKWARD -> options.keyDown
            InputKey.LEFT -> options.keyLeft
            InputKey.RIGHT -> options.keyRight
            InputKey.JUMP -> options.keyJump
            InputKey.SNEAK -> options.keyShift
            InputKey.SPRINT -> options.keySprint

            else -> null
        }

        if (keyMapping != null) {
            keyMapping.isDown = state
        }

        val current = player.input.keyPresses

        val newInput = Input(
            if (key == InputKey.FORWARD) state else current.forward,
            if (key == InputKey.BACKWARD) state else current.backward,
            if (key == InputKey.LEFT) state else current.left,
            if (key == InputKey.RIGHT) state else current.right,
            if (key == InputKey.JUMP) state else current.jump,
            if (key == InputKey.SNEAK) state else current.shift,
            if (key == InputKey.SPRINT) state else current.sprint
        )

        player.input.keyPresses = newInput
        mc.connection?.send(ServerboundPlayerInputPacket(newInput))
        if (key == InputKey.SNEAK) {
            player.isShiftKeyDown = state
        }
    }

    fun leftClick() {
        val key = mc.options.keyAttack
        (key as KeyMappingAccessor).clickCount++
    }

    fun rightClick() {
        val key = mc.options.keyUse
        (key as KeyMappingAccessor).clickCount++
    }





    fun setYawPitch(yaw: Float, pitch: Float) {
        val player = mc.player ?: return


        player.yRot = yaw
        player.xRot = pitch
    }

    fun countItemInHotbar(item: Item): Int {
        val player = mc.player ?: return 0
        // Only scan slots 0 to 8
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (stack.item == item) {
                return stack.count
            }
        }
        return 0
    }

    fun isMovementKeysPressed(): Boolean {
        val options = mc.options

        return options.keyUp.isDown ||
                options.keyDown.isDown ||
                options.keyLeft.isDown ||
                options.keyRight.isDown ||
                options.keyJump.isDown
    }

    fun getRotationsTo(start: Vec3, target: Vec3): Pair<Float, Float>{
        val dx = target.x - start.x
        val dy = target.y - start.y
        val dz = target.z - start.z
        val dist = sqrt(dx * dx + dz * dz)

        val yaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, dist))).toFloat()

        return Pair(yaw, pitch)
    }



    enum class RotationMode {
        INACTIVE,
        NORMAL,
        BODY_ONLY
    }

    private var rotationMode = RotationMode.INACTIVE
    var isRotating: Boolean
        get() = rotationMode != RotationMode.INACTIVE
        private set(value) {
            if (!value) rotationMode = RotationMode.INACTIVE
        }

    private var targetYaw: Float? = null
    private var targetPitch: Float? = null
    private var rotationSpeed: Float = 0f
    private var isFirstTick = true


    private var bodyYaw: Float = 0f
    private var bodyPitch: Float = 0f

    private var targetProvider: (() -> Pair<Float?, Float?>)? = null






    fun smoothRotate(
        yaw: Float?,
        pitch: Float?,
        speed: Float,
        bodyOnly: Boolean = false,
        targetProvider: (() -> Pair<Float?, Float?>)? = null
    ) {
        val player = mc.player ?: return
        if (yaw == null && pitch == null && targetProvider == null) {
            stopRotation()
            return
        }


        this.targetProvider = targetProvider
        this.rotationSpeed = speed


        if (targetProvider == null) {
            this.targetYaw = yaw?.let { Mth.wrapDegrees(it) }
            this.targetPitch = pitch?.coerceIn(-90f, 90f)
        }
        else {


            this.targetYaw = null
            this.targetPitch = null
        }


        rotationMode = if (bodyOnly) RotationMode.BODY_ONLY else RotationMode.NORMAL

        this.isFirstTick = true


        bodyYaw = player.yRot
        bodyPitch = player.xRot
    }

    fun stopRotation() {
        rotationMode = RotationMode.INACTIVE
        targetProvider = null
    }


    fun onAiStep() {
        if (rotationMode != RotationMode.BODY_ONLY) return
        val player = mc.player ?: return

        handleRotationLogic()


        if (targetYaw != null) {
            player.yRot = bodyYaw
        } else {

            bodyYaw = player.yRot
        }


        if (targetPitch != null) {
            player.xRot = bodyPitch
        } else {
            bodyPitch = player.xRot
        }
    }


    init {
        EventBus.subscribe(this)
        on<TickEvent.Start> {
            if (rotationMode != RotationMode.NORMAL) return@on
            val player = mc.player ?: return@on

            handleRotationLogic()


            if (targetYaw != null) {
                player.yRot = bodyYaw
            } else {
                bodyYaw = player.yRot
            }

            if (targetPitch != null) {
                player.xRot = bodyPitch
            } else {
                bodyPitch = player.xRot
            }
        }
    }

    private fun handleRotationLogic() {
        if (isFirstTick) {
            isFirstTick = false
            return
        }

        targetProvider?.let {
            val (dynamicYaw, dynamicPitch) = it()
            targetYaw = dynamicYaw
            targetPitch = dynamicPitch
        }


        val maxStep = rotationSpeed
        var yawDone = true
        var pitchDone = true

        targetYaw?.let { tYaw ->
            bodyYaw = Mth.approachDegrees(bodyYaw, tYaw, maxStep)
            if (abs(Mth.wrapDegrees(tYaw - bodyYaw)) > 0.01f) yawDone = false
        }

        targetPitch?.let { tPitch ->
            bodyPitch = Mth.approach(bodyPitch, tPitch, maxStep)
            if (abs(tPitch - bodyPitch) > 0.01f) pitchDone = false
        }

        if (yawDone && pitchDone) {
            stopRotation()
        }
    }

}