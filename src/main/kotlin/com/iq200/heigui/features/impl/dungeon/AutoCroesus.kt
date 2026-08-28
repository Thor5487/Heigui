package com.iq200.heigui.features.impl.dungeon

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.lore
import com.iq200.heigui.utils.loreString
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.PriceParser
import com.iq200.heigui.utils.skyblock.PriceUtils
import com.iq200.heigui.utils.toComponent
import com.iq200.heigui.utils.toJsonString
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult


data class IgnoreData(
    var ignoreList: MutableSet<String> = mutableSetOf()
)

data class TrackerItem(
    var coloredName: String = "",
    var count: Int = 0,
    var totalValue: Double = 0.0 // 物品開出當下的歷史總價值
)

data class FloorTracker(
    var runsOpened: Int = 0,
    var kismetsUsed: Int = 0,
    var kismetCost: Double = 0.0,     // 該樓層消耗的羽毛歷史總成本
    var chestCost: Double = 0.0,      // 該樓層開箱歷史總花費
    var totalSellPrice: Double = 0.0, // 該樓層物品歷史總市價
    val items: MutableMap<String, TrackerItem> = mutableMapOf()
)

// 包含全域總計與各樓層明細
data class TrackerConfigData(
    val floors: MutableMap<String, FloorTracker> = mutableMapOf()
)

data class ParsedItem(val rawComponent: Component, val cleanName: String, val count: Int, val value: Double)
data class ChestData(val cost: Double, val totalValue: Double, val profit: Double, val items: List<ParsedItem>)

