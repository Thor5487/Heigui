package com.iq200.heigui.utils.camera

import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.events.CameraSetupEvent
import com.iq200.heigui.events.TurnPlayerEvent
import com.iq200.heigui.events.core.EventBus
import com.iq200.heigui.events.core.on
import com.iq200.heigui.utils.RotationUtils
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2
import kotlin.math.roundToInt

object ClientRotationHandler : CameraRotationProvider {
    var clientYaw = Float.NaN
        private set
    var clientPitch = Float.NaN
        private set

    private var desynced = false
    private val providers = mutableListOf<ClientRotationProvider>()

    private var lastRotationDeltaYaw = 0f
    private var forwardRemainder = 0f
    private var strafeRemainder = 0f
    var allowInputs = true
        private set
    private var lastPausedState = false

    fun setYaw(yaw: Float) {
        clientYaw = yaw
    }

    fun setPitch(pitch: Float) {
        clientPitch = pitch
    }

    fun registerProvider(provider: ClientRotationProvider) {
        if (!providers.contains(provider)) {
            providers.add(provider)
        }
    }

    init {
        EventBus.subscribe(this)

        on<CameraSetupEvent> {
            if (mc.player == null) return@on

            providers.removeAll {
                val inactive = !it.isClientRotationActive()
                if (inactive) it.onDesyncDisable()
                inactive
            }

            allowInputs = providers.all { it.allowClientKeyInputs() }
            val isNotPaused = providers.any { !it.isDesyncPaused() }

            if (providers.isNotEmpty()) {
                if (!(isNotPaused || lastPausedState)) {
                    providers.forEach { it.onDesyncPause() }
                }
                lastPausedState = !isNotPaused
            } else {
                lastPausedState = false
            }

            if (isNotPaused && !desynced) {
                if (clientYaw.isNaN()) clientYaw = mc.player!!.yRot
                if (clientPitch.isNaN()) clientPitch = mc.player!!.xRot
                CameraHandler.registerProvider(this@ClientRotationHandler)
            }

            if (!isNotPaused && desynced) {
                mc.player!!.yRotO = clientYaw
                mc.player!!.xRotO = clientPitch
                clientYaw = Float.NaN
                clientPitch = Float.NaN
            }
            desynced = isNotPaused
        }

        on<TurnPlayerEvent> {
            if (!isActive()) return@on
            handleTurnPlayer(d, dx, dy, smoothTurnX!!, smoothTurnY!!)
            cancel()
        }
    }

    @JvmStatic
    fun adjustInputsForRotation(inputs: Input): Input {
        // 1. 檢查是否要完全禁用鍵盤
        if (!allowInputs) return Input(false, false, false, false, false, false, false)

        // 2. 檢查是否有任何 Provider 不希望我們調整移動方向
        val shouldAdjust = providers.all { it.shouldAdjustMovement() }
        if (!shouldAdjust) {
            // 如果不調整，就直接回傳原始的輸入，這樣移動就會跟隨本體
            return inputs
        }

        // 3. 如果以上都通過了，才執行原本的視角跟隨移動邏輯
        val player = mc.player ?: return inputs
        if (!desynced || clientYaw.isNaN()) return inputs

        val moveVector: Vec2 = RotationUtils.constructMovementVector(inputs)
        if (moveVector.x == 0f && moveVector.y == 0f) {
            forwardRemainder = 0f
            strafeRemainder = 0f
            lastRotationDeltaYaw = clientYaw - player.yRot
            return inputs
        }

        val currentDeltaYaw = clientYaw - player.yRot
        val deltaYaw = currentDeltaYaw - lastRotationDeltaYaw
        if (deltaYaw != 0f) {
            val newRemainder: Vec2 = RotationUtils.rotateVector(forwardRemainder, strafeRemainder, deltaYaw)
            forwardRemainder = newRemainder.x
            strafeRemainder = newRemainder.y
        }

        lastRotationDeltaYaw = currentDeltaYaw
        val rotatedMovementVector: Vec2 = RotationUtils.rotateVector(moveVector.x, moveVector.y, currentDeltaYaw)
        val newForward = Mth.clamp(rotatedMovementVector.x - forwardRemainder, -1f, 1f)
        val newStrafe = Mth.clamp(rotatedMovementVector.y - strafeRemainder, -1f, 1f)

        val forwardsMovement = newForward.roundToInt().toFloat()
        val strafeMovement = newStrafe.roundToInt().toFloat()

        forwardRemainder = forwardsMovement - newForward
        strafeRemainder = strafeMovement - newStrafe
        return getInputsFromVec(forwardsMovement, strafeMovement, inputs)
    }

    private fun getInputsFromVec(forwards: Float, strafe: Float, inputs: Input): Input {
        return Input(
            forwards == 1f,
            forwards == -1f,
            strafe == 1f,
            strafe == -1f,
            inputs.jump(),
            inputs.shift(),
            inputs.sprint()
        )
    }

    fun handleTurnPlayer(d: Double, dx: Double, dy: Double, smoothTurnX: SmoothDouble, smoothTurnY: SmoothDouble) {
        val player = mc.player ?: return
        val options = mc.options
        val e = options.sensitivity().get() * 0.6 + 0.2
        val f = e * e * e
        val g = f * 8.0
        val j: Double
        val k: Double

        if (options.smoothCamera) {
            j = smoothTurnX.getNewDeltaValue(dx * g, d * g)
            k = smoothTurnY.getNewDeltaValue(dy * g, d * g)
        } else if (options.cameraType.isFirstPerson && player.isScoping) {
            smoothTurnX.reset()
            smoothTurnY.reset()
            j = dx * f
            k = dy * f
        } else {
            smoothTurnX.reset()
            smoothTurnY.reset()
            j = dx * g
            k = dy * g
        }
        turn(if (options.invertMouseX().get()) -j else j, if (options.invertMouseY().get()) -k else k)
    }

    fun turn(d: Double, e: Double) {
        if (clientYaw.isNaN() || clientPitch.isNaN()) return
        val f = (e * 0.15).toFloat()
        val g = (d * 0.15).toFloat()
        setPitch(Mth.clamp(clientPitch + f, -90.0f, 90.0f))
        setYaw(clientYaw + g)
    }

    fun syncServerRotationToClient() {
        val player = mc.player ?: return
        if (clientYaw.isNaN() || clientPitch.isNaN()) return
        player.yRot = clientYaw
        player.xRot = clientPitch
    }

    override fun shouldOverrideYaw() = desynced
    override fun shouldOverridePitch() = desynced
    override fun getYaw() = clientYaw
    override fun getPitch() = clientPitch
    override fun shouldBlockMouseMovement() = false

    fun reset() {
        clientYaw = Float.NaN
        clientPitch = Float.NaN
        desynced = false
        lastRotationDeltaYaw = 0f
        forwardRemainder = 0f
        strafeRemainder = 0f
        lastPausedState = false
        // 我們不需要手動清除 providers，因為 CameraHandler 會自動處理
    }
}