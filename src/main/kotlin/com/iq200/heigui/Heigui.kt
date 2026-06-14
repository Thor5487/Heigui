package com.iq200.heigui

import com.iq200.heigui.commands.mainCommand
import com.iq200.heigui.events.EventDispatcher
import com.iq200.heigui.events.core.EventBus
import com.iq200.heigui.features.ModuleManager
import com.iq200.heigui.utils.AuthManager
import com.iq200.heigui.utils.DeathTickUtil
import com.iq200.heigui.utils.IrisCompatability
import com.iq200.heigui.utils.ServerUtils
import com.iq200.heigui.utils.handlers.TickTasks
import com.iq200.heigui.utils.ui.rendering.NVGPIPRenderer
import com.iq200.heigui.utils.render.ItemStateRenderer
import com.iq200.heigui.utils.render.RenderBatchManager
import com.iq200.heigui.utils.skyblock.LocationUtils
import com.iq200.heigui.utils.skyblock.PartyUtils
import com.iq200.heigui.utils.skyblock.SkyblockPlayer
import com.iq200.heigui.utils.skyblock.SplitsManager
import com.iq200.heigui.utils.skyblock.dungeon.DungeonListener
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback // 🌟 引入 Fabric 指令註冊
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.Version
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

object Heigui : ClientModInitializer {

    val logger: Logger = LogManager.getLogger("Heigui")

    @JvmStatic
    val mc: Minecraft = Minecraft.getInstance()

    val configFile: File = File(mc.gameDirectory, "config/heigui/").apply {
        try {
            if (isFile) delete()
            if (!exists()) mkdirs()
        } catch (e: Exception) {
            println("Error initializing module config\n${e.message}")
            logger.error("Error initializing module config", e)
        }
    }

    const val MOD_ID = "heigui"

    val version: Version by lazy { FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().metadata.version }
    val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

    override fun onInitializeClient() {
        logger.info("Heigui Mod is initializing... Version: $version")

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            arrayOf(mainCommand).forEach { it.register(dispatcher) }
        }

        SpecialGuiElementRegistry.register { context ->
            NVGPIPRenderer(context.vertexConsumers())
        }
        SpecialGuiElementRegistry.register { context ->
            ItemStateRenderer(context.vertexConsumers())
        }

        // 🌟 2. 執行 HWID 驗證機制
        if (BuildConfig.REQUIRE_AUTH) {
            logger.info("Initializing Auth...")

            // 嘗試使用儲存的密鑰驗證
            AuthManager.verifySavedKey {
                // 只有伺服器回傳 authorized: true，才會進來執行這裡的程式碼！
                logger.info("[Heigui] Loading Features")
                loadCoreFeatures()
            }
        } else {
            loadCoreFeatures()
        }
    }

    /**
     * 將牽涉到遊戲底層邏輯與模組掛載的程式碼集中在這裡。
     * 只有驗證成功（或是免費版）才會被呼叫，確保未授權時模組只是一具空殼。
     */
    private fun loadCoreFeatures() {
        listOf(
            this, LocationUtils, TickTasks,
            SkyblockPlayer, ServerUtils, EventDispatcher,
            DungeonListener, PartyUtils,
            ScanUtils, DungeonUtils, SplitsManager,
            IrisCompatability, RenderBatchManager,
            ModuleManager, DeathTickUtil
        ).forEach { EventBus.subscribe(it) }

        logger.info("[Heigui] All Features Loaded！")
    }
}