object AutoCroesus : Module(
    name = "Auto Croesus",
    description = "Automatically opens and claims rare or profitable loot from Croesus.",
    category = Category.DUNGEON
) {
    private val clickDelay by NumberSetting("Click Delay", 150, 50 .. 1000, 50, "minimum delay between each click", "ms")
    private val useKismets by BooleanSetting("Kismet", true, "use kismets or not")
    private val targetProfit by NumberSetting("Target Profit", 5, 1 .. 100, 1, "rerolls the chest if current profit is below this value", "m").withDependency { useKismets }

    val ignoreConfig = JsonConfig(
        fileName = "ac-ignore.json",
        typeToken = object : TypeToken<IgnoreData>() {}.type,
        defaultData = { IgnoreData() }
    )

    val trackerConfig = JsonConfig(
        fileName = "ac-loot-tracker.json",
        typeToken = object : TypeToken<TrackerConfigData>() {}.type,
        defaultData = { TrackerConfigData() }
    )

    val ignoreList: MutableSet<String>
        get() = ignoreConfig.data.ignoreList

    var isWorking = false
        private set

    var currentKismetAvailable = false
        private set

    private enum class CroesusState {
        IDLE,
        WAITING_FOR_MENU,
        SCANNING_MAIN_PAGE,
        WAITING_FOR_CHEST_MENU, // 點擊局數後，等待該局的寶箱畫面加載
        INSIDE_LOOT_CHEST,
        WAITING_FOR_CONFIRM_MENU,
        IN_CONFIRM_MENU,
        WAITING_FOR_REOPEN
    }

    private var intendingToReroll = false
    private var currentState = CroesusState.IDLE
    private var lastActionTime = 0L

    private var currentFloor: String = "unknown"
    private var currentRunKismets: Int = 0
    private var pendingChestData: ChestData? = null

    init {
        ignoreConfig.load()
        trackerConfig.load()

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

            if (currentState == CroesusState.WAITING_FOR_REOPEN) {
                if (mc.gui.screen() == null) { // 只有當介面真的被伺服器關閉後，才進行下一步
                    saveRunRecord(pendingChestData)
                    pendingChestData = null

                    mc.execute {
                        val player = mc.player ?: return@execute
                        val npc = findCroesusNPC(5.0)
                        if (npc == null) {
                            modMessage("§c[AutoCroesus] Error: Croesus NPC not found nearby after reopening!")
                            stop() // 找不到就停止腳本
                            return@execute
                        }
                        mc.gameMode?.attack(player, npc as Entity)
                        player.swing(InteractionHand.MAIN_HAND)
                        currentState = CroesusState.WAITING_FOR_MENU
                        lastActionTime = System.currentTimeMillis()
                    }
                }
                // 如果介面還開著（可能是伺服器延遲，或是背包滿了買失敗），就繼續在原地等待
                return@on
            }

            val currentScreen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return@on
            val menuTitle = currentScreen.title.string.replace(Regex("§[0-9a-fk-or]"), "")

            // 就像路由台一樣，把任務指派給對應的函式處理
            when (currentState) {
                CroesusState.WAITING_FOR_MENU -> handleWaitingForMenu(menuTitle)
                CroesusState.SCANNING_MAIN_PAGE -> handleScanningMainPage(currentScreen, currentTime)
                CroesusState.WAITING_FOR_CHEST_MENU -> handleWaitingForChestMenu(menuTitle)
                CroesusState.INSIDE_LOOT_CHEST -> handleInsideLootChest(currentScreen, currentTime)
                CroesusState.WAITING_FOR_CONFIRM_MENU -> handleWaitingForConfirmMenu(menuTitle)
                CroesusState.IN_CONFIRM_MENU -> handleInConfirmMenu(currentScreen, currentTime)
                else -> {}
            }
        }
    }


    private fun handleWaitingForMenu(menuTitle: String) {
        if (menuTitle.contains("Croesus")) {
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
                val cleanName = slot.item.hoverName.string.replace(Regex("§[0-9a-fk-or]"), "")
                currentFloor = parseFloor(cleanName, lore)
                currentRunKismets = 0

                val kismetComp = loreComponents.find { it.string.contains("Kismet Feather") }
                if (kismetComp != null) {
                    // 使用 toString() 檢查底層是否帶有 strikethrough (刪除線) 屬性
                    val isUsed = kismetComp.toString().contains("strikethrough", ignoreCase = true)
                    currentKismetAvailable = !isUsed
                } else {
                    currentKismetAvailable = false
                }

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
                mc.gameMode?.handleContainerInput(menu.containerId, 53, 0, ContainerInput.PICKUP, player)

                currentState = CroesusState.WAITING_FOR_MENU
                lastActionTime = currentTime
            } else {
                modMessage("§a[AutoCroesus] Finished! All pages have been scanned and no unopened chests remain.")
                stop()
            }
        }
    }

    private fun handleWaitingForChestMenu(menuTitle: String) {
        if (!menuTitle.contains("Croesus")) {
            currentState = CroesusState.INSIDE_LOOT_CHEST
        }
    }

    private fun handleInsideLootChest(currentScreen: AbstractContainerScreen<*>, currentTime: Long) {
        val menu = currentScreen.menu
        val player = mc.player ?: return

        val targetChestNames = listOf(
            "Wood", "Gold", "Diamond",
            "Emerald", "Obsidian", "Bedrock"
        )

        val foundChests = mutableListOf<Pair<Int, ItemStack>>()

        for (i in menu.slots.indices) {
            val slot = menu.slots.getOrNull(i) ?: continue
            if (!slot.hasItem()) continue
            val itemName = slot.item.hoverName.string.replace(Regex("§[0-9a-fk-or]"), "")
            if (targetChestNames.any { itemName.contains(it) }) {
                foundChests.add(Pair(i, slot.item))
            }
        }

        if (foundChests.isEmpty()) {
            modMessage("§c[AutoCroesus] Warning: No valid chests found in this run!")
            stop()
            return
        }

        var bestChestSlot = -1
        var maxProfit = Double.NEGATIVE_INFINITY
        var bestChestData: ChestData? = null

        var bedrockChestSlot = -1
        var bedrockChestData: ChestData? = null // 【新增】專門記錄 Bedrock 的資料

        for ((slotIndex, itemStack) in foundChests) {
            val itemName = itemStack.hoverName.string.replace(Regex("§[0-9a-fk-or]"), "")
            val chestData = calculateChestData(itemStack.lore, itemStack.loreString)

            if (itemName.contains("Bedrock", ignoreCase = true)) {
                bedrockChestSlot = slotIndex
                bedrockChestData = chestData // 存下 Bedrock 的專屬資料
            }

            if (chestData.profit > maxProfit) {
                maxProfit = chestData.profit
                bestChestSlot = slotIndex
                bestChestData = chestData
            }
        }

        // 將 bedrockChestData 也傳進去
        makeDecision(menu.containerId, player, maxProfit, bestChestSlot, bestChestData, bedrockChestSlot, bedrockChestData, menu)
    }

    fun go() {
        if (!enabled) {
            modMessage("§c[AutoCroesus] AutoCroesus isn't enabled!")
            return
        }

        if (isWorking) {
            modMessage("§e[AutoCroesus] Already In Process")
            return
        }


        PriceUtils.fetchPrices(notifyPlayer = true) { success ->
            if (!success) {
                modMessage("§c[AutoCroesus] Error: Failed to update prices. Process aborted.")
                return@fetchPrices
            }


            mc.execute {
                val player = mc.player ?: return@execute

                if (mc.gui.screen() != null) {
                    player.closeContainer()
                }


                val npc = findCroesusNPC(5.0)

                if (npc == null) {
                    modMessage("§c[AutoCroesus] Error: Croesus NPC not found nearby (or out of range)!")
                    return@execute
                }

                modMessage("§a[AutoCroesus] Started! Attempting to open Croesus menu...")
                mc.gameMode?.attack(player, npc as Entity)
                player.swing(InteractionHand.MAIN_HAND)
                startProcess()

            }
        }

    }

    private fun findCroesusNPC(maxDistance: Double): Player? {
        val level = mc.level ?: return null

        val center = mc.player?.position() ?: return null


        val croesusStands = level.entitiesForRendering()
            .filterIsInstance<ArmorStand>()
            .filter { it.distanceToSqr(center) < maxDistance }
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
            modMessage("§c[AutoCroesus] AutoCroesus has been stopped")

            if (mc.gui.screen() != null) {
                mc.player?.closeContainer()
            }
        }
    }

    private fun startProcess() {
        isWorking = true
        currentState = CroesusState.WAITING_FOR_MENU
        lastActionTime = System.currentTimeMillis()
    }

    override fun onDisable() {
        isWorking = false
        super.onDisable()
    }

    private fun parseFloor(cleanName: String, lore: List<String>): String {
        val isMaster = cleanName.contains("Master Mode", ignoreCase = true)
        val prefix = if (isMaster) "m" else "f"

        val floorLine = lore.find { it.replace(Regex("§[0-9a-fk-or]"), "").contains("Floor ") }
        if (floorLine != null) {
            val roman = floorLine.replace(Regex("§[0-9a-fk-or]"), "").substringAfter("Floor ").trim()
            val num = when (roman) {
                "I" -> "1"; "II" -> "2"; "III" -> "3"; "IV" -> "4"; "V" -> "5"; "VI" -> "6"; "VII" -> "7"
                else -> "?"
            }
            return "$prefix$num"
        }
        return "unknown"
    }

    private fun saveRunRecord(boughtData: ChestData?) {
        val globalData = trackerConfig.data
        val floorData = globalData.floors.getOrPut(currentFloor) { FloorTracker() }

        floorData.runsOpened++

        // 計算這局當下的 Kismet 羽毛總成本
        val kismetCost = PriceParser.parseItemValue("Kismet Feather") * currentRunKismets

        // 累加羽毛耗損 (全域 + 樓層)
        floorData.kismetsUsed += currentRunKismets
        floorData.kismetCost += kismetCost

        // 如果這局有買寶箱，累加開箱成本與收益
        if (boughtData != null) {
            floorData.chestCost += boughtData.cost
            floorData.totalSellPrice += boughtData.totalValue

            // 存入這局當下的物品價值
            for (item in boughtData.items) {
                val drop = floorData.items.getOrPut(item.cleanName) { TrackerItem("", 0, 0.0) }
                if (drop.coloredName.isEmpty()) {
                    val rebuiltComponent = Component.empty()
                    val validParts = mutableListOf<Pair<String, net.minecraft.network.chat.Style>>()

                    for (node in item.rawComponent.toFlatList()) {
                        val textPart = node.string

                        if (textPart.matches(Regex("(?i)^\\s*x\\d+\\s*$"))) {
                            continue
                        }

                        val cleanedTextPart = textPart.replace(Regex("(?i)\\s*x\\d+\\s*$"), "")

                        if (cleanedTextPart.isNotEmpty()) {
                            validParts.add(Pair(cleanedTextPart, node.style))
                        }
                    }

                    if (validParts.isNotEmpty()) {
                        val lastIndex = validParts.lastIndex
                        val lastPart = validParts[lastIndex]

                        // trimEnd() 只會清除字串右邊的空白
                        validParts[lastIndex] = Pair(lastPart.first.trimEnd(), lastPart.second)
                    }

                    for (part in validParts) {
                        if (part.first.isNotEmpty()) {
                            rebuiltComponent.append(Component.literal(part.first).withStyle(part.second))
                        }
                    }

                    drop.coloredName = rebuiltComponent.toJsonString()
                }
                drop.count += item.count
                drop.totalValue += item.value
            }
        }
        trackerConfig.save()
    }

    private fun calculateChestData(loreComponents: List<Component>, loreString: List<String>): ChestData {
        val loreStartIndex = loreString.indexOfFirst { it.contains("Contents") } + 1
        val loreEndIndex = loreString.indexOfFirst { it.isEmpty() || it.contains("Cost") }.takeIf { it != -1 } ?: loreString.size

        var cost = 0.0
        val costIndex = loreString.indexOfFirst { it.contains("Cost") }
        if (costIndex != -1 && costIndex + 1 < loreString.size) {
            val costLine = loreString[costIndex + 1].replace(Regex("§[0-9a-fk-or]"), "").trim()
            if (costLine != "FREE") {
                cost = costLine.replace(Regex("[^0-9]"), "").toDoubleOrNull() ?: 0.0
            }
        }

        var totalValue = 0.0
        val items = mutableListOf<ParsedItem>()

        if (loreStartIndex in 1 until loreEndIndex) {
            for (i in loreStartIndex until loreEndIndex) {
                val rawComponent = loreComponents[i]
                val cleanLine = loreString[i].replace(Regex("§[0-9a-fk-or]"), "").trim()

                val isIgnored = ignoreList.any { ignoredKeyword ->
                    cleanLine.contains(ignoredKeyword, ignoreCase = true)
                }

                if (!isIgnored && cleanLine.isNotEmpty()) {
                    val itemValue = PriceParser.parseItemValue(cleanLine) // 取得當下物價
                    totalValue += itemValue

                    var count = 1
                    var cleanNameForTracker = cleanLine
                    val xMatch = Regex("^(.*)\\s+x(\\d+)$").find(cleanLine)
                    if (xMatch != null) {
                        cleanNameForTracker = xMatch.groupValues[1].trim()
                        count = xMatch.groupValues[2].toInt()
                    }

                    items.add(ParsedItem(rawComponent, cleanNameForTracker, count, itemValue))
                }
            }
        }
        return ChestData(cost, totalValue, totalValue - cost, items)
    }

    private fun makeDecision(containerId: Int, player: Player, maxProfit: Double, bestChestSlot: Int, bestChestData: ChestData?, bedrockChestSlot: Int, bedrockChestData: ChestData?, menu: AbstractContainerMenu) {
        val targetProfitCoins = targetProfit * 1_000_000.0

        // 1. 判斷是否需要重骰：只拿 Bedrock 寶箱的利潤來跟目標比較！
        if (useKismets && currentKismetAvailable && bedrockChestSlot != -1 && bedrockChestData != null) {

            // 如果 Bedrock 箱子沒達標，直接無視其他箱子，果斷重骰！
            if (bedrockChestData.profit < targetProfitCoins) {
                intendingToReroll = true
                mc.gameMode?.handleContainerInput(containerId, bedrockChestSlot, 0, ContainerInput.PICKUP, player)

                currentState = CroesusState.WAITING_FOR_CONFIRM_MENU
                lastActionTime = System.currentTimeMillis()
                return
            }
        }

        // 2. 判斷是購買還是略過 (當無法重骰，或 Bedrock 已經達標時，才來選全場最賺的)
        intendingToReroll = false
        if (maxProfit > 0 && bestChestSlot != -1 && bestChestData != null) {
            // 【購買】：點擊利潤最高 (maxProfit) 的寶箱來止損或收成
            pendingChestData = bestChestData
            mc.gameMode?.handleContainerInput(containerId, bestChestSlot, 0, ContainerInput.PICKUP, player)

            currentState = CroesusState.WAITING_FOR_CONFIRM_MENU
            lastActionTime = System.currentTimeMillis()
        } else {
            // 【略過】：全場都是垃圾，連買都不買，直接返回
            saveRunRecord(null)

            val backSlot = findSlotByItemName(menu, "Go Back") ?: findSlotByItemName(menu, "Back")
            if (backSlot != -1) {
                mc.gameMode?.handleContainerInput(containerId, backSlot, 0, ContainerInput.PICKUP, player)
                currentState = CroesusState.WAITING_FOR_MENU
                lastActionTime = System.currentTimeMillis()
            } else {
                modMessage("§c[AutoCroesus] Error: Go Back button not found. Process aborted.")
                stop()
            }
        }
    }


    private fun handleWaitingForConfirmMenu(menuTitle: String) {
        val targetChestTitles = listOf("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock")

        // 如果標題包含任何一個寶箱名稱，代表已經成功進入確認畫面
        if (targetChestTitles.any { menuTitle.contains(it, ignoreCase = true) }) {
            currentState = CroesusState.IN_CONFIRM_MENU
        }

    }


    private fun handleInConfirmMenu(currentScreen: AbstractContainerScreen<*>, currentTime: Long) {
        val menu = currentScreen.menu
        val player = mc.player ?: return


        // 根據你的截圖，尋找名稱包含 "Open Reward Chest" 的格子
        if (intendingToReroll) {
            // ============================
            // 情況 A：為了重骰而進入此介面
            // ============================
            val rerollSlot = findSlotByItemName(menu, "Reroll")
            if (rerollSlot != -1) {
                mc.gameMode?.handleContainerInput(menu.containerId, rerollSlot, 0, ContainerInput.PICKUP, player)

                // 更新羽毛使用狀態與統計資料
                currentRunKismets++
                currentKismetAvailable = false // 標記這局已經骰過了
                intendingToReroll = false

                // 點擊重骰後，伺服器會把我們退回寶箱選擇介面 (第二層)，準備讀取新箱子
                currentState = CroesusState.WAITING_FOR_CHEST_MENU
                lastActionTime = currentTime
            } else {
                modMessage("§c[AutoCroesus] Warning: Intended to reroll but 'Reroll' button not found! Aborting.")
                stop()
            }
        } else {
            // ============================
            // 情況 B：為了購買而進入此介面
            // ============================
            val confirmSlot = findSlotByItemName(menu, "Open Reward Chest")
            if (confirmSlot != -1) {
                mc.gameMode?.handleContainerInput(menu.containerId, confirmSlot, 0, ContainerInput.PICKUP, player)

                // 點擊後，伺服器會扣款、給予物品，並將介面關閉
                currentState = CroesusState.WAITING_FOR_REOPEN
                lastActionTime = currentTime
            } else {
                modMessage("§c[AutoCroesus] Error: Open Reward Chest button not found. Process aborted.")
                stop()
            }
        }
    }

    private fun findSlotByItemName(menu: AbstractContainerMenu, targetName: String): Int {
        for (i in menu.slots.indices) {
            val slot = menu.slots.getOrNull(i) ?: continue
            if (!slot.hasItem()) continue
            val itemName = slot.item.hoverName.string.replace(Regex("§[0-9a-fk-or]"), "")
            if (itemName.contains(targetName, ignoreCase = true)) {
                return i
            }
        }
        return -1
    }

    fun displayHoverLootTracker(floorInput: String) {
        val floor = floorInput.lowercase()
        val floorData = trackerConfig.data.floors[floor]

        if (floorData == null || floorData.runsOpened == 0) {
            modMessage("§c[AutoCroesus] No tracker data found for floor: §e${floor.uppercase()}")
            return
        }

        val runs = floorData.runsOpened
        val chestCost = floorData.chestCost
        val kismetCost = floorData.kismetCost
        val totalCost = chestCost + kismetCost
        val totalSell = floorData.totalSellPrice
        val totalProfit = totalSell - totalCost
        val profitPerRun = if (runs > 0) totalProfit / runs else 0.0

        val hoverText = Component.literal("§cLoot from %,d runs on ${floor.uppercase()}:\n".format(runs))

        val sortedItems = floorData.items.values.sortedByDescending { it.totalValue }
        var displayed = 0
        var remainingTypes = 0
        var remainingValue = 0.0

        for (item in sortedItems) {
            if (displayed < 20) {
                val unitPrice = item.totalValue / item.count
                val percent = if (totalSell > 0) (item.totalValue / totalSell) * 100 else 0.0
                val originalComponent = item.coloredName.toComponent()
                hoverText.append(Component.literal("§b${item.count}x "))
                hoverText.append(originalComponent) // 直接塞入還原後的 Component
                hoverText.append(Component.literal(" §8(§6${"%,.0f".format(unitPrice)}§8) §f= §e${"%,.0f".format(item.totalValue)} §7(${String.format("%.2f", percent)}%)\n"))
                displayed++
            } else {
                remainingTypes++
                remainingValue += item.totalValue
            }
        }

        if (remainingTypes > 0) {
            hoverText.append(Component.literal("§a... and $remainingTypes more §8(§6${"%,.0f".format(remainingValue)}§8)\n"))
        }

        // 把羽毛和開箱子的成本分開列出，更直觀
        hoverText.append(Component.literal("§cTotal Kismet Cost: ${"%,.0f".format(kismetCost)} §8(${floorData.kismetsUsed} used)\n"))
        hoverText.append(Component.literal("§cTotal Chest Cost: ${"%,.0f".format(chestCost)}\n"))
        hoverText.append(Component.literal("§cTotal Sell Price: ${"%,.0f".format(totalSell)}\n"))
        hoverText.append(Component.literal("§eTotal Profit: ${"%,.0f".format(totalProfit)}\n"))
        hoverText.append(Component.literal("§bProfit/Run: ${"%,.0f".format(profitPerRun)}"))

        val profitColor = if (totalProfit >= 0) "§a" else "§c"
        val mainText = Component.literal("§e[AutoCroesus] §7Total Profits: $profitColor${"%,.0f".format(totalProfit)}§7, Profit/Run: $profitColor${"%,.0f".format(profitPerRun)}")
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(hoverText)) }

        modMessage(mainText)
    }

    fun resetFloorData(floorInput: String) {
        val floor = floorInput.lowercase()
        val globalData = trackerConfig.data

        // 檢查該樓層是否存在，或者是否有開過局數
        val floorData = globalData.floors[floor]
        if (floorData == null || floorData.runsOpened == 0) {
            modMessage("§c[AutoCroesus] No tracker data found to reset for floor: §e${floor.uppercase()}")
            return
        }

        // 從 floors 移除該樓層的資料
        globalData.floors.remove(floor)

        // 立即儲存設定檔
        trackerConfig.save()

        modMessage("§a[AutoCroesus] Successfully reset all tracker data for floor: §e${floor.uppercase()}")
    }
}