package com.iq200.heigui.utils.skyblock

import com.iq200.heigui.utils.romanToInt

object PriceParser {
    private val previewEnchantedBookRegex = Regex("^Enchanted Book \\(?([\\w ]+) (\\w+)\\)$")
    private val previewEssenceRegex = Regex("^(\\w+) Essence(?: x(\\d+))?$")
    private val shardRegex = Regex("^([A-Za-z ]+) Shard(?: x1)?$")

    private val ultimateEnchants = setOf(
        "Soul Eater", "Combo", "Legion", "One For All", "Rend",
        "Bank", "Swarm", "Last Stand", "Wisdom", "No Pain No Gain"
    )

    private val itemReplacements = mapOf(
        "Shiny Wither Chestplate" to "WITHER_CHESTPLATE",
        "Shiny Wither Leggings" to "WITHER_LEGGINGS",
        "Shiny Necron's Handle" to "NECRON_HANDLE",
        "Necron's Handle" to "NECRON_HANDLE",
        "Shiny Wither Helmet" to "WITHER_HELMET",
        "Shiny Wither Boots" to "WITHER_BOOTS",
        "Wither Shield" to "WITHER_SHIELD_SCROLL",
        "Implosion" to "IMPLOSION_SCROLL",
        "Shadow Warp" to "SHADOW_WARP_SCROLL",
        "Necron Dye" to "DYE_NECRON",
        "Livid Dye" to "DYE_LIVID",
        "Giant's Sword" to "GIANTS_SWORD",
        "NECROMANCERS_BROOCH" to "NECROMANCER_BROOCH",
        "SHADOW_WARP" to "SHADOW_WARP_SCROLL",
        "SPIRIT_STONE" to "SPIRIT_DECOY",
        "WARPED_STONE" to "AOTE_STONE",
        "First Master Skull" to "MASTER_SKULL_TIER_1",
        "Second Master Skull" to "MASTER_SKULL_TIER_2",
        "Third Master Skull" to "MASTER_SKULL_TIER_3",
        "Fourth Master Skull" to "MASTER_SKULL_TIER_4",
        "Fifth Master Skull" to "MASTER_SKULL_TIER_5",
        "Sixth Master Skull" to "MASTER_SKULL_TIER_6",
        "Seventh Master Skull" to "MASTER_SKULL_TIER_7",
        "Precursor Relic" to "PRECURSOR_GEAR"
    )

    fun parseItemValue(item: String, includeEssence: Boolean = true): Double {
        // 1. 附魔書
        previewEnchantedBookRegex.find(item)?.destructured?.let { (name, level) ->
            val ult = if (name in ultimateEnchants) "ULTIMATE_" else ""
            val apiId = "ENCHANTED_BOOK-$ult${name.uppercase().replace(" ", "_")}-${romanToInt(level)}"
            return PriceUtils.getItemPrice(apiId)
        }

        // 2. 精華 (Essence)
        previewEssenceRegex.find(item)?.destructured?.let { (name, quantityStr) ->
            if (!includeEssence) return 0.0
            val apiId = "ESSENCE_${name.uppercase()}"
            val price = PriceUtils.getItemPrice(apiId)
            val quantity = quantityStr.toIntOrNull() ?: 1
            return price * quantity
        }

        // 3. 碎片 (Shard)
        shardRegex.find(item)?.groupValues?.get(1)?.let { shardName ->
            val apiId = "SHARD_${shardName.uppercase().replace(" ", "_").replace("'s", "")}"
            return PriceUtils.getItemPrice(apiId)
        }

        // 4. 特例替換表
        itemReplacements[item]?.let { itemId ->
            return PriceUtils.getItemPrice(itemId)
        }

        // 5. 預設 Fallback 規則
        val fallbackId = item.uppercase().replace("'", "").replace(" -", "").replace(" ", "_")
        return PriceUtils.getItemPrice(fallbackId)
    }
}