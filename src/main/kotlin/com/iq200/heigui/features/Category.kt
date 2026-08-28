package com.iq200.heigui.features

import com.iq200.heigui.features.Category.Companion.categories

@ConsistentCopyVisibility
data class Category private constructor(val name: String, val x: Int, val y: Int) {
    companion object {

        /**
         * Map containing all the categories, with the key being the name.
         */
        val categories: LinkedHashMap<String, Category> = linkedMapOf()

        @JvmField
        val DUNGEON = custom(name = "Dungeon", 10, 10)
        @JvmField
        val FLOOR7 = custom(name = "Floor 7", 180, 10)
        @JvmField
        val RENDER = custom(name = "Render", 350, 10)
        @JvmField
        val SKYBLOCK = custom(name = "Skyblock", 520, 10)
        @JvmField
        val MINING = custom(name = "Mining", 690, 10)
        @JvmField
        val GENERAL = custom(name = "General", 860, 10)
        @JvmField
        val DEV = custom(name = "Dev", 1040, 10)

        /**
         * Returns a category with name provided.
         *
         * If a category with the same name has already been made, it won't reallocate.
         * Otherwise, it will be added to [categories].
         */
        fun custom(name: String, x: Int, y: Int): Category = categories.getOrPut(name) { Category(name, x, y) }
    }
}