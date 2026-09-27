package com.iq200.heigui.config

import com.google.gson.GsonBuilder
import com.iq200.heigui.Heigui
import java.io.File
import java.lang.reflect.Type








class JsonConfig<T>(
    private val fileName: String,
    private val typeToken: Type,
    private val defaultData: () -> T
) {
    private val file = File(Heigui.configDir, fileName)
    private val gson = GsonBuilder().setPrettyPrinting().create()

    var data: T = defaultData()
        private set

    fun load() {
        if (!file.exists()) {
            save()
            return
        }
        try {
            val json = file.readText()
            data = gson.fromJson(json, typeToken) ?: defaultData()
        } catch (e: Exception) {
            Heigui.logger.error("Failed to load config: $fileName", e)
            data = defaultData()
        }
    }

    fun save() {
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(data))
    }

    fun update(block: (T) -> Unit) {
        block(data)
        save()
    }
}