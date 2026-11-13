package com.iq200.heigui.features.impl.skyblock

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.clickgui.settings.AlwaysActive
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.itemUUID
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.skyblock.Island
import com.iq200.heigui.utils.skyblock.LocationUtils
import net.minecraft.world.inventory.ContainerInput
import java.util.UUID
import kotlin.math.abs

enum class AutoSwapTriggerType {
    POSITION,
    CHAT,
    CHAT_POSITION
}

data class AutoSwapRule(
    val id: String = UUID.randomUUID().toString(),
    val triggerType: AutoSwapTriggerType,
    val itemUuid: String? = null,
    val itemName: String,
    val hotbarSlot: Int,
    val island: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val radius: Double? = null,
    val chatText: String? = null
)

data class AutoSwapData(
    val rules: MutableList<AutoSwapRule> = mutableListOf()
)

@AlwaysActive
object AutoSwap : Module(
    name = "Auto Swap",
    description = "Moves configured items into a hotbar slot when a position, chat, or combined rule is triggered. Check /hg autoswap for full command usage.",
    category = Category.SKYBLOCK
) {
    private val config = JsonConfig(
        fileName = "auto_swap.json",
        typeToken = object : TypeToken<AutoSwapData>() {}.type,
        defaultData = { AutoSwapData() }
    )

    private var pendingRule: AutoSwapRule? = null
    private val triggeredRules = mutableSetOf<String>()

    init {
        config.load()

        on<TickEvent.Start> {
            if (!enabled) return@on

            pendingRule?.let { rule ->
                if (mc.gui.screen() == null && !isPlayerMoving()) {
                    pendingRule = null
                    beginSwap(rule)
                }
                return@on
            }

            if (mc.gui.screen() != null) return@on
            processPositionRules()
        }

        on<ChatPacketEvent> {
            if (!enabled) return@on
            if (pendingRule != null) return@on

            val rule = config.data.rules.firstOrNull { rule ->
                rule.id !in triggeredRules &&
                    rule.triggerType in CHAT_TRIGGER_TYPES &&
                    !rule.itemUuid.isNullOrBlank() &&
                    !rule.chatText.isNullOrBlank() &&
                    value.contains(rule.chatText, ignoreCase = true) &&
                    matchesChatPosition(rule)
            } ?: return@on

            val itemUuid = rule.itemUuid ?: return@on
            if (isInHotbar(itemUuid) || findMainInventorySlot(itemUuid) == null) return@on
            pendingRule = rule
        }

        on<WorldEvent.Load> {
            pendingRule = null
            triggeredRules.clear()
        }
    }

    override fun onEnable() {
        config.load()
        super.onEnable()
    }

    override fun onDisable() {
        pendingRule = null
        super.onDisable()
    }

    fun addPositionRule(hotbarSlot: Int, radius: Double): Boolean {
        val player = mc.player ?: return false
        val stack = player.mainHandItem
        val targetItemUuid = stack.itemUUID
        if (stack.isEmpty || targetItemUuid.isBlank()) {
            modMessage("§cHold a SkyBlock item with a UUID before adding an Auto Swap rule.")
            return false
        }

        val island = LocationUtils.currentArea
        if (island == Island.Unknown) {
            modMessage("§cWait for the current island to load before adding a position rule.")
            return false
        }

        val rule = AutoSwapRule(
            triggerType = AutoSwapTriggerType.POSITION,
            itemUuid = targetItemUuid,
            itemName = stack.hoverName.string,
            hotbarSlot = hotbarSlot,
            island = island.name,
            x = player.x,
            y = player.y,
            z = player.z,
            radius = radius
        )
        config.update { it.rules.add(rule) }
        modMessage(
            "§aAdded position rule for §e${rule.itemName} §aat §e${formatPosition(rule)} §7(radius ${formatRadius(radius)}) " +
                "§aon §e${island.displayName}§a " +
                "to hotbar slot §e${hotbarSlot + 1}§a."
        )
        return true
    }

    fun addChatRule(hotbarSlot: Int, chatText: String): Boolean {
        val player = mc.player ?: return false
        val stack = player.mainHandItem
        val targetItemUuid = stack.itemUUID
        if (stack.isEmpty || targetItemUuid.isBlank()) {
            modMessage("§cHold a SkyBlock item with a UUID before adding an Auto Swap rule.")
            return false
        }

        val message = chatText.trim()
        if (message.isEmpty()) {
            modMessage("§cThe chat trigger text cannot be empty.")
            return false
        }

        val rule = AutoSwapRule(
            triggerType = AutoSwapTriggerType.CHAT,
            itemUuid = targetItemUuid,
            itemName = stack.hoverName.string,
            hotbarSlot = hotbarSlot,
            chatText = message
        )
        config.update { it.rules.add(rule) }
        modMessage(
            "§aAdded chat rule for §e${rule.itemName} §aon §e\"$message\" §ato hotbar slot §e${hotbarSlot + 1}§a."
        )
        return true
    }

    fun addChatPositionRule(hotbarSlot: Int, radius: Double, chatText: String): Boolean {
        val player = mc.player ?: return false
        val stack = player.mainHandItem
        val targetItemUuid = stack.itemUUID
        if (stack.isEmpty || targetItemUuid.isBlank()) {
            modMessage("§cHold a SkyBlock item with a UUID before adding an Auto Swap rule.")
            return false
        }

        val island = LocationUtils.currentArea
        if (island == Island.Unknown) {
            modMessage("§cWait for the current island to load before adding a chat-position rule.")
            return false
        }

        val message = chatText.trim()
        if (message.isEmpty()) {
            modMessage("§cThe chat trigger text cannot be empty.")
            return false
        }

        val rule = AutoSwapRule(
            triggerType = AutoSwapTriggerType.CHAT_POSITION,
            itemUuid = targetItemUuid,
            itemName = stack.hoverName.string,
            hotbarSlot = hotbarSlot,
            island = island.name,
            x = player.x,
            y = player.y,
            z = player.z,
            radius = radius,
            chatText = message
        )
        config.update { it.rules.add(rule) }
        modMessage(
            "§aAdded chat-position rule for §e${rule.itemName} §awhen §e\"$message\" §aappears within " +
                "§e${formatPosition(rule)} §7(radius ${formatRadius(radius)}) §aon §e${island.displayName}§a " +
                "to hotbar slot §e${hotbarSlot + 1}§a."
        )
        return true
    }

    fun getRules(): List<AutoSwapRule> = config.data.rules.toList()

    fun removeRule(index: Int): AutoSwapRule? {
        if (index !in config.data.rules.indices) return null
        var removed: AutoSwapRule? = null
        config.update { removed = it.rules.removeAt(index) }
        triggeredRules.remove(removed?.id)
        return removed
    }

    fun clearRules(): Int {
        val count = config.data.rules.size
        if (count == 0) return 0
        config.update { it.rules.clear() }
        triggeredRules.clear()
        return count
    }

    fun describeRule(rule: AutoSwapRule): String {
        val trigger = when (rule.triggerType) {
            AutoSwapTriggerType.POSITION -> {
                val islandName = Island.entries.firstOrNull { it.name == rule.island }?.displayName ?: rule.island
                "Position ${formatPosition(rule)} radius ${formatRadius(rule.radius ?: 0.0)} on $islandName"
            }

            AutoSwapTriggerType.CHAT -> "Chat \"${rule.chatText.orEmpty()}\""

            AutoSwapTriggerType.CHAT_POSITION -> {
                val islandName = Island.entries.firstOrNull { it.name == rule.island }?.displayName ?: rule.island
                "Chat \"${rule.chatText.orEmpty()}\" + position ${formatPosition(rule)} " +
                    "radius ${formatRadius(rule.radius ?: 0.0)} on $islandName"
            }
        }
        val uuid = rule.itemUuid
            ?.takeIf { it.isNotBlank() }
            ?.let { "UUID …${it.takeLast(8)}" }
            ?: "missing UUID; recreate this rule"
        return "$trigger -> ${rule.itemName} [$uuid] to slot ${rule.hotbarSlot + 1}"
    }

    private fun processPositionRules() {
        val player = mc.player ?: return
        val currentIsland = LocationUtils.currentArea.name
        val matchingRules = config.data.rules.filter { rule ->
            rule.triggerType == AutoSwapTriggerType.POSITION &&
                rule.island == currentIsland &&
                isInsidePositionRule(rule, player.x, player.y, player.z)
        }
        if (isPlayerMoving()) return

        for (rule in matchingRules) {
            if (rule.id in triggeredRules) continue
            if (beginSwap(rule)) {
                return
            }
        }
    }

    private fun beginSwap(rule: AutoSwapRule): Boolean {
        val player = mc.player ?: return false
        if (mc.gui.screen() != null || isPlayerMoving()) return false
        if (player.containerMenu !== player.inventoryMenu) return false

        val itemUuid = rule.itemUuid?.takeIf { it.isNotBlank() } ?: return false
        if (rule.id in triggeredRules || isInHotbar(itemUuid)) return false

        val sourceInventorySlot = findMainInventorySlot(itemUuid) ?: return false
        val sourceMenuSlot = player.inventoryMenu.findSlot(player.inventory, sourceInventorySlot)
        if (sourceMenuSlot.isEmpty) return false
        val gameMode = mc.gameMode ?: return false

        gameMode.handleContainerInput(
            player.inventoryMenu.containerId,
            sourceMenuSlot.asInt,
            rule.hotbarSlot,
            ContainerInput.SWAP,
            player
        )
        triggeredRules.add(rule.id)
        player.closeContainer()
        return true
    }

    private fun findMainInventorySlot(targetItemUuid: String): Int? {
        val inventory = mc.player?.inventory ?: return null
        return (9 until 36).firstOrNull { inventory.getItem(it).itemUUID == targetItemUuid }
    }

    private fun isInHotbar(targetItemUuid: String): Boolean {
        val inventory = mc.player?.inventory ?: return false
        return (0 until 9).any { inventory.getItem(it).itemUUID == targetItemUuid }
    }

    private fun isPlayerMoving(): Boolean {
        val movement = mc.player?.deltaMovement ?: return true
        return abs(movement.x) > MOVEMENT_EPSILON || abs(movement.z) > MOVEMENT_EPSILON
    }

    private fun isInsidePositionRule(rule: AutoSwapRule, x: Double, y: Double, z: Double): Boolean {
        val targetX = rule.x ?: return false
        val targetY = rule.y ?: return false
        val targetZ = rule.z ?: return false
        val radius = rule.radius?.takeIf { it > 0.0 } ?: return false
        val dx = x - targetX
        val dy = y - targetY
        val dz = z - targetZ
        return dx * dx + dy * dy + dz * dz <= radius * radius
    }

    private fun matchesChatPosition(rule: AutoSwapRule): Boolean {
        if (rule.triggerType == AutoSwapTriggerType.CHAT) return true
        val player = mc.player ?: return false
        return rule.triggerType == AutoSwapTriggerType.CHAT_POSITION &&
            rule.island == LocationUtils.currentArea.name &&
            isInsidePositionRule(rule, player.x, player.y, player.z)
    }

    private fun formatPosition(rule: AutoSwapRule): String =
        "%.2f, %.2f, %.2f".format(rule.x, rule.y, rule.z)

    private fun formatRadius(radius: Double): String = "%.2f".format(radius)

    private const val MOVEMENT_EPSILON = 0.003
    private val CHAT_TRIGGER_TYPES = setOf(AutoSwapTriggerType.CHAT, AutoSwapTriggerType.CHAT_POSITION)
}
