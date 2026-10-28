package com.iq200.heigui.features.impl.floor7

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.render.getStringWidth
import com.iq200.heigui.utils.render.text
import com.iq200.heigui.utils.skyblock.dungeon.DungeonClass
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils

data class TermRule(
    var dungeonClass: DungeonClass = DungeonClass.EMPTY,
    var triggerSection: Int = 1,
    var deviceSection: Int = 1
)

data class TermRulesData(
    var rules: MutableList<TermRule> = mutableListOf()
)

object TermInfo : Module(
    name = "Term Info",
    description = "Shows the current Goldor section's terminal, lever, device, and gate progress.",
    category = Category.FLOOR7
) {
    private val rulesConfig = JsonConfig(
        fileName = "term_rules.json",
        typeToken = object : TypeToken<TermRulesData>() {}.type,
        defaultData = { TermRulesData() }
    )

    private val hud by HUD(
        "Term Info",
        "Shows terminal, lever, device, and gate progress for the current Goldor section."
    ) { example ->
        if (!example && (!DungeonUtils.inBoss || !shouldRender)) return@HUD 0 to 0

        val terminalTotal = if (section == SECOND_SECTION) 5 else 4
        val leverLine = "§6Levers ${if (levers >= LEVERS_PER_SECTION) "§a" else "§c"}${levers.coerceAtMost(LEVERS_PER_SECTION)}§8/§a$LEVERS_PER_SECTION"
        val terminalLine = "§6Terms ${if (terminals >= terminalTotal) "§a" else "§c"}${terminals.coerceAtMost(terminalTotal)}§8/§a$terminalTotal"
        val deviceLine = "§6Device ${if (isDeviceComplete(section)) "§a✔" else "§c✘"}"
        val gateLine = "§6Gate ${if (gate) "§a✔" else "§c✘"}"

        text(leverLine, 0, 0, Colors.WHITE)
        text(terminalLine, 0, 9, Colors.WHITE)
        text(deviceLine, 0, 18, Colors.WHITE)
        text(gateLine, 0, 27, Colors.WHITE)

        val width = listOf(leverLine, terminalLine, deviceLine, gateLine)
            .maxOf(::getStringWidth)
        width to 36
    }

    private var shouldRender = false
    private var section = 1
    private var terminals = 0
    private var levers = 0
    private val devices = BooleanArray(MAX_SECTIONS + 1)
    private var gate = false
    private var sectionComplete = false
    private var lastCompleted = 0

    init {
        rulesConfig.load()

        on<ChatPacketEvent> {
            when {
                value == GOLDOR_START_MESSAGE -> {
                    shouldRender = true
                }

                value == GATE_DESTROYED_MESSAGE -> {
                    gate = true
                    if (sectionComplete) advanceSection()
                }

                value == CORE_OPENING_MESSAGE -> {
                    shouldRender = false
                }

                else -> handleCompletion(value)
            }
        }

        on<WorldEvent.Load> {
            shouldRender = false
            resetRun()
        }
    }

    private fun handleCompletion(message: String) {
        val match = COMPLETION_REGEX.matchEntire(message) ?: return
        val type = match.groupValues[3]
        val completed = match.groupValues[4].toIntOrNull() ?: return
        val total = match.groupValues[5].toIntOrNull() ?: return

        // Normally advanceSection() handles this. This fallback also recovers if
        // the gate/completion messages arrive out of order or one is missed.
        if (lastCompleted != 0 && completed < lastCompleted) advanceSection()
        if (completed == lastCompleted) {
            if (type == "device") applyPreDeviceRule(match.groupValues[1])
            return
        }

        when (type) {
            "terminal" -> terminals++
            "lever" -> levers++
            "device" -> setDeviceComplete(section)
        }

        lastCompleted = completed
        if (completed >= total) {
            sectionComplete = true
            if (gate) advanceSection()
        }
    }

    private fun advanceSection() {
        section++
        resetSection()
    }

    private fun resetRun() {
        section = 1
        devices.fill(false)
        resetSection()
    }

    private fun resetSection() {
        terminals = 0
        levers = 0
        gate = false
        sectionComplete = false
        lastCompleted = 0
    }

    private fun applyPreDeviceRule(playerName: String) {
        val dungeonClass = DungeonUtils.dungeonTeammates
            .firstOrNull { it.name.equals(playerName, ignoreCase = true) }
            ?.clazz
            ?.takeUnless { it == DungeonClass.EMPTY }
            ?: return

        val rule = rulesConfig.data.rules.firstOrNull {
            it.dungeonClass == dungeonClass &&
                it.triggerSection == section &&
                it.deviceSection in 1..MAX_SECTIONS &&
                !isDeviceComplete(it.deviceSection)
        } ?: return

        setDeviceComplete(rule.deviceSection)
    }

    private fun isDeviceComplete(targetSection: Int): Boolean =
        targetSection in 1..MAX_SECTIONS && devices[targetSection]

    private fun setDeviceComplete(targetSection: Int) {
        if (targetSection in 1..MAX_SECTIONS) devices[targetSection] = true
    }

    fun addRule(dungeonClass: DungeonClass, triggerSection: Int, deviceSection: Int): Boolean {
        val newRule = TermRule(dungeonClass, triggerSection, deviceSection)
        if (rulesConfig.data.rules.any { it == newRule }) return false
        rulesConfig.update { it.rules.add(newRule) }
        return true
    }

    fun removeRule(index: Int): TermRule? {
        if (index !in rulesConfig.data.rules.indices) return null
        var removed: TermRule? = null
        rulesConfig.update { removed = it.rules.removeAt(index) }
        return removed
    }

    fun getRules(): List<TermRule> = rulesConfig.data.rules.toList()

    private const val SECOND_SECTION = 2
    private const val MAX_SECTIONS = 4
    private const val LEVERS_PER_SECTION = 2
    private const val GOLDOR_START_MESSAGE = "[BOSS] Goldor: Who dares trespass into my domain?"
    private const val GATE_DESTROYED_MESSAGE = "The gate has been destroyed!"
    private const val CORE_OPENING_MESSAGE = "The Core entrance is opening!"
    private val COMPLETION_REGEX = Regex(
        "^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d+)/(\\d+)\\)$"
    )
}
