package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.github.stivais.commodore.utils.GreedyString
import com.iq200.heigui.features.impl.skyblock.Vampire
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.modMessage


fun LiteralNode.setupRotationCommand() {
    literal("rotation") {
        runs { args: GreedyString ->
            val input = args.toString().trim()

            // 將字串以一個或多個空格分割成陣列
            val parts = input.split("\\s+".toRegex())

            // 檢查是否至少有輸入 3 個參數
            if (parts.size < 3) {
                modMessage("§cWrong Arguments!Usage: /hg rotation <yaw> <pitch> <speed>")
                return@runs
            }

            val yawStr = parts[0]
            val pitchStr = parts[1]
            val speedStr = parts[2]

            // 使用 toFloatOrNull()，非數字或 "null" 都會直接變成 null
            val yaw = yawStr.toFloatOrNull()
            val pitch = pitchStr.toFloatOrNull()
            val speed = speedStr.toFloatOrNull()

            // 防呆機制：速度必須是正數且不可為 null
            if (speed == null || speed <= 0f) {
                modMessage("§cRotationSpeed must be valid positive integer")
                return@runs
            }

            // 呼叫 PlayerUtils 執行旋轉
            PlayerUtils.smoothRotate(yaw, pitch, speed)

            modMessage("§a[Heigui] Start Rotating: Yaw=$yaw, Pitch=$pitch, Speed=$speed")
        }
    }

    literal("startimpelrotation") {
        runs { args: GreedyString ->
            val input = args.toString().trim()
            val pitch = input.toFloatOrNull()

            if (pitch == null) {
                modMessage("§cWrong Arguments! Usage: /hg startimpelrotation <pitch>")
                return@runs
            }

            // 直接呼叫 Vampire 的函數，這會連帶觸發狀態機，轉完後會執行點擊！
            Vampire.startImpelRotation(pitch)

            modMessage("§a[Heigui] Triggered Vampire Impel Rotation: Pitch=$pitch")
        }
    }
}