package com.iq200.heigui.features.impl.dungeon.icefill

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.iq200.heigui.utils.JsonResourceLoader

data class IceFillPoint(val x: Int, val y: Int, val z: Int)
data class IceFillCheck(val airX: Int, val airZ: Int, val solidX: Int, val solidZ: Int)

object IceFillPatterns {
    private val data: JsonObject by lazy {
        JsonResourceLoader.loadJson("/assets/heigui/icefill/patterns.json", JsonObject())
    }

    val starts: List<Pair<Int, Int>> by lazy {
        data["starts"]?.asJsonArray?.map { startElement ->
            val start = startElement.asJsonArray
            start[0].asInt to start[1].asInt
        } ?: emptyList()
    }

    val representativeFloors: List<List<IceFillCheck>> by lazy {
        data["representativeFloors"]?.asJsonArray?.map { floorElement ->
            floorElement.asJsonArray.map { checkElement ->
                val check = checkElement.asJsonArray
                IceFillCheck(check[0].asInt, check[1].asInt, check[2].asInt, check[3].asInt)
            }
        } ?: emptyList()
    }

    val floors: List<List<List<IceFillPoint>>> by lazy {
        data["floors"]?.asJsonArray?.map { floorElement ->
            floorElement.asJsonArray.map { pathElement ->
                pathElement.asJsonArray.map { pointElement ->
                    pointElement.asJsonArray.toIceFillPoint()
                }
            }
        } ?: emptyList()
    }

    private fun JsonArray.toIceFillPoint(): IceFillPoint =
        IceFillPoint(this[0].asInt, this[1].asInt, this[2].asInt)
}
