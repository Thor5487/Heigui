@file:Suppress("unused")

package com.iq200.heigui.features

import com.iq200.heigui.Heigui
import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.clickgui.HudManager
import com.iq200.heigui.clickgui.settings.impl.HUDSetting
import com.iq200.heigui.clickgui.settings.impl.KeybindSetting
import com.iq200.heigui.config.ModuleConfig
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.ModuleManager.configs
import com.iq200.heigui.features.impl.dungeon.AutoClick
import com.iq200.heigui.features.impl.dungeon.AutoClose
import com.iq200.heigui.features.impl.dungeon.AutoCroesus
import com.iq200.heigui.features.impl.dungeon.SATpFix
import com.iq200.heigui.features.impl.dungeon.SecretAura
import com.iq200.heigui.features.impl.dungeon.SecretDone
import com.iq200.heigui.features.impl.dungeon.SkipSecrets
import com.iq200.heigui.features.impl.dungeon.Triggerbot
import com.iq200.heigui.features.impl.dungeon.ZPDB
import com.iq200.heigui.features.impl.floor7.AutoCrit
import com.iq200.heigui.features.impl.floor7.LBHelper
import com.iq200.heigui.features.impl.floor7.SimonSays
import com.iq200.heigui.features.impl.floor7.WitherAimBot
import com.iq200.heigui.features.impl.mining.BigPane
import com.iq200.heigui.features.impl.mining.Mineshaft
import com.iq200.heigui.features.impl.render.*
import com.iq200.heigui.features.impl.skyblock.TeleportOptimization
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import net.minecraft.resources.Identifier.fromNamespaceAndPath
import java.io.File

/**
 * # Module Manager
 *
 * This object stores all [Modules][Module] and provides functionality to [HUDs][Module.HUD]
 */
object ModuleManager {

    /**
     * where the key is the modules name in lowercase.
     */
    val modules: HashMap<String, Module> = linkedMapOf()

    /**
     * Map containing all modules under their category.
     */
    val modulesByCategory: HashMap<Category, ArrayList<Module>> = hashMapOf()

    val configs: ArrayList<ModuleConfig> = arrayListOf()

    val keybindSettingsCache: ArrayList<KeybindSetting> = arrayListOf()
    val hudSettingsCache: ArrayList<HUDSetting> = arrayListOf()

    private val HUD_LAYER: Identifier = fromNamespaceAndPath(Heigui.MOD_ID, "heigui_hud")

    init {
        registerModules(config = ModuleConfig(file = File(Heigui.configDir, "heigui-config.json")),
            // dungeon
            SecretAura, AutoClose, ZPDB, Triggerbot, AutoClick, SecretDone, SkipSecrets, SATpFix, AutoCroesus,

            // floor 7
            SimonSays, WitherAimBot, LBHelper, AutoCrit,

            // render
            ClickGUIModule,

            // skyblock
            TeleportOptimization,

            // mining
            BigPane, Mineshaft
        )

        // hashmap, but would need to keep track when setting values change
        on<InputEvent> {
            if (!isPress) return@on

            for (setting in keybindSettingsCache) {
                if (setting.value.value == key.value) setting.onPress?.invoke()
            }
        }

        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, HUD_LAYER, ModuleManager::render)
    }

    /**
     * Registers modules to the [ModuleManager] and initializes them.
     *
     * @param config the config the [Module] is saved to,
     * it is recommended that each unique mod that uses this has its own config
     */
    fun registerModules(config: ModuleConfig, vararg modules: Module) {
        for (module in modules) {
            if (module.isDevModule && !FabricLoader.getInstance().isDevelopmentEnvironment) continue

            val lowercase = module.name.lowercase()
            config.modules[lowercase] = module
            this.modules[lowercase] = module
            this.modulesByCategory.getOrPut(module.category) { arrayListOf() }.add(module)

            module.key?.let { keybind ->
                val setting = KeybindSetting("Keybind", keybind, "Toggles this module.")
                setting.onPress = module::onKeybind
                module.registerSetting(setting)
            }

            for ((_, setting) in module.settings) {
                when (setting) {
                    is KeybindSetting -> keybindSettingsCache.add(setting)
                    is HUDSetting -> hudSettingsCache.add(setting)
                }
            }
        }
        configs.add(config)
        config.load()
    }

    /**
     * Loads all [configs] from disk, into the respective modules.
     */
    fun loadConfigurations() {
        for (config in configs) {
            config.load()
        }
    }

    /**
     * Saves all [configs] to disk, from the respective modules.
     */
    fun saveConfigurations() {
        for (config in configs) {
            config.save()
        }
    }

    fun render(guiGraphics: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (mc.level == null || mc.player == null || mc.screen == HudManager || mc.options.hideGui) return

        guiGraphics.pose().pushMatrix()
        val sf = mc.window.guiScale
        guiGraphics.pose().scale(1f / sf, 1f / sf)
        for (hudSettings in hudSettingsCache) {
            if (hudSettings.isEnabled) hudSettings.value.draw(guiGraphics, false)
        }
        guiGraphics.pose().popMatrix()
    }
}