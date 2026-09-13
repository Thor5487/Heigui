package com.iq200.heigui.config

import java.util.Properties

object BuildConfig {
    // 讀一次就好，之後全部欄位共用
    private val props: Properties by lazy {
        val loaded = Properties()
        try {
            // 嘗試讀取打包在 jar 裡面的設定檔
            BuildConfig::class.java.getResourceAsStream("/build_type.properties")?.use {
                loaded.load(it)
            }
        } catch (e: Exception) {
            // 讀不到就用空的，各欄位自己的預設值會生效
        }
        loaded
    }

    val isPrivate: Boolean by lazy {
        props.getProperty("is_private", "false").toBoolean()
    }

    // "release" 或 "beta"。開發環境 (IDE) 找不到檔案時預設為 beta —
    // 誤判成測試版比誤判成正式版安全
    val channel: String by lazy {
        props.getProperty("channel", "beta")
    }

    val isBeta: Boolean
        get() = channel != "release"

    // 這個 build 對應的 commit 短 hash，方便對照 bug 回報
    val commit: String by lazy {
        props.getProperty("commit", "unknown")
    }

    // 完整版本號，測試版會帶 "-beta.N+<hash>" 後綴
    val version: String by lazy {
        props.getProperty("version", "unknown")
    }
}
