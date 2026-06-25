@file:Suppress("unused")

package com.iq200.heigui.config

import com.google.gson.*
import com.iq200.heigui.Heigui
import com.iq200.heigui.Heigui.logger
import com.iq200.heigui.clickgui.settings.Saving
import com.iq200.heigui.features.Module
import java.io.File

/**
 * # ModuleConfig
 *
 * 負責將 Heigui 的外掛模組狀態 (開啟/關閉) 以及設定值，存成 JSON 格式的設定檔。
 */
class ModuleConfig internal constructor(file: File) {

    /**
     * 主設定檔的建構子。檔案會儲存在： config/heigui/{fileName}
     */
    constructor(fileName: String) : this(File(Heigui.configFile, fileName))

    // key 是小寫的模組名稱
    internal val modules: HashMap<String, Module> = hashMapOf()

    private val file: File = file.apply {
        try {
            parentFile.mkdirs()
            createNewFile()
        } catch (e: Exception) {
            logger.error("Error initializing module config", e)
        }
    }

    /**
     * 從 JSON 檔案讀取設定，並套用到 [modules] 裡面的各個功能。
     */
    fun load() {
        try {
            with(file.bufferedReader().use { it.readText() }) {
                if (isEmpty()) return

                val jsonArray = JsonParser.parseString(this).asJsonArray ?: return
                for (modules in jsonArray) {
                    val moduleObj = modules?.asJsonObject ?: continue
                    val module = this@ModuleConfig.modules[moduleObj.get("name").asString.lowercase()] ?: continue

                    // 讀取模組的開關狀態
                    if (moduleObj.get("enabled").asBoolean != module.enabled) module.toggle()

                    // 讀取模組內的詳細設定值 (例如速度、範圍等)
                    val settingObj = moduleObj.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet() ?: continue
                    for ((key, value) in settingObj) {
                        (module.settings[key] as? Saving)?.apply { read(value ?: continue, gson) }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading module config", e)
        }
    }

    /**
     * 將目前 [modules] 裡面的所有狀態，存檔寫入 JSON。
     */
    fun save() {
        try {
            val jsonArray = JsonArray().apply {
                for ((_, module) in modules) {
                    add(JsonObject().apply {
                        add("name", JsonPrimitive(module.name))
                        add("enabled", JsonPrimitive(module.enabled))
                        add("settings", JsonObject().apply {
                            for ((name, setting) in module.settings) {
                                // 只有實作了 Saving 介面的設定才會被存檔
                                if (setting is Saving) add(name, setting.write(gson))
                            }
                        })
                    })
                }
            }
            file.bufferedWriter().use { it.write(gson.toJson(jsonArray)) }
        } catch (e: Exception) {
            logger.error("Error saving module config.", e)
        }
    }

    private companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    override fun toString(): String {
        return "ModuleConfig(file=$file)"
    }
}