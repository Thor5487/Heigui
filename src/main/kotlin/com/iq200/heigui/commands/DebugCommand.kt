package com.iq200.heigui.commands

import com.github.stivais.commodore.nodes.LiteralNode
import com.github.stivais.commodore.utils.GreedyString
import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.utils.InputKey
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.modMessage
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

fun LiteralNode.setupDebugCommand() {
    literal("set") {
        runs { args: GreedyString ->
            val player = mc.player ?: return@runs

            // 1. 取得字串並去除頭尾多餘的空白
            // (注意: 如果你的 GreedyString 是一個物件，請根據你的框架使用 args.string 或 args.value。
            // 這裡假設它可以直接轉成 String)
            val input = args.toString().trim()

            // 2. 用正規表達式 "\\s+" 切割空白 (防止玩家多打了幾個空格)
            val parts = input.split("\\s+".toRegex())

            // 3. 防呆：檢查有沒有輸入兩個數字
            if (parts.size < 2) {
                modMessage("§c[Debug] 參數不足！用法: /<指令前綴> set <yaw> <pitch>")
                return@runs
            }

            // 4. 安全地將字串解析為 Float
            val targetYaw = parts[0].toFloatOrNull()
            val targetPitch = parts[1].toFloatOrNull()

            // 防呆：如果玩家輸入的不是數字 (例如輸入了 set abc def)
            if (targetYaw == null || targetPitch == null) {
                modMessage("§c[Debug] 請輸入有效的數字！例如: set 90 -45")
                return@runs
            }

            // ==========================================
            // 🌟 5. 核心邏輯：設定視角
            // ==========================================
            // 直接呼叫你原本寫好的 PlayerUtils 工具！
            PlayerUtils.setYawPitch(targetYaw, targetPitch)

            // 6. 給予成功提示
            modMessage("§a[Debug] 視角已強制設定為 Yaw: $targetYaw, Pitch: $targetPitch")
        }
    }

    literal("test_overshoot") {
        runs {
            val player = mc.player ?: return@runs

            // 防呆：確保玩家拿著 AOTV / Etherwarp 武器
            val heldItem = player.mainHandItem.displayName.string.lowercase()
            if (!heldItem.contains("aspect of the void") && !heldItem.contains("aotv")) {
                modMessage("§c[Debug] 請手持 Aspect of the Void！")
                return@runs
            }

            // 1. 取得基準點 (玩家當前位置，對齊到方塊中心)
            val startX = floor(player.x) + 0.5
            val startY = floor(player.y) // 地板高度 (踩著的面)
            val startZ = floor(player.z) + 0.5

            // 2. 定義兩個完美放大誤差的目標點 (Z+5 和 Z+16)
            val target1 = Vec3(startX, startY, startZ + 5.0)
            val target2 = Vec3(startX, startY, startZ + 45.0)

            // 3. 計算第一段視角 (從起點看向目標 1，使用 Dungeons 蹲下高度 1.54)
            val eyePos0 = Vec3(startX, startY + 1.54, startZ)
            val (yaw1, pitch1) = PlayerUtils.getRotationsTo(eyePos0, target1)

            // 4. 計算第二段視角 (從目標 1 看向目標 2，同樣使用高度 1.54)
            val eyePos1 = Vec3(startX, startY + 1.54, startZ + 5.0)
            val (yaw2, pitch2) = PlayerUtils.getRotationsTo(eyePos1, target2)

            // ==========================================
            // 🌟 開始發送 0-tick 連鎖封包 (瞬間極速發送)
            // ==========================================

            // 第 1 發：傳送到 Z + 5
            PlayerUtils.setKeyState(InputKey.SNEAK, true)
            PlayerUtils.setYawPitch(yaw1, pitch1)
            mc.gameMode?.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND)

            // 第 2 發：傳送到 Z + 16 (0-Tick 盲發！)
            PlayerUtils.setYawPitch(yaw2, pitch2)
            mc.gameMode?.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND)

            // ==========================================
            // 報告預期結果
            // ==========================================
            modMessage("§a[Debug] 0-Tick 射線過載測試已送出！")
            modMessage("§e 📍 目標落點應為: Z = ${startZ + 45.0}")
            modMessage("§c ⚠️ 若發生視線高度 (1.62) 判定失誤，落點將為: Z = ${startZ + 47.0}")
            modMessage("第一段角度: $pitch1, 第二段: $pitch2")
        }
    }
}