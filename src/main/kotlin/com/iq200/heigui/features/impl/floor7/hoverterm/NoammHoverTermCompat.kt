package com.iq200.heigui.features.impl.floor7.hoverterm

import com.github.noamm9.event.EventBus
import com.github.noamm9.event.EventListener
import com.github.noamm9.event.impl.ScreenEvent
import com.github.noamm9.event.priority.EventPriority
import com.github.noamm9.features.Feature
import com.github.noamm9.features.impl.floor7.terminals.TerminalListener
import com.github.noamm9.features.impl.floor7.terminals.TerminalSolver
import com.github.noamm9.features.impl.floor7.terminals.impl.ColorsTerminal
import com.github.noamm9.features.impl.floor7.terminals.impl.MelodyTerminal
import com.github.noamm9.features.impl.floor7.terminals.impl.NumberTerminal
import com.github.noamm9.features.impl.floor7.terminals.impl.RedGreenTerminal
import com.github.noamm9.features.impl.floor7.terminals.impl.RubixTerminal
import com.github.noamm9.features.impl.floor7.terminals.impl.StartWithTerminal
import com.github.noamm9.features.impl.floor7.terminals.impl.Terminal

internal object NoammHoverTermCompat : HoverTermCompat {
    private const val RUBIX_SAME_SLOT_MIN_DELAY_MS = 50L

    private val hoveredSlotField = TerminalSolver::class.java.getDeclaredField("hoveredSlot").apply {
        isAccessible = true
    }

    private var lastClickTime = 0L
    private var lastClickedSlot: Int? = null

    private val renderListener: EventListener<ScreenEvent.PreRender> = EventBus.listener(
        priority = EventPriority.LOWEST,
        receiveCancelled = true
    ) {
        if (
            HoverTerm.mode != "Noamm" ||
            !TerminalSolver.enabled ||
            !TerminalListener.inTerm ||
            !event.isCanceled
        ) {
            return@listener
        }

        val handler = TerminalListener.currentHandler ?: return@listener
        if (handler is MelodyTerminal || HoverTerm.isAutoTermEnabledFor(handler.autoTermSettingName())) {
            return@listener
        }

        val hoveredSlot = hoveredSlotField.get(TerminalSolver) as? Int ?: return@listener

        val click = handler.getClickForSlot(hoveredSlot) ?: return@listener
        val now = System.currentTimeMillis()
        val requiredDelay = if (handler is RubixTerminal && hoveredSlot == lastClickedSlot) {
            maxOf(HoverTerm.clickDelay.toLong(), RUBIX_SAME_SLOT_MIN_DELAY_MS)
        } else HoverTerm.clickDelay.toLong()
        if (now - lastClickTime < requiredDelay) return@listener
        if (TerminalListener.checkFcDelay()) return@listener

        handler.predict(click)
        click.send()
        lastClickTime = now
        lastClickedSlot = hoveredSlot
    }

    private fun Terminal.autoTermSettingName(): String = when (this) {
            is NumberTerminal -> "Numbers"
            is ColorsTerminal -> "Colors"
            is RubixTerminal -> "Rubix"
            is RedGreenTerminal -> "Red-Green"
            is StartWithTerminal -> "Start-With"
            is MelodyTerminal -> "Melody"
        }

    override fun start() {
        resetHoverState()
        renderListener.register()
    }

    override fun stop() {
        renderListener.unregister()
        resetHoverState()
    }

    private fun resetHoverState() {
        lastClickTime = 0L
        lastClickedSlot = null
    }
}

internal object NoammAutoTermGuard : AutoTermGuard {
    private const val AUTO_TERMINAL_CLASS =
        "com.github.noamm9.features.impl.floor7.terminals.AutoTerminal"

    private val autoTerminal: Feature? by lazy {
        runCatching {
            val clazz = Class.forName(AUTO_TERMINAL_CLASS)
            clazz.getField("INSTANCE").get(null) as? Feature
        }.getOrNull()
    }

    override fun isEnabledFor(settingName: String): Boolean {
        val feature = autoTerminal?.takeIf { it.enabled } ?: return false
        return feature.configSettings
            .firstOrNull { it.name == settingName }
            ?.value as? Boolean == true
    }
}
