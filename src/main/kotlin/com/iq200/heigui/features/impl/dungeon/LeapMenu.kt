package com.iq200.heigui.features.impl.dungeon

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.Heigui
import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.clickgui.settings.impl.KeybindSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.ScreenEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color.Companion.withAlpha
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.clickSlot
import com.iq200.heigui.utils.equalsOneOf
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.sendCommand
import com.iq200.heigui.utils.skyblock.dungeon.DungeonClass
import com.iq200.heigui.utils.skyblock.dungeon.DungeonListener
import com.iq200.heigui.utils.skyblock.dungeon.DungeonPlayer
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.network.hypixelapi.RequestUtils
import com.iq200.heigui.utils.ui.rendering.GuiRenderer
import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import java.util.concurrent.ConcurrentHashMap

data class LeapClassRule(
    val uuid: String = "",
    var fakeClass: DungeonClass = DungeonClass.EMPTY
)

data class LeapClassRulesData(
    val rules: MutableList<LeapClassRule> = mutableListOf()
)

data class LeapClassRuleDisplay(
    val name: String?,
    val fakeClass: DungeonClass
)

private data class CachedLeapClassRule(
    val uuid: String,
    val name: String,
    val fakeClass: DungeonClass
)

object LeapMenu : Module(
    name = "Leap Menu",
    description = "Replaces the Spirit Leap container with a sortable four-quadrant menu. Check /hg leap to configure custom fake classes.",
    category = Category.DUNGEON
) {
    private val rulesConfig = JsonConfig(
        fileName = "leap_rules.json",
        typeToken = object : TypeToken<LeapClassRulesData>() {}.type,
        defaultData = { LeapClassRulesData() }
    )
    private val cachedRulesByUuid = ConcurrentHashMap<String, CachedLeapClassRule>()
    private val cachedRulesByName = ConcurrentHashMap<String, CachedLeapClassRule>()

    private val sorting by SelectorSetting(
        "Sorting",
        "Odin Sorting",
        listOf("Odin Sorting", "A-Z Class"),
        "Controls how teammates are positioned in the leap menu."
    )
    private val leftClickOnly by BooleanSetting(
        "Left Click Only",
        false,
        "Only allow the left mouse button to select a teammate."
    )
    private val autoClose by BooleanSetting(
        "Auto Close",
        true,
        "Close the leap menu immediately after selecting a teammate."
    )
    private val classColorBackground by BooleanSetting(
        "Class Color Background",
        false,
        "Use each teammate's class color as their menu background."
    )
    private val backgroundColor by ColorSetting(
        "Background Color",
        Colors.gray38.withAlpha(0.75f),
        true,
        "Color used for menu backgrounds."
    ).withDependency { !classColorBackground }
    private val scale by NumberSetting(
        "Render Scale",
        1f,
        0.1f,
        2f,
        0.1f,
        desc = "Scale of the leap menu.",
        unit = "x"
    )
    private val keybindLeap by BooleanSetting(
        "Keybind Leap",
        false,
        "Allow selecting leap targets with configurable keybinds."
    )
    private val keybindMode by SelectorSetting(
        "Keybind Mode",
        "Corners",
        listOf("Corners", "Classes"),
        "Choose whether keybinds select a screen corner or a dungeon class."
    ).withDependency { keybindLeap }

    private val topLeftKeybind by KeybindSetting("Top Left", InputConstants.UNKNOWN, "Leap to the top-left teammate.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CORNERS }
    private val topRightKeybind by KeybindSetting("Top Right", InputConstants.UNKNOWN, "Leap to the top-right teammate.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CORNERS }
    private val bottomLeftKeybind by KeybindSetting("Bottom Left", InputConstants.UNKNOWN, "Leap to the bottom-left teammate.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CORNERS }
    private val bottomRightKeybind by KeybindSetting("Bottom Right", InputConstants.UNKNOWN, "Leap to the bottom-right teammate.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CORNERS }

    private val archerKeybind by KeybindSetting("Archer", InputConstants.UNKNOWN, "Leap to the Archer.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CLASSES }
    private val berserkerKeybind by KeybindSetting("Berserker", InputConstants.UNKNOWN, "Leap to the Berserker.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CLASSES }
    private val healerKeybind by KeybindSetting("Healer", InputConstants.UNKNOWN, "Leap to the Healer.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CLASSES }
    private val mageKeybind by KeybindSetting("Mage", InputConstants.UNKNOWN, "Leap to the Mage.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CLASSES }
    private val tankKeybind by KeybindSetting("Tank", InputConstants.UNKNOWN, "Leap to the Tank.")
        .withDependency { keybindLeap && keybindMode == KEYBINDS_CLASSES }

    private val leapAnnounce by BooleanSetting("Leap Announce", false, "Announce successful leaps in party chat.")

    init {
        rulesConfig.load()
        refreshRuleCache()

        on<ScreenEvent.Render> {
            val leapScreen = currentLeapScreen() ?: return@on
            val teammates = sortedLeapTeammates()
            renderMenu(guiGraphics, mouseX, mouseY, teammates)
            cancel()
        }

        on<ScreenEvent.MouseClick> {
            val leapScreen = currentLeapScreen() ?: return@on
            if (isAllowedMouseButton(click.button())) {
                triggerMouseQuadrant(leapScreen, click.x().toInt(), click.y().toInt())
            }
            cancel()
        }

        on<ScreenEvent.KeyPress> {
            val leapScreen = currentLeapScreen() ?: return@on
            if (!keybindLeap) return@on
            val pressed = InputConstants.getKey(input)
            val player = if (keybindMode == KEYBINDS_CORNERS) {
                val index = cornerKeybinds().indexOf(pressed)
                if (index < 0) return@on
                sortedLeapTeammates().getOrNull(index)
            } else {
                val clazz = classKeybinds().firstOrNull { it.second == pressed }?.first ?: return@on
                val duplicatedClasses = duplicatedClasses()
                sortedLeapTeammates().filterNotNull().firstOrNull {
                    effectiveClass(it, duplicatedClasses) == clazz
                }
            } ?: return@on

            triggerLeap(player, leapScreen)
            cancel()
        }

        on<ChatPacketEvent> {
            if (!leapAnnounce || !DungeonUtils.inDungeons) return@on
            val playerName = LEAPED_REGEX.find(value)?.groupValues?.get(1) ?: return@on
            sendCommand("pc Leaped to $playerName!")
        }
    }

    fun addRule(fakeClass: DungeonClass, playerName: String) {
        val requestedName = playerName.trim()
        if (!USERNAME_REGEX.matches(requestedName)) {
            modMessage("§c'$requestedName' is not a valid Minecraft username.")
            return
        }

        modMessage("§eLooking up §f$requestedName§e...")
        Heigui.scope.launch(Dispatchers.IO) {
            RequestUtils.getUuid(requestedName)
                .onSuccess { profile ->
                    mc.execute {
                        val uuid = normalizeUuid(profile.id)
                        var previousClass: DungeonClass? = null
                        rulesConfig.update { data ->
                            val existing = data.rules.firstOrNull { normalizeUuid(it.uuid) == uuid }
                            if (existing == null) {
                                data.rules += LeapClassRule(uuid, fakeClass)
                            } else {
                                previousClass = existing.fakeClass
                                existing.fakeClass = fakeClass
                            }
                        }

                        cacheResolvedRule(LeapClassRule(uuid, fakeClass), profile.name)
                        when (val oldClass = previousClass) {
                            null -> modMessage(
                                "§aAdded leap rule: §e${profile.name} §7→ §e${formatClass(fakeClass)}§a."
                            )

                            fakeClass -> modMessage(
                                "§e${profile.name}§a's fake class is already §e${formatClass(fakeClass)}§a."
                            )

                            else -> modMessage(
                                "§aUpdated §e${profile.name}§a's fake class from §e${formatClass(oldClass)} " +
                                    "§ato §e${formatClass(fakeClass)}§a."
                            )
                        }
                    }
                }
                .onFailure {
                    modMessage("§cCould not find Minecraft player '$requestedName'.")
                }
        }
    }

    fun getRuleDisplays(): List<LeapClassRuleDisplay> = rulesConfig.data.rules.map { rule ->
        LeapClassRuleDisplay(
            name = cachedRulesByUuid[normalizeUuid(rule.uuid)]?.name,
            fakeClass = rule.fakeClass
        )
    }

    fun removeRule(index: Int): LeapClassRuleDisplay? {
        if (index !in rulesConfig.data.rules.indices) return null

        var removed: LeapClassRule? = null
        rulesConfig.update { removed = it.rules.removeAt(index) }
        val rule = removed ?: return null
        val uuid = normalizeUuid(rule.uuid)
        val cached = cachedRulesByUuid.remove(uuid)
        cached?.let { cachedRulesByName.remove(it.name.lowercase(), it) }
        return LeapClassRuleDisplay(cached?.name, rule.fakeClass)
    }

    private fun refreshRuleCache() {
        cachedRulesByUuid.clear()
        cachedRulesByName.clear()
        val rules = rulesConfig.data.rules.toList()

        Heigui.scope.launch(Dispatchers.IO) {
            rules.forEach { savedRule ->
                RequestUtils.getName(savedRule.uuid)
                    .onSuccess { profile ->
                        mc.execute {
                            val currentRule = rulesConfig.data.rules.firstOrNull {
                                normalizeUuid(it.uuid) == normalizeUuid(savedRule.uuid)
                            } ?: return@execute
                            cacheResolvedRule(currentRule, profile.name)
                        }
                    }
                    .onFailure {
                        Heigui.logger.warn("Failed to resolve leap rule UUID ${savedRule.uuid}: ${it.message}")
                    }
            }
        }
    }

    private fun cacheResolvedRule(rule: LeapClassRule, name: String) {
        val uuid = normalizeUuid(rule.uuid)
        cachedRulesByUuid.remove(uuid)?.let { old ->
            cachedRulesByName.remove(old.name.lowercase(), old)
        }

        val cached = CachedLeapClassRule(uuid, name, rule.fakeClass)
        cachedRulesByUuid[uuid] = cached
        cachedRulesByName[name.lowercase()] = cached
    }

    private fun currentLeapScreen(): AbstractContainerScreen<*>? {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return null
        if (!screen.title.string.equalsOneOf("Spirit Leap", "Teleport to Player")) return null
        if (DungeonListener.dungeonTeammatesNoSelf.isEmpty()) return null
        return screen
    }

    private fun triggerMouseQuadrant(screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int) {
        val quadrant = (if (mouseY >= screen.height / 2) 2 else 0) +
            (if (mouseX >= screen.width / 2) 1 else 0)
        val player = sortedLeapTeammates().getOrNull(quadrant) ?: return
        triggerLeap(player, screen)
    }

    private fun isAllowedMouseButton(button: Int): Boolean =
        !leftClickOnly || button == InputConstants.MOUSE_BUTTON_LEFT

    private fun triggerLeap(player: DungeonPlayer, screen: AbstractContainerScreen<*>) {
        if (player.isDead) {
            modMessage("§c${player.name} is dead and cannot be leaped to.")
            return
        }

        val slots = screen.menu.slots
        val leapSlot = slots.subList(LEAP_SLOT_START.coerceAtMost(slots.size), LEAP_SLOT_END.coerceAtMost(slots.size))
            .firstOrNull { slot ->
                slot.item.hoverName.string
                    .substringAfter(' ')
                    .noControlCodes
                    .equals(player.name, ignoreCase = true)
            } ?: return

        mc.player?.clickSlot(screen.menu.containerId, leapSlot.index)
        if (autoClose) mc.player?.closeContainer()
        modMessage("§aTeleporting to §e${player.name}§a.")
    }

    private fun renderMenu(
        graphics: net.minecraft.client.gui.GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        teammates: List<DungeonPlayer?>
    ) {
        val halfWidth = mc.window.guiScaledWidth / 2
        val halfHeight = mc.window.guiScaledHeight / 2

        GuiRenderer.render(graphics) {
            repeat(4) { index ->
                val player = teammates.getOrNull(index) ?: return@repeat
                val column = index % 2
                val row = index / 2
                val nearX = if (column == 0) halfWidth - QUADRANT_GAP else halfWidth + QUADRANT_GAP
                val nearY = if (row == 0) halfHeight - QUADRANT_GAP else halfHeight + QUADRANT_GAP
                val localX = if (column == 0) -BOX_WIDTH else 0
                val localY = if (row == 0) -BOX_HEIGHT else 0
                val hovered = (if (column == 0) mouseX < halfWidth else mouseX >= halfWidth) &&
                    (if (row == 0) mouseY < halfHeight else mouseY >= halfHeight)
                val grow = if (hovered) HOVER_GROW else 0f

                GuiRenderer.push()
                GuiRenderer.translate(nearX.toFloat(), nearY.toFloat())
                GuiRenderer.scale(
                    scale * (BOX_WIDTH + grow * 2f) / BOX_WIDTH,
                    scale * (BOX_HEIGHT + grow * 2f) / BOX_HEIGHT
                )

                val panelColor = if (classColorBackground) player.clazz.color else backgroundColor
                GuiRenderer.rect(
                    localX.toFloat(),
                    localY.toFloat(),
                    BOX_WIDTH.toFloat(),
                    BOX_HEIGHT.toFloat(),
                    panelColor.rgba,
                    9f
                )

                val faceSize = (BOX_HEIGHT * 0.76f).toInt()
                (player.playerSkin ?: mc.player?.skin)?.let { skin ->
                    PlayerFaceExtractor.extractRenderState(
                        graphics,
                        skin,
                        localX + 9,
                        localY + 9,
                        faceSize
                    )
                }

                val primaryColor = if (classColorBackground) Colors.WHITE else player.clazz.color
                GuiRenderer.text(
                    player.name,
                    (localX + 15 + faceSize).toFloat(),
                    (localY + 24).toFloat(),
                    16f,
                    primaryColor.rgba
                )

                GuiRenderer.text(
                    if (player.isDead) "DEAD" else player.clazz.name.lowercase(),
                    (localX + 15 + faceSize).toFloat(),
                    (localY + 45).toFloat(),
                    14f,
                    if (player.isDead) Colors.MINECRAFT_RED.rgba else Colors.WHITE.rgba
                )

                GuiRenderer.pop()
            }
        }
    }

    private fun sortedLeapTeammates(): List<DungeonPlayer?> {
        val players = DungeonListener.dungeonTeammatesNoSelf.take(4)
        val duplicatedClasses = duplicatedClasses()
        return when (sorting) {
            SORT_ODIN -> odinSorting(players, duplicatedClasses)
            else -> players.sortedWith(
                compareBy<DungeonPlayer>(
                    { effectiveClass(it, duplicatedClasses).ordinal },
                    { it.name.lowercase() }
                )
            ).map { it }
        }
    }

    /** Places classes in Odin's default quadrants, then fills collisions into the remaining empty quadrants. */
    private fun odinSorting(
        players: List<DungeonPlayer>,
        duplicatedClasses: Set<DungeonClass>
    ): List<DungeonPlayer?> {
        val result = MutableList<DungeonPlayer?>(4) { null }
        val overflow = mutableListOf<DungeonPlayer>()

        players.sortedWith(
            compareBy<DungeonPlayer>(
                { effectiveClass(it, duplicatedClasses).priority },
                { it.name.lowercase() }
            )
        ).forEach { player ->
            val quadrant = effectiveClass(player, duplicatedClasses).defaultQuadrant
            if (result[quadrant] == null) result[quadrant] = player else overflow += player
        }

        result.indices.forEach { index ->
            if (result[index] == null && overflow.isNotEmpty()) result[index] = overflow.removeAt(0)
        }
        return result
    }

    private fun duplicatedClasses(): Set<DungeonClass> = DungeonListener.dungeonTeammates
        .asSequence()
        .map { it.clazz }
        .filter { it != DungeonClass.EMPTY }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    private fun effectiveClass(
        player: DungeonPlayer,
        duplicatedClasses: Set<DungeonClass>
    ): DungeonClass {
        if (player.clazz !in duplicatedClasses) return player.clazz
        return cachedRulesByName[player.name.lowercase()]?.fakeClass ?: player.clazz
    }

    private fun normalizeUuid(uuid: String): String = uuid.replace("-", "").lowercase()

    private fun formatClass(clazz: DungeonClass): String =
        clazz.name.lowercase().replaceFirstChar(Char::titlecase)

    private fun cornerKeybinds() = listOf(
        topLeftKeybind,
        topRightKeybind,
        bottomLeftKeybind,
        bottomRightKeybind
    )

    private fun classKeybinds() = listOf(
        DungeonClass.ARCHER to archerKeybind,
        DungeonClass.BERSERK to berserkerKeybind,
        DungeonClass.HEALER to healerKeybind,
        DungeonClass.MAGE to mageKeybind,
        DungeonClass.TANK to tankKeybind
    )

    private const val SORT_ODIN = 0
    private const val KEYBINDS_CORNERS = 0
    private const val KEYBINDS_CLASSES = 1
    private const val BOX_WIDTH = 200
    private const val BOX_HEIGHT = 75
    private const val QUADRANT_GAP = 24
    private const val HOVER_GROW = 5f
    private const val LEAP_SLOT_START = 11
    private const val LEAP_SLOT_END = 16
    private val USERNAME_REGEX = Regex("^[A-Za-z0-9_]{1,16}$")
    private val LEAPED_REGEX = Regex("^You have teleported to (\\w{1,16})!$")
}
