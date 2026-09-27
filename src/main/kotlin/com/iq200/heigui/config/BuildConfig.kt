package com.iq200.heigui.config

import java.util.Properties

object BuildConfig {

    private val props: Properties by lazy {
        val loaded = Properties()
        try {

            BuildConfig::class.java.getResourceAsStream("/build_type.properties")?.use {
                loaded.load(it)
            }
        } catch (e: Exception) {

        }
        loaded
    }

    val channel: String by lazy {
        props.getProperty("channel", "beta")
    }

    val isBeta: Boolean
        get() = channel != "release"


    val commit: String by lazy {
        props.getProperty("commit", "unknown")
    }


    val version: String by lazy {
        props.getProperty("version", "unknown")
    }
}
