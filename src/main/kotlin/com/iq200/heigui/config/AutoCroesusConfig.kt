package com.iq200.heigui.config

import com.iq200.heigui.Heigui
import com.iq200.heigui.Heigui.logger
import com.google.gson.*
import java.io.File


data class AutoCroesusData(
    var ignoreList: MutableSet<String> = mutableSetOf()
)

class AutoCroesusConfig internal constructor(file: File){
    constructor(fileName: String = "autocroesus.json") : this(File(Heigui.configFile, fileName))


    private val file: File = file.apply {
        try {
            parentFile.mkdirs()
            if (!exists()) createNewFile()
        } catch (e: Exception) {
            logger.error("Error initializing AutoCroesus config", e)
        }
    }

    var ignoreList: MutableSet<String> = mutableSetOf()

    fun load() {
        try {
            val jsonText = file.bufferedReader().use { it.readText() }
            if (jsonText.isEmpty()) {
                save()
                return
            }

            // 使用 data class，Gson 可以一行自動解析完畢！
            val data = gson.fromJson(jsonText, AutoCroesusData::class.java)
            if (data != null) {
                ignoreList = data.ignoreList
            }
        } catch (e: Exception) {
            logger.error("Error loading AutoCroesus config", e)
        }
    }

    fun save() {
        try {
            val data = AutoCroesusData(ignoreList)
            file.bufferedWriter().use { it.write(gson.toJson(data)) }
        } catch (e: Exception) {
            logger.error("Error saving AutoCroesus config", e)
        }
    }

    private companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    override fun toString(): String {
        return "AutoCroesusConfig(file=$file)"
    }
}