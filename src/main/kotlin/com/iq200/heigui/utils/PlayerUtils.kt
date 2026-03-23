package com.iq200.heigui.utils

import com.iq200.heigui.Heigui.mc
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.StringUtil
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.Item

enum class InputKey {
    FORWARD, BACKWARD, LEFT, RIGHT, JUMP, SNEAK, SPRINT
}

// ==========================================
// 來自原本 Odin 的 Top-Level 函式
// ==========================================

fun playSoundSettings(soundSettings: Triple<String, Float, Float>) {
    val (soundName, volume, pitch) = soundSettings
    // 在官方映射中，Identifier 已改名為 ResourceLocation
    val identifier = Identifier.tryParse(StringUtil.filterText(soundName)) ?: return
    playSoundAtPlayer(SoundEvent.createVariableRangeEvent(identifier), volume, pitch)
}

fun playSoundAtPlayer(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) = mc.execute {
    mc.soundManager.playDelayed(SimpleSoundInstance.forUI(event, pitch, volume), 0)
}

fun setTitle(title: String) {
    mc.gui.setTimes(0, 20, 5)
    mc.gui.setTitle(Component.literal(title))
}

fun alert(title: String, playSound: Boolean = true) {
    setTitle(title)
    if (playSound) playSoundAtPlayer(SoundEvents.NOTE_BLOCK_PLING.value())
}

fun getPositionString(): String {
    // 這裡加上了 () 來適應最新的官方映射
    with(mc.player?.blockPosition() ?: BlockPos(0, 0, 0)) {
        return "x: $x, y: $y, z: $z"
    }
}


// ==========================================
// 來自原本 OdinAddon-IQ 的 PlayerUtils 物件
// ==========================================

object PlayerUtils {

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

    /**
     * @param key 要控制的按鍵 (例如 InputKey.SNEAK)
     * @param state true代表按下，false代表鬆開
     */
    fun setKeyState(key: InputKey, state: Boolean) {
        val player = mc.player ?: return
        val current = player.input.keyPresses

        // 建立一個全新的 Input，繼承舊狀態，唯獨把你指定的 key 替換成新的 state
        val newInput = Input(
            if (key == InputKey.FORWARD) state else current.forward,
            if (key == InputKey.BACKWARD) state else current.backward,
            if (key == InputKey.LEFT) state else current.left,
            if (key == InputKey.RIGHT) state else current.right,
            if (key == InputKey.JUMP) state else current.jump,
            if (key == InputKey.SNEAK) state else current.shift, // 注意底層名稱叫做 shift
            if (key == InputKey.SPRINT) state else current.sprint
        )

        player.connection.send(ServerboundPlayerInputPacket(newInput))

        if (key == InputKey.SNEAK) {
            player.isShiftKeyDown = state
        }
    }

    /**
     * @param yaw 左右旋轉角度 (Y軸)
     * @param pitch 上下俯仰角度 (X軸)
     */
    fun setYawPitch(yaw: Float, pitch: Float) {
        val player = mc.player ?: return

        // 這裡確保不用加上括號，因為這是直接寫入屬性
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
}