@file:Suppress("unused")

package com.iq200.heigui.config

import com.google.gson.*
import com.iq200.heigui.Heigui
import com.iq200.heigui.Heigui.logger
import com.iq200.heigui.clickgui.settings.Saving
import com.iq200.heigui.features.Module
import java.io.File






class ModuleConfig internal constructor(file: File) {




    constructor(fileName: String) : this(File(Heigui.configDir, fileName))


    internal val modules: HashMap<String, Module> = hashMapOf()

    private val file: File = file.apply {
        try {
            parentFile.mkdirs()
            createNewFile()
        } catch (e: Exception) {
            logger.error("Error initializing module config", e)
        }
    }




    fun load() {
        try {
            with(file.bufferedReader().use { it.readText() }) {
                if (isEmpty()) return

                val jsonArray = JsonParser.parseString(this).asJsonArray ?: return
                for (modules in jsonArray) {
                    val moduleObj = modules?.asJsonObject ?: continue
                    val module = this@ModuleConfig.modules[moduleObj.get("name").asString.lowercase()] ?: continue


                    if (moduleObj.get("enabled").asBoolean != module.enabled) module.toggle()


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




    fun save() {
        try {
            val jsonArray = readExistingConfig()
            val moduleIndices = mutableMapOf<String, Int>()

            jsonArray.forEachIndexed { index, element ->
                val moduleName = element
                    .takeIf(JsonElement::isJsonObject)
                    ?.asJsonObject
                    ?.get("name")
                    ?.takeIf(JsonElement::isJsonPrimitive)
                    ?.asString
                    ?.lowercase()

                if (moduleName != null) moduleIndices.putIfAbsent(moduleName, index)
            }

            for ((moduleName, module) in modules) {
                val existingIndex = moduleIndices[moduleName]
                val moduleObj = existingIndex
                    ?.let { jsonArray[it].takeIf(JsonElement::isJsonObject)?.asJsonObject?.deepCopy() }
                    ?: JsonObject()

                val settingObj = moduleObj
                    .get("settings")
                    ?.takeIf(JsonElement::isJsonObject)
                    ?.asJsonObject
                    ?.deepCopy()
                    ?: JsonObject()

                for ((name, setting) in module.settings) {
                    if (setting is Saving) settingObj.add(name, setting.write(gson))
                }

                moduleObj.add("name", JsonPrimitive(module.name))
                moduleObj.add("enabled", JsonPrimitive(module.enabled))
                moduleObj.add("settings", settingObj)

                if (existingIndex == null) jsonArray.add(moduleObj)
                else jsonArray.set(existingIndex, moduleObj)
            }

            file.bufferedWriter().use { it.write(gson.toJson(jsonArray)) }
        } catch (e: Exception) {
            logger.error("Error saving module config.", e)
        }
    }

    private fun readExistingConfig(): JsonArray {
        if (!file.exists() || file.length() == 0L) return JsonArray()
        return JsonParser.parseReader(file.reader()).asJsonArray.deepCopy()
    }

    private companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    override fun toString(): String {
        return "ModuleConfig(file=$file)"
    }
}
