package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.Heigui
import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.lore
import com.iq200.heigui.utils.loreString
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.PriceUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult


object AutoCroesus : Module(
    name = "Auto Croesus",
    description = "Automatically opens and claims rare or profitable loot from Croesus.",
    category = Category.DUNGEON
) {
    private val clickDelay by NumberSetting("Click Delay", 150, 50, 500, 50, "minimum delay between each click", "ms")
    private val useKismets by BooleanSetting("Kismet", true, "use kismets or not")
    private val targetProfit by NumberSetting("Target Profit", 5, 1, 100, 1, "rerolls the chest if current profit is below this value", "m").withDependency { useKismets }

    val ignoreList: MutableSet<String>
        get() = Heigui.autoCroesusConfig.ignoreList

    var isWorking = false
        private set

    var currentKismetAvailable = false
        private set

    private enum class CroesusState {
        IDLE,
        WAITING_FOR_MENU,
        SCANNING_MAIN_PAGE,
        WAITING_FOR_CHEST_MENU, // 點擊局數後，等待該局的寶箱畫面加載
        INSIDE_LOOT_CHEST
    }

    private var currentState = CroesusState.IDLE
    private var lastActionTime = 0L


    init {
        on<InputEvent> {
            if (!isWorking) return@on
            if (isPress) {
                stop()
            }
        }

        on<TickEvent.Start> {
            if (!isWorking) return@on

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastActionTime < clickDelay) return@on

            val currentScreen = mc.screen as? AbstractContainerScreen<*> ?: return@on
            val menuTitle = currentScreen.title.string.replace(Regex("§[0-9a-fk-or]"), "")

            // 就像路由台一樣，把任務指派給對應的函式處理
            when (currentState) {
                CroesusState.WAITING_FOR_MENU -> handleWaitingForMenu(menuTitle)
                CroesusState.SCANNING_MAIN_PAGE -> handleScanningMainPage(currentScreen, currentTime)
                CroesusState.WAITING_FOR_CHEST_MENU -> handleWaitingForChestMenu(menuTitle)
                CroesusState.INSIDE_LOOT_CHEST -> handleInsideLootChest(currentScreen, currentTime)
                CroesusState.IDLE -> {}
            }
        }
    }


    private fun handleWaitingForMenu(menuTitle: String) {
        if (menuTitle.contains("Croesus")) {
            modMessage("§a[感知] 成功開啟 Croesus 主介面！準備掃描局數...")
            currentState = CroesusState.SCANNING_MAIN_PAGE
        }
    }

    private fun handleScanningMainPage(currentScreen: AbstractContainerScreen<*>, currentTime: Long) {
        val menu = currentScreen.menu
        var foundUnopened = false

        val player = mc.player ?: return

        for (i in 10..43) {
            val slot = menu.slots.getOrNull(i) ?: continue
            if (!slot.hasItem()) continue

            val lore = slot.item.loreString
            val loreComponents = slot.item.lore

            if (lore.any { it.contains("No chests opened yet!") }) {
                val kismetComp = loreComponents.find { it.string.contains("Kismet Feather") }
                if (kismetComp != null) {
                    // 使用 toString() 檢查底層是否帶有 strikethrough (刪除線) 屬性
                    val isUsed = kismetComp.toString().contains("strikethrough", ignoreCase = true)
                    currentKismetAvailable = !isUsed
                } else {
                    currentKismetAvailable = false
                }

                modMessage("§a[AutoCroesus] 找到未開啟的局數 (Slot $i)，正在點擊...")
                mc.gameMode?.handleContainerInput(menu.containerId, i, 0, ContainerInput.PICKUP, player)

                currentState = CroesusState.WAITING_FOR_CHEST_MENU
                lastActionTime = currentTime
                foundUnopened = true
                break
            }
        }

        if (!foundUnopened) {
            val nextSlot = menu.slots.getOrNull(53)
            val nextItemName = nextSlot?.item?.hoverName?.string?.replace(Regex("§[0-9a-fk-or]"), "") ?: ""

            if (nextItemName.contains("Next Page")) {
                modMessage("§e[AutoCroesus] 當前頁面已掃描完畢，點擊下一頁...")
                mc.gameMode?.handleContainerInput(menu.containerId, 53, 0, ContainerInput.PICKUP, player)

                currentState = CroesusState.WAITING_FOR_MENU
                lastActionTime = currentTime
            } else {
                modMessage("§a[AutoCroesus] 所有頁面皆已掃描完畢！沒有未開的局數了。")
                stop()
            }
        }
    }

    private fun handleWaitingForChestMenu(menuTitle: String) {
        if (!menuTitle.contains("Croesus")) {
            modMessage("§a[感知] 成功進入局數寶箱介面！等待下一步指示...")
            currentState = CroesusState.INSIDE_LOOT_CHEST
        }
    }

    private fun handleInsideLootChest(currentScreen: AbstractContainerScreen<*>, currentTime: Long) {
        val menu = currentScreen.menu
        val player = mc.player ?: return

        val targetChestNames = listOf(
            "Wood Chest", "Gold Chest", "Diamond Chest",
            "Emerald Chest", "Obsidian Chest", "Bedrock Chest"
        )

        val foundChests = mutableListOf<Pair<Int, ItemStack>>()

        for (i in 0 until menu.slots.size) {
            val slot = menu.slots.getOrNull(i) ?: continue
            if (!slot.hasItem()) continue

            // 取得乾淨的物品名稱 (洗掉 Color Code)
            val itemName = slot.item.hoverName.string.replace(Regex("§[0-9a-fk-or]"), "")

            // 如果這格是目標箱子之一，收集起來
            if (targetChestNames.any { itemName.contains(it) }) {
                foundChests.add(Pair(i, slot.item))
            }

        }

        if (foundChests.isEmpty()) {
            modMessage("§c[AutoCroesus] 警告：在此局數中找不到任何箱子！")
            stop()
            return
        }

        modMessage("§e[AutoCroesus] 掃描完畢，共找到 ${foundChests.size} 個箱子！準備進行利潤計算...")
        stop()
    }

    fun go() {
        if (!enabled) {
            modMessage("§cAutoCroesus isn't enabled!")
            return
        }

        if (isWorking) {
            modMessage("§eAlready In Process")
            return
        }


        PriceUtils.fetchPrices(notifyPlayer = true) { success ->
            if (!success) {
                // 防呆：如果 API 掛了或網路斷線，立刻中斷
                modMessage("§c[AutoCroesus] 價格更新失敗，已取消啟動開箱流程。")
                return@fetchPrices
            }


            mc.execute {

                if (mc.screen != null) {
                    mc.player?.closeContainer()
                }

                val npc = findCroesusNPC()

                val hit = mc.hitResult

                val hitEntity = if (hit != null && hit.type == HitResult.Type.ENTITY) {
                    (hit as EntityHitResult).entity
                } else null

                if (npc == null || hitEntity == null || npc != hitEntity) {
                    modMessage("§cPlease Aim At Croesus!")
                    return@execute
                }


                val player = mc.player ?: return@execute

                // 4. 一切確認無誤，正式攻擊 NPC 並啟動狀態機
                mc.gameMode?.attack(player, npc)
                player.swing(InteractionHand.MAIN_HAND)
                startProcess()

            }
        }

    }

    private fun findCroesusNPC(): Player? {
        val level = mc.level ?: return null

        val center = mc.player?.position() ?: return null


        val croesusStands = level.entitiesForRendering()
            .filterIsInstance<ArmorStand>()
            .filter { it.distanceToSqr(center) < 100.0 }
            .filter { it.name.string.contains("Croesus", ignoreCase = true) }

        if (croesusStands.isEmpty()) return null

        // 2. 遍歷所有玩家實體 (Player)
        // 我們只搜尋距離 ArmorStand 極近的實體 (距離 < 0.5 格)
        for (stand in croesusStands) {
            val standPos = stand.position()

            val targetPlayer = level.players().find { player ->
                // 排除自己
                if (player == mc.player) return@find false

                // 計算距離 (位置重合度)
                val dist = player.position().distanceToSqr(standPos)
                dist == 0.0
            }

            if (targetPlayer != null) return targetPlayer
        }

        return null
    }


    fun stop() {
        if (isWorking) {
            isWorking = false
            modMessage("§cAutoCroesus has been stopped")
        }
    }

    private fun startProcess() {
        isWorking = true
        currentState = CroesusState.WAITING_FOR_MENU
        lastActionTime = System.currentTimeMillis()
        modMessage("§a[AutoCroesus] Started! Attempting to open Croesus menu...")
    }

    override fun onDisable() {
        isWorking = false
        super.onDisable()
    }
}