package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.TurnPlayerEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.camera.CameraRotationProvider
import com.iq200.heigui.utils.camera.ClientRotationProvider
import com.iq200.heigui.utils.noControlCodes
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.util.Mth
import kotlin.math.abs

object Vampire : Module (
    name = "Vampire",
    description = "Useful QOL for Vampire Slayer",
    category = Category.SKYBLOCK
), CameraRotationProvider, ClientRotationProvider {
    private val autoImpel by BooleanSetting("Auto Impel", false, "Auto Do Impel")
    private val rotationSpeed by NumberSetting("Speed", 40, 0, 100, 10,
        unit = "°/t", desc = "Rotation Speed For Click Up/Down").withDependency { autoImpel }
    private val snapTolerance by NumberSetting("Snap Tolerance", 3, 1, 10, 1,
        unit = "°", desc = "Angle tolerance to snap back to camera").withDependency { autoImpel }

    enum class ImpelState {
        IDLE,
        ROTATING,
        READY_TO_CLICK,
        RETURNING,
        HOLDING_SNEAK,
        HOLDING_JUMP
    }

    private var currentState: ImpelState = ImpelState.IDLE
    private var tickDelay = 0

    // --- Camera 視角分離相關變數 ---
    private var isCameraDecoupled = false
    private var camYaw = 0f
    private var camPitch = 0f

    // --- CameraRotationProvider 實作 ---
    // 當 isCameraDecoupled 為 true 時，畫面渲染將使用 getYaw() 與 getPitch()
    override fun shouldOverrideYaw() = isCameraDecoupled
    override fun shouldOverridePitch() = isCameraDecoupled
    override fun getYaw() = camYaw
    override fun getPitch() = camPitch
    // 阻擋原版的滑鼠視角轉動 (防止玩家滑鼠干擾正在執行 smoothRotate 的本體)
    override fun shouldBlockMouseMovement() = isCameraDecoupled

    // --- ClientRotationProvider 實作 ---
    override fun isClientRotationActive() = isCameraDecoupled
    override fun allowClientKeyInputs() = true

    init {
        // 1. 監聽封包 (Title / Subtitle)
        on<PacketEvent.Receive> {
            if (!autoImpel || currentState != ImpelState.IDLE) return@on

            var text = ""

            // 根據你使用的 Minecraft 版本，解析 Title 或 Subtitle 的文本
            if (packet is ClientboundSetTitleTextPacket) {
                text = packet.text.string.noControlCodes.lowercase()
            } else if (packet is ClientboundSetSubtitleTextPacket) {
                text = packet.text.string.noControlCodes.lowercase()
            }


            if (text.contains("impel:")) {
                when {
                    text.contains("click up") -> startImpelRotation(-90f)
                    text.contains("click down") -> startImpelRotation(90f)
                    text.contains("sneak") -> startImpelAction(ImpelState.HOLDING_SNEAK)
                    text.contains("jump") -> startImpelAction(ImpelState.HOLDING_JUMP)
                }
            }
        }

        // 2. 監聽 Tick，處理點擊邏輯與狀態重置
        on<TickEvent.Start> {
            if (!autoImpel || mc.player == null) return@on


            when (currentState) {
                ImpelState.ROTATING -> {
                    // 檢查 PlayerUtils 的旋轉是否已經結束
                    if (!PlayerUtils.isRotating) {
                        currentState = ImpelState.READY_TO_CLICK
                        tickDelay = 2 // 給予 2 Ticks 的緩衝，確保伺服器同步了玩家的視角
                    }
                }
                ImpelState.READY_TO_CLICK -> {
                    if (tickDelay > 0) {
                        tickDelay--
                        return@on
                    }

                    performClick()

                    PlayerUtils.smoothRotate(camYaw, camPitch, rotationSpeed.toFloat())
                    currentState = ImpelState.RETURNING
                }
                ImpelState.RETURNING -> {
                    val player = mc.player ?: return@on

                    // 計算本體與玩家實際視角 (Camera) 的差距
                    val yawDiff = abs(Mth.wrapDegrees(player.yRot - camYaw))
                    val pitchDiff = abs(player.xRot - camPitch)

                    // 如果在容忍度內，直接瞬間歸位並解除視角分離
                    if (yawDiff <= snapTolerance.toFloat() && pitchDiff <= snapTolerance.toFloat()) {
                        player.yRot = camYaw
                        player.xRot = camPitch
                        isCameraDecoupled = false // 解除分離
                        currentState = ImpelState.IDLE
                    } else {
                        // 在歸位過程中，玩家可能還在移動滑鼠，因此要持續更新 smoothRotate 的目標
                        PlayerUtils.smoothRotate(camYaw, camPitch, rotationSpeed.toFloat())
                    }
                }
                ImpelState.HOLDING_SNEAK -> {
                    if (tickDelay > 0) {
                        tickDelay--
                    } else {
                        mc.options.keyShift.isDown = false // 時間到釋放按鍵
                        currentState = ImpelState.IDLE
                    }
                }
                ImpelState.HOLDING_JUMP -> {
                    if (tickDelay > 0) {
                        tickDelay--
                    } else {
                        mc.options.keyJump.isDown = false // 時間到釋放按鍵
                        currentState = ImpelState.IDLE
                    }
                }
                ImpelState.IDLE -> {
                    // 閒置狀態不做事
                }
            }
        }

        on<TurnPlayerEvent> {
            if (isCameraDecoupled) {
                val options = mc.options

                // 套用原版 Minecraft 的滑鼠靈敏度公式
                val sensitivity = options.sensitivity().get() * 0.6 + 0.2
                val multiplier = sensitivity * sensitivity * sensitivity * 8.0

                // 計算實際要轉動的角度
                val deltaYaw = dx * multiplier
                val deltaPitch = dy * multiplier

                // 將計算後的角度加到虛擬攝影機上
                camYaw += deltaYaw.toFloat()
                camPitch = Mth.clamp(camPitch + deltaPitch.toFloat(), -90f, 90f) // 限制仰角與俯角不超過 90 度

                // 取消事件，阻止原版的轉動邏輯影響正在打王的本體
                cancel()
            }
        }
    }


    fun startImpelRotation(targetPitch: Float) {
        val player = mc.player ?: return

        // 啟動時，先記錄當下實際的視角給 Camera 使用
        camYaw = player.yRot
        camPitch = player.xRot
        isCameraDecoupled = true // 開啟分離，接管畫面渲染

        // 只轉動本體的 Pitch，Yaw 保持不變 (null)
        PlayerUtils.smoothRotate(null, targetPitch, rotationSpeed.toFloat())
        currentState = ImpelState.ROTATING
    }

    private fun startImpelAction(state: ImpelState) {
        currentState = state
        tickDelay = 3 // 設定按壓持續 3 個 Ticks

        if (state == ImpelState.HOLDING_SNEAK) {
            mc.options.keyShift.isDown = true
        } else if (state == ImpelState.HOLDING_JUMP) {
            mc.options.keyJump.isDown = true
        }
    }

    private fun performClick() {
        PlayerUtils.leftClick()
    }

    override fun onDisable() {
        // 模組關閉時的安全機制，強制重置所有狀態與按鍵
        currentState = ImpelState.IDLE
        tickDelay = 0
        mc.options.keyShift.isDown = false
        mc.options.keyJump.isDown = false
        mc.options.keyAttack.isDown = false
        PlayerUtils.smoothRotate(null, null, 0f)

        isCameraDecoupled = false
    }
}