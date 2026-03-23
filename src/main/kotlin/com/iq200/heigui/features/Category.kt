package com.iq200.heigui.features

import com.iq200.heigui.features.Category.Companion.categories

@ConsistentCopyVisibility
data class Category private constructor(val name: String) {
    companion object {

        /**
         * Map containing all the categories, with the key being the name.
         */
        val categories: LinkedHashMap<String, Category> = linkedMapOf()

        @JvmField
        val DUNGEON = custom(name = "Dungeon")
        @JvmField
        val FLOOR7 = custom(name = "Floor 7")
        @JvmField
        val RENDER = custom(name = "Render")
        @JvmField
        val SKYBLOCK = custom(name = "Skyblock")
        @JvmField
        val MINING = custom(name = "Mining")

        /**
         * Returns a category with name provided.
         *
         * If a category with the same name has already been made, it won't reallocate.
         * Otherwise, it will be added to [categories].
         */
        fun custom(name: String): Category {
            return categories.getOrPut(name) { Category(name) }
        }
    }
}