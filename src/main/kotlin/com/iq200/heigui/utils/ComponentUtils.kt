package com.iq200.heigui.utils

import com.google.gson.JsonParser
import com.iq200.heigui.Heigui.mc
import com.mojang.serialization.JsonOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization

fun Component.toJsonString(): String {
    val registryAccess = mc.level?.registryAccess()

    val ops = registryAccess?.createSerializationContext(JsonOps.INSTANCE) ?: JsonOps.INSTANCE

    return ComponentSerialization.CODEC
        .encodeStart(ops, this)
        .result()
        .map { it.toString() }
        .orElse(this.string)!!
}

fun String.toComponent(): Component {
    if (this.isEmpty()) return Component.empty()

    return try {
        val registryAccess = mc.level?.registryAccess()

        val ops = registryAccess?.createSerializationContext(JsonOps.INSTANCE) ?: JsonOps.INSTANCE

        val jsonElement = JsonParser.parseString(this)

        ComponentSerialization.CODEC
            .parse(ops, jsonElement)
            .result()
            .orElse(null) ?: Component.literal(this)
    } catch (e: Exception) {
        // 解析失敗代表這不是 JSON，直接當作普通文字處理
        Component.literal(this)
    }
}