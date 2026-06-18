package com.iq200.heigui.utils.camera

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.CameraSetupEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.TurnPlayerEvent
import com.iq200.heigui.events.core.on
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.client.Camera
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

object CameraHandler {
    private const val YAW_FLAG: Byte = 0x01
    private const val PITCH_FLAG: Byte = 0x02
    private const val POSITION_FLAG: Byte = 0x04
    private const val BLOCK_KEYS_FLAG: Byte = 0x08
    private const val BLOCK_MOUSE_FLAG: Byte = 0x10
    private const val HIT_ROT_FLAG: Byte = 0x20
    private const val HIT_POS_FLAG: Byte = 0x40

    private val providers = mutableListOf<CameraProvider>()

    private var yaw = 0.0f
    private var pitch = 0.0f
    var lastYaw = 0.0f
    var lastPitch = 0.0f
    var cameraPos: Vec3? = Vec3.ZERO
        private set

    private var hitPos: Vec3? = Vec3.ZERO
    private var hitRot: Vec3 = Vec3.ZERO
    private val cameraBlockPos = BlockPos.MutableBlockPos()
    private var flags: Byte = 0



    init {
        on<CameraSetupEvent> {
            flags = 0
            if (providers.isEmpty()) return@on

            providers.removeAll { !it.isActive() }
            if (providers.isEmpty()) return@on

            if (providers.any { it.shouldBlockKeyboardMovement() }) flags = (flags.toInt() or BLOCK_KEYS_FLAG.toInt()).toByte()
            if (providers.any { it.shouldBlockMouseMovement() }) flags = (flags.toInt() or BLOCK_MOUSE_FLAG.toInt()).toByte()

            val sortedProviders = providers.sortedBy { it.priority }

            val positionProvider = sortedProviders.firstOrNull { it.shouldOverridePosition() }
            val yawProvider = sortedProviders.firstOrNull { it.shouldOverrideYaw() }
            val pitchProvider = sortedProviders.firstOrNull { it.shouldOverridePitch() }
            val hitPosProvider = sortedProviders.firstOrNull { it.shouldOverrideHitPos() }
            val hitRotProvider = sortedProviders.firstOrNull { it.shouldOverrideHitRot() }

            positionProvider?.let {
                cameraPos = it.getCameraPosition()
                if (cameraPos != null) {
                    cameraBlockPos.set(cameraPos!!.x, cameraPos!!.y, cameraPos!!.z)
                    flags = (flags.toInt() or POSITION_FLAG.toInt()).toByte()
                }
            }

            yawProvider?.let {
                yaw = it.getYaw()
                flags = (flags.toInt() or YAW_FLAG.toInt()).toByte()
            }

            pitchProvider?.let {
                pitch = it.getPitch()
                flags = (flags.toInt() or PITCH_FLAG.toInt()).toByte()
            }

            hitPosProvider?.let {
                hitPos = it.getPosForHit()
                flags = (flags.toInt() or HIT_POS_FLAG.toInt()).toByte()
            }

            hitRotProvider?.let {
                hitRot = it.getRotForHit()
                flags = (flags.toInt() or HIT_ROT_FLAG.toInt()).toByte()
            }
        }

        on<TickEvent.End> {
            val player = mc.player ?: return@on
            lastYaw = if (hasYaw()) yaw else player.yRot
            lastPitch = if (hasPitch()) pitch else player.xRot
        }

        on<TurnPlayerEvent> {
            if ((flags.toInt() and BLOCK_MOUSE_FLAG.toInt()) != 0) {
                cancel()
            }
        }
    }



    fun registerProvider(provider: CameraProvider) {
        if (!providers.contains(provider)) {
            providers.add(provider)
        }
    }

    // 這些方法是提供給 Mixin 呼叫的 Hook
    @JvmStatic
    fun onGetCameraPos(cir : CallbackInfoReturnable<Vec3>) {
        if ((flags.toInt() and POSITION_FLAG.toInt()) == 0 || cameraPos == null) return
        cir.setReturnValue(cameraPos!!)
    }

    @JvmStatic
    fun onGetCameraRotation(instance: Camera, yRot: Float, xRot: Float, original: Operation<Void>) {
        original.call(
            instance,
            if ((flags.toInt() and YAW_FLAG.toInt()) == 0) yRot else yaw,
            if ((flags.toInt() and PITCH_FLAG.toInt()) == 0) xRot else pitch
        )
    }

    @JvmStatic
    fun onPrePollInputs(inputs: Input): Input {
        if ((flags.toInt() and BLOCK_KEYS_FLAG.toInt()) == 0) return inputs
        return Input(false, false, false, false, false, false, false)
    }

    @JvmStatic
    fun onGetPositionForHit(vec: Vec3): Vec3 {
        // 如果沒有設定 HIT_POS_FLAG，就回傳原本肉體的位置
        if ((flags.toInt() and HIT_POS_FLAG.toInt()) == 0 || hitPos == null) return vec
        return hitPos!!
    }

    @JvmStatic
    fun onGetRotationForHit(vec: Vec3): Vec3 {
        // 如果沒有設定 HIT_ROT_FLAG，就回傳原本肉體的視角
        if ((flags.toInt() and HIT_ROT_FLAG.toInt()) == 0) return vec
        return hitRot
    }

    @JvmStatic
    fun hasAnyRotation(): Boolean {
        // 檢查 flags 裡面是否包含 PITCH_FLAG 或 YAW_FLAG
        return (flags.toInt() and (PITCH_FLAG.toInt() or YAW_FLAG.toInt())) != 0
    }


    fun hasYaw(): Boolean = (flags.toInt() and YAW_FLAG.toInt()) != 0
    fun hasPitch(): Boolean = (flags.toInt() and PITCH_FLAG.toInt()) != 0

    @JvmStatic
    fun getPitch(original: Float): Float = if (hasPitch()) pitch else original

    @JvmStatic
    fun getYaw(original: Float): Float = if (hasYaw()) yaw else original

    @JvmStatic
    fun getPos(original: Vec3, partialTickTime: Float, eyeHeightOld: Float, eyeHeight: Float): Vec3 {
        if ((flags.toInt() and POSITION_FLAG.toInt()) == 0 || cameraPos == null) return original
        return cameraPos!!.add(0.0, Mth.lerp(partialTickTime.toDouble(), eyeHeightOld.toDouble(), eyeHeight.toDouble()), 0.0)
    }
}