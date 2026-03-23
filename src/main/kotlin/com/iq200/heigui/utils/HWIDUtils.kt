package com.iq200.heigui.utils

import java.security.MessageDigest

object HWIDUtils {
    fun getHWID(): String {
        return try {
            // 1. 收集電腦的核心特徵 (以 Windows 為主的環境變數)
            val osName = System.getProperty("os.name") ?: "UnknownOS"
            val osArch = System.getProperty("os.arch") ?: "UnknownArch"
            // 電腦名稱 (例如: DIEGO-PC)
            val computerName = System.getenv("COMPUTERNAME") ?: "UnknownHost"
            // CPU 特徵碼 (例如: Intel64 Family 6 Model 158 Stepping 10, GenuineIntel)
            val cpuIdentifier = System.getenv("PROCESSOR_IDENTIFIER") ?: "UnknownCPU"
            // 系統使用者名稱 (例如: diego)
            val userName = System.getProperty("user.name") ?: "UnknownUser"

            // 2. 將所有硬體資訊串接成一個「原始字串」
            // 加上一個屬於你模組的專屬「鹽巴 (Salt)」，防止別人用彩虹表反查
            val rawHWID = "BloodBlink_SecretSalt_888|$osName|$osArch|$computerName|$cpuIdentifier|$userName"

            // 3. 進行 SHA-256 雜湊處理
            hashSHA256(rawHWID)

        } catch (e: Exception) {
            // 如果遇到極端權限問題抓不到，回傳錯誤碼
            "ERR_CANNOT_GENERATE_HWID"
        }
    }

    /**
     * SHA-256 雜湊演算法
     */
    private fun hashSHA256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)

        // 將 Byte 陣列轉換成小寫的 16 進位字串 (Hex String)
        return digest.joinToString("") { "%02x".format(it) }
    }
}