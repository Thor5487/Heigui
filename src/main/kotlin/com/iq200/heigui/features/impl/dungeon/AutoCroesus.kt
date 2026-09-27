package com.iq200.heigui.features.impl.dungeon

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.lore
import com.iq200.heigui.utils.loreString
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.skyblock.PriceParser
import com.iq200.heigui.utils.skyblock.PriceUtils
import com.iq200.heigui.utils.toComponent
import com.iq200.heigui.utils.toJsonString
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.component.SwingAnimation
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
    var totalValue: Double = 0.0
)

data class FloorTracker(
    var runsOpened: Int = 0,
    var kismetsUsed: Int = 0,
    var kismetCost: Double = 0.0,
    var keysUsed: Int = 0,
    var keyCost: Double = 0.0,
    var chestCost: Double = 0.0,
    val items: MutableMap<String, TrackerItem> = mutableMapOf()
)


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
    private val clickDelay by NumberSetting("Click Delay", 150, 50, 1000, 50, "minimum delay between each click", "ms")
    private val useKismets by BooleanSetting("Kismet", true, "use kismets or not")
    private val targetProfit by NumberSetting("Target Profit", 5, 1, 100, 1, "rerolls the chest if current profit is below this value", "m").withDependency { useKismets }
    private val useKey by BooleanSetting("Key", false, "use dungeon chest key or not")
    private val keyTargetProfit by NumberSetting("Key Target Profit", 5, 1, 100, 1, "minimum profit to use a key", "m").withDependency { useKey }


    private var pendingKeyRun = false
    private var pendingKeyRunSlot = -1
    private var pendingKeyRunMenuTitle = ""
    private var pendingKeyChestSlot = -1
    private var pendingKeyChestData: ChestData? = null

    private var currentRunIsKey = false
    private var currentRunCroesusSlot = -1
    private var currentRunCroesusMenuTitle = ""

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
        WAITING_FOR_NEXT_PAGE,
        SCANNING_MAIN_PAGE,
        WAITING_FOR_CHEST_MENU,
        INSIDE_LOOT_CHEST,
        WAITING_FOR_CONFIRM_MENU,
        IN_CONFIRM_MENU,
        WAITING_FOR_REOPEN
    }

    private var intendingToReroll = false
    private var currentState = CroesusState.IDLE
    private var lastActionTime = 0L

    private var lastMenuTitle = ""

    private var currentFloor: String = "unknown"
    private var currentRunKismets: Int = 0
    private var pendingChestData: ChestData? = null

    init {
        ignoreConfig.load()
        trackerConfig.load()

        on<ChatPacketEvent> {
            if (!isWorking) return@on
            val cleanMsg = value.replace(Regex("§[0-9a-fk-or]"), "")

            if (cleanMsg.contains("You need a Dungeon Chest Key to open another chest!")) {
                modMessage("§c[AutoCroesus] Out of Dungeon Chest Keys! Script paused.")
                modMessage("§e[AutoCroesus] Buy a key and type /hg ac go to resume.")


                currentState = CroesusState.IDLE
                pendingChestData = null


                stop(clearPending = false)
            }
        }

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
                if (mc.gui.screen() == null) {
                    saveRunRecord(pendingChestData)
                    pendingChestData = null

                    if (currentRunIsKey) {
                        pendingKeyRun = false
                        pendingKeyRunSlot = -1
                        pendingKeyRunMenuTitle = ""
                        pendingKeyChestSlot = -1
                        pendingKeyChestData = null
                    }

                    mc.execute {
                        val player = mc.player ?: return@execute
                        val npc = findCroesusNPC(5.0)
                        if (npc == null) {
                            modMessage("§c[AutoCroesus] Error: Croesus NPC not found nearby after reopening!")
                            stop()
                            return@execute
                        }
                        mc.gameMode?.attack(player, npc as Entity)
                        player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
                        player.connection.send(ServerboundPunchPacket.INSTANCE)
                        currentState = CroesusState.WAITING_FOR_MENU
                        lastActionTime = System.currentTimeMillis()
                    }
                }

                return@on
            }

            val currentScreen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return@on
            val menuTitle = currentScreen.title.string.replace(Regex("§[0-9a-fk-or]"), "")


            when (currentState) {
                CroesusState.WAITING_FOR_MENU -> handleWaitingForMenu(menuTitle)
                CroesusState.WAITING_FOR_NEXT_PAGE -> handleWaitingForNextPage(menuTitle)
                CroesusState.SCANNING_MAIN_PAGE -> handleScanningMainPage(currentScreen, currentTime)
                CroesusState.WAITING_FOR_CHEST_MENU -> handleWaitingForChestMenu(menuTitle)
                CroesusState.INSIDE_LOOT_CHEST -> handleInsideLootChests(currentScreen, currentTime)
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
        val cleanMenuTitle = currentScreen.title.string.noControlCodes

        val player = mc.player ?: return

        for (i in 10..43) {
            val slot = menu.slots.getOrNull(i) ?: continue
            if (!slot.hasItem()) continue

            val lore = slot.item.loreString
            val loreComponents = slot.item.lore

            val isValidTarget = if (pendingKeyRun) {
                cleanMenuTitle == pendingKeyRunMenuTitle && i == pendingKeyRunSlot && loreComponents.any {
                    it.string.contains("Dungeon Chest Key", ignoreCase = true) &&
                            !it.toString().contains("strikethrough", ignoreCase = true)
                }
            } else {
                lore.any { it.contains("No chests opened yet!") }
            }

            if (isValidTarget) {
                val cleanName = slot.item.hoverName.string.replace(Regex("§[0-9a-fk-or]"), "")
                currentFloor = parseFloor(cleanName, lore)
                currentRunKismets = 0

                currentRunIsKey = pendingKeyRun
                currentRunCroesusSlot = i
                currentRunCroesusMenuTitle = cleanMenuTitle

                val kismetComp = loreComponents.find { it.string.contains("Kismet Feather") }
                if (kismetComp != null) {
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
                lastMenuTitle = currentScreen.title.string.replace(Regex("§[0-9a-fk-or]"), "")

                mc.gameMode?.handleContainerInput(menu.containerId, 53, 0, ContainerInput.PICKUP, player)

                currentState = CroesusState.WAITING_FOR_NEXT_PAGE
                lastActionTime = currentTime
            } else {
                if (pendingKeyRun) {

                    modMessage("§c[AutoCroesus] Warning: Pending key run, but no available Dungeon Chest Keys found.")
                    pendingKeyRun = false
                    pendingKeyRunSlot = -1
                    pendingKeyRunMenuTitle = ""
                    stop()
                } else {
                    modMessage("§a[AutoCroesus] Finished! All pages have been scanned and no unopened chests remain.")
                    stop()
                }
            }
        }
    }

    private fun handleWaitingForChestMenu(menuTitle: String) {
        if (menuTitle.contains("Catacombs", ignoreCase = true)) {
            currentState = CroesusState.INSIDE_LOOT_CHEST
        }
    }

    private fun handleWaitingForNextPage(menuTitle: String) {
        if (menuTitle.contains("Croesus") && menuTitle != lastMenuTitle) {
            currentState = CroesusState.SCANNING_MAIN_PAGE
        }
    }

    private fun handleInsideLootChests(currentScreen: AbstractContainerScreen<*>, currentTime: Long) {
        val menu = currentScreen.menu
        val player = mc.player ?: return

        if (pendingKeyRun && pendingKeyChestSlot != -1) {
            pendingChestData = pendingKeyChestData
            mc.gameMode?.handleContainerInput(menu.containerId, pendingKeyChestSlot, 0, ContainerInput.PICKUP, player)
            currentState = CroesusState.WAITING_FOR_CONFIRM_MENU
            lastActionTime = currentTime

            return
        }

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

        val evaluatedChests = foundChests.map { Pair(it.first, calculateChestData(it.second.lore, it.second.loreString)) }
        val sortedChests = evaluatedChests.sortedByDescending { it.second.profit }

        val bestChestSlot = sortedChests.getOrNull(0)?.first ?: -1
        val bestChestData = sortedChests.getOrNull(0)?.second
        val maxProfit = bestChestData?.profit ?: Double.NEGATIVE_INFINITY


        val secondChestSlot = sortedChests.getOrNull(1)?.first ?: -1
        val secondChestData = sortedChests.getOrNull(1)?.second

        var bedrockChestSlot = -1
        var bedrockChestData: ChestData? = null
        val bedrockEntry = foundChests.find { it.second.hoverName.string.contains("Bedrock", ignoreCase = true) }

        if (bedrockEntry != null) {
            bedrockChestSlot = bedrockEntry.first
            bedrockChestData = evaluatedChests.find { it.first == bedrockChestSlot }?.second
        }

        makeDecision(menu.containerId, player, maxProfit, bestChestSlot, bestChestData, bedrockChestSlot, bedrockChestData, secondChestSlot, secondChestData, menu)
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
                player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
                player.connection.send(ServerboundPunchPacket.INSTANCE)
                startProcess()

            }
        }

    }

    fun help() {
        modMessage("§aAutoCroesus Commands:")
        modMessage("§e/hg ac go §8- §7Start AutoCroesus. Requires the module to be enabled and Croesus nearby.", prefix = "")
        modMessage("§e/hg ac update §8- §7Update item prices used for chest profit checks.", prefix = "")
        modMessage("§e/hg ac ignore add <item> §8- §7Ignore items matching the given name or keyword.", prefix = "")
        modMessage("§e/hg ac ignore remove <item> §8- §7Remove a name or keyword from the ignore list.", prefix = "")
        modMessage("§e/hg ac ignore list §8- §7Show the current ignore list.", prefix = "")
        modMessage("§e/hg ac loot <floor> §8- §7Show tracked loot stats for a floor, for example m6 or f7.", prefix = "")
        modMessage("§e/hg ac loot reset <floor> §8- §7Reset tracked loot stats for a floor.", prefix = "")
        modMessage("§e/hg ac help §8- §7Show this help message.", prefix = "")
    }

    private fun findCroesusNPC(maxDistance: Double): Player? {
        val level = mc.level ?: return null

        val center = mc.player?.position() ?: return null


        val croesusStands = level.entitiesForRendering()
            .filterIsInstance<ArmorStand>()
            .filter { it.distanceToSqr(center) < maxDistance }
            .filter { it.name.string.contains("Croesus", ignoreCase = true) }

        if (croesusStands.isEmpty()) return null



        for (stand in croesusStands) {
            val standPos = stand.position()

            val targetPlayer = level.players().find { player ->

                if (player == mc.player) return@find false


                val dist = player.position().distanceToSqr(standPos)
                dist == 0.0
            }

            if (targetPlayer != null) return targetPlayer
        }

        return null
    }


    fun stop(clearPending: Boolean = true) {
        if (isWorking) {
            isWorking = false
            if (clearPending) {
                pendingKeyRun = false
                pendingKeyRunSlot = -1
                pendingKeyRunMenuTitle = ""
                pendingKeyChestSlot = -1
                pendingKeyChestData = null
            }
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

        if (!currentRunIsKey) {
            floorData.runsOpened++
        }


        val kismetCost = PriceParser.parseItemValue("Kismet Feather") * currentRunKismets


        floorData.kismetsUsed += currentRunKismets
        floorData.kismetCost += kismetCost


        if (boughtData != null) {
            floorData.chestCost += boughtData.cost


            if (currentRunIsKey) {
                floorData.keysUsed++
                floorData.keyCost += PriceParser.parseItemValue("Dungeon Chest Key")
            }

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
                    val itemValue = PriceParser.parseItemValue(cleanLine)
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

    private fun makeDecision(containerId: Int, player: Player, maxProfit: Double, bestChestSlot: Int, bestChestData: ChestData?, bedrockChestSlot: Int, bedrockChestData: ChestData?, secondChestSlot: Int, secondChestData: ChestData?, menu: AbstractContainerMenu) {
        val targetProfitCoins = targetProfit * 1_000_000.0


        if (useKismets && currentKismetAvailable && bedrockChestSlot != -1 && bedrockChestData != null) {


            if (bedrockChestData.profit < targetProfitCoins) {
                intendingToReroll = true
                mc.gameMode?.handleContainerInput(containerId, bedrockChestSlot, 0, ContainerInput.PICKUP, player)

                currentState = CroesusState.WAITING_FOR_CONFIRM_MENU
                lastActionTime = System.currentTimeMillis()
                return
            }
        }


        intendingToReroll = false
        if (maxProfit > 0 && bestChestSlot != -1 && bestChestData != null) {
            val keyTargetProfitCoins = keyTargetProfit * 1_000_000.0
            if (useKey && secondChestSlot != -1 && secondChestData != null && secondChestData.profit >= keyTargetProfitCoins) {
                pendingKeyRun = true
                pendingKeyRunSlot = currentRunCroesusSlot
                pendingKeyRunMenuTitle = currentRunCroesusMenuTitle
                pendingKeyChestSlot = secondChestSlot
                pendingKeyChestData = secondChestData
            }

            pendingChestData = bestChestData
            mc.gameMode?.handleContainerInput(containerId, bestChestSlot, 0, ContainerInput.PICKUP, player)

            currentState = CroesusState.WAITING_FOR_CONFIRM_MENU
            lastActionTime = System.currentTimeMillis()
        } else {

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


        if (targetChestTitles.any { menuTitle.contains(it, ignoreCase = true) }) {
            currentState = CroesusState.IN_CONFIRM_MENU
        }

    }


    private fun handleInConfirmMenu(currentScreen: AbstractContainerScreen<*>, currentTime: Long) {
        val menu = currentScreen.menu
        val player = mc.player ?: return



        if (intendingToReroll) {
            // ============================

            // ============================
            val rerollSlot = findSlotByItemName(menu, "Reroll")
            if (rerollSlot != -1) {
                mc.gameMode?.handleContainerInput(menu.containerId, rerollSlot, 0, ContainerInput.PICKUP, player)


                currentRunKismets++
                currentKismetAvailable = false
                intendingToReroll = false


                currentState = CroesusState.WAITING_FOR_CHEST_MENU
                lastActionTime = currentTime
            } else {
                modMessage("§c[AutoCroesus] Warning: Intended to reroll but 'Reroll' button not found! Aborting.")
                stop()
            }
        } else {
            // ============================

            // ============================
            val confirmSlot = findSlotByItemName(menu, "Open Reward Chest")
            if (confirmSlot != -1) {
                mc.gameMode?.handleContainerInput(menu.containerId, confirmSlot, 0, ContainerInput.PICKUP, player)


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
        val keyCost = floorData.keyCost
        val totalCost = chestCost + kismetCost + keyCost
        val totalSell = floorData.items.values.sumOf { it.totalValue }
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
                hoverText.append(originalComponent)
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


        hoverText.append(Component.literal("§cTotal Kismet Cost: ${"%,.0f".format(kismetCost)} §8(${floorData.kismetsUsed} used)\n"))
        hoverText.append(Component.literal("§cTotal Key Cost: ${"%,.0f".format(keyCost)} §8(${floorData.keysUsed} used)\n"))
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


        val floorData = globalData.floors[floor]
        if (floorData == null || floorData.runsOpened == 0) {
            modMessage("§c[AutoCroesus] No tracker data found to reset for floor: §e${floor.uppercase()}")
            return
        }


        globalData.floors.remove(floor)


        trackerConfig.save()

        modMessage("§a[AutoCroesus] Successfully reset all tracker data for floor: §e${floor.uppercase()}")
    }
}
