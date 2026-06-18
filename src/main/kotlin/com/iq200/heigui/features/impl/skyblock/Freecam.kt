package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.impl.KeybindSetting
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.camera.CameraHandler
import com.iq200.heigui.utils.camera.CameraPositionProvider
import com.iq200.heigui.utils.camera.ClientRotationHandler
import com.iq200.heigui.utils.camera.ClientRotationProvider
import com.iq200.heigui.utils.isDown
import com.iq200.heigui.utils.modMessage
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.sin

object Freecam : Module (
    name = "Freecam",
    description = "Free Camera",
    category = Category.SKYBLOCK
), CameraPositionProvider, ClientRotationProvider {
    private val toggleKey by KeybindSetting("Toggle Key", GLFW.GLFW_KEY_UNKNOWN, "Press to Toggle On/Off")

    private const val FREECAM_SPEED = 0.5

    var isFreecamActive = false
    private var wasKeyDown = false

    private var freecamPos = Vec3.ZERO


    init {

        on<TickEvent.Start> {
            if (!enabled) return@on
            val player = mc.player ?: return@on

            // 按鍵開關邏輯
            val isDown = toggleKey.isDown()
            if (mc.screen != null) {
                wasKeyDown = isDown
            } else {
                if (isDown && !wasKeyDown) {
                    if (isFreecamActive) stopFreecam() else startFreecam()
                }
                wasKeyDown = isDown
            }

            if (!isFreecamActive) return@on

            // 2. 處理靈魂的自由移動
            handleFreecamMovement()

            // 3. 強制加載相機周圍的 Chunk，避免看到虛空
            mc.level?.chunkSource?.updateViewCenter(freecamPos.x.toInt() shr 4, freecamPos.z.toInt() shr 4)

        }

        on<PacketEvent.Send> {
            if (!enabled || !isFreecamActive) return@on


            if (packet is ServerboundInteractPacket ||
                packet is ServerboundUseItemOnPacket ||
                packet is ServerboundUseItemPacket ||
                packet is ServerboundPlayerCommandPacket ||
                packet is ServerboundPlayerInputPacket) {
                cancel()
            }
        }
    }

    private fun startFreecam() {
        val player = mc.player ?: return


        // 初始化靈魂座標 (注意：不加 eyeHeight，因為 CameraHandler.getPos 會自動幫我們加上眼睛高度)
        freecamPos = Vec3(player.x, player.y, player.z)


        CameraHandler.registerProvider(this)
        ClientRotationHandler.registerProvider(this)
        isFreecamActive = true
        modMessage("Freecam Toggled On")
    }

    private fun stopFreecam() {
        isFreecamActive = false
        modMessage("Freecam Toggled Off")
    }

    // --- 自己算靈魂的移動 ---
    private fun handleFreecamMovement() {
        val yaw = ClientRotationHandler.clientYaw
        val pitch = ClientRotationHandler.clientPitch
        if (yaw.isNaN() || pitch.isNaN()) return

        var forward = 0.0
        var strafe = 0.0
        var vertical = 0.0

        // 讀取鍵盤輸入
        if (mc.options.keyUp.isDown) forward += 1.0
        if (mc.options.keyDown.isDown) forward -= 1.0
        if (mc.options.keyLeft.isDown) strafe += 1.0
        if (mc.options.keyRight.isDown) strafe -= 1.0
        if (mc.options.keyJump.isDown) vertical += 1.0
        if (mc.options.keyShift.isDown) vertical -= 1.0

        if (forward == 0.0 && strafe == 0.0 && vertical == 0.0) return

        // 根據目前的視角方向 (yaw) 旋轉向量，這樣按 W 才會往你看的地方飛
        val yawRad = Math.toRadians(yaw.toDouble())
        val cos = cos(yawRad)
        val sin = sin(yawRad)

        val moveX = (forward * -sin) + (strafe * -cos)
        val moveZ = (forward * cos) + (strafe * -sin)

        // 更新靈魂座標
        freecamPos = freecamPos.add(moveX * FREECAM_SPEED, vertical * FREECAM_SPEED, moveZ * FREECAM_SPEED)
    }

    // ============================================
    //  CameraPositionProvider 實作 (告訴大腦我要幹嘛)
    // ============================================
    override fun shouldOverridePosition() = isFreecamActive
    override fun getCameraPosition() = freecamPos
    override fun shouldBlockKeyboardMovement() = isFreecamActive // 鎖定真實玩家不能走路

    // ============================================
    //  ClientRotationProvider 實作 (告訴脫步系統)
    // ============================================
    override fun isClientRotationActive() = isFreecamActive
    override fun allowClientKeyInputs() = false // 不讓玩家按鍵盤轉頭或移動


    override fun shouldOverrideHitPos() = false
    override fun shouldOverrideHitRot() = false
    override fun getPosForHit(): Vec3 = Vec3.ZERO
    override fun getRotForHit(): Vec3 = Vec3.ZERO
}