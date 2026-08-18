package com.iq200.heigui.config

import java.util.Properties

object BuildConfig {
    val isPrivate: Boolean by lazy {
        try {
            // 嘗試讀取打包在 jar 裡面的設定檔
            val stream = BuildConfig::class.java.getResourceAsStream("/build_type.properties")
            if (stream != null) {
                val props = Properties()
                props.load(stream)
                stream.close()
                // 讀取 is_private 的值，預設為 false
                props.getProperty("is_private", "false").toBoolean()
            } else {
                false // 開發環境 (IDE) 執行時如果找不到檔案的預設值
            }
        } catch (e: Exception) {
            false
        }
    }
}