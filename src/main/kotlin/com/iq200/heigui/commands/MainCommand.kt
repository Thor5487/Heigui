package com.iq200.heigui.commands

import com.github.stivais.commodore.Commodore
import com.iq200.heigui.BuildConfig
import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.clickgui.ClickGUI
import com.iq200.heigui.utils.AuthManager
import com.iq200.heigui.utils.handlers.schedule
import com.iq200.heigui.utils.modMessage

val mainCommand = Commodore("heigui", "hg") {

    // 當玩家只輸入 /heigui 或 /hg 時觸發的邏輯
    runs {
        if (!BuildConfig.REQUIRE_AUTH) {
            schedule(0) { mc.setScreen(ClickGUI) }
            return@runs // 直接結束，不要往下跑驗證邏輯
        }

        if (AuthManager.isAuthorized) {
            // ✅ 如果是付費版且已經驗證過：打開選單
            schedule(0) { mc.setScreen(ClickGUI) }
        } else {
            // ❌ 如果是付費版但還沒驗證：提示玩家輸入序號
            modMessage("§b[Heigui] Welcome! Please use /hg auth <your_license_key> to unlock features.")
        }
    }

    literal("auth") {
        runs {
            if (!BuildConfig.REQUIRE_AUTH) {
                modMessage("§aThis is the free version, no authentication required!")
                return@runs
            }

            if (AuthManager.isAuthorized) {
                modMessage("§aYou have already been verified, no need to enter it again!")
            } else {
                modMessage("§cError: Missing license key! §ePlease use /hg auth <your_license_key>")
            }
        }

        runs { key: String ->
            if (!BuildConfig.REQUIRE_AUTH) {
                modMessage("§aThis is the free version, no authentication required!")
                return@runs
            }

            if (AuthManager.isAuthorized) {
                modMessage("You have already been verified, no need to enter it again!")
            } else {
                AuthManager.registerNewKey(key)
            }
        }
    }

    setupDebugCommand()
}