package com.iq200.heigui.utils.skyblock

import com.google.gson.JsonParser
import com.iq200.heigui.Heigui
import com.iq200.heigui.utils.modMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

object PriceUtils {
    private val prices = ConcurrentHashMap<String, Double>()

    var isLoaded = false
        private set


    fun fetchPrices(notifyPlayer: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        Heigui.scope.launch(Dispatchers.IO) {
            try {
                if (notifyPlayer) modMessage("§eFetching latest prices from ODTheKing API...")
                Heigui.logger.info("[PriceUtils] Fetching prices from ODTheKing API...")

                val jsonString = URI.create("https://api.odtheking.com/lb/averages/7day").toURL().readText()
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject

                var count = 0
                for ((itemName, priceElement) in jsonObject.entrySet()) {
                    prices[itemName] = priceElement.asDouble
                    count++
                }

                isLoaded = true
                if (notifyPlayer) modMessage("§aSuccessfully updated $count item prices!")
                Heigui.logger.info("[PriceUtils] Successfully loaded $count item prices!")

                onComplete?.invoke(true)
            } catch (e: Exception) {
                if (notifyPlayer) modMessage("§cFailed to fetch prices! Check your internet connection.")
                Heigui.logger.error("[PriceUtils] Failed to fetch prices!", e)

                onComplete?.invoke(false)
            }
        }
    }

    fun getItemPrice(internalId: String): Double {
        return prices[internalId] ?: 0.0
    }
}