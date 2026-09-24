package com.iq200.heigui.features.impl.floor7.hoverterm

import com.odtheking.odin.events.ScreenEvent
import com.odtheking.odin.events.core.EventBus
import com.odtheking.odin.events.core.EventPriority
import com.odtheking.odin.features.impl.boss.TerminalSolver
import com.odtheking.odin.features.impl.boss.termGUI.TermGui
import com.odtheking.odin.utils.skyblock.dungeon.terminals.TerminalTypes
import com.odtheking.odin.utils.skyblock.dungeon.terminals.TerminalUtils
import com.odtheking.odin.utils.skyblock.dungeon.terminals.terminalhandler.RubixHandler
import com.odtheking.odin.utils.skyblock.dungeon.terminals.terminalhandler.TerminalHandler
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal object OdinHoverTermCompat : HoverTermCompat {
    private const val RUBIX_SAME_SLOT_MIN_DELAY_MS = 50L

    private val customHoveredSlotField = TermGui::class.java.getDeclaredField("hoveredSlotIndex").apply {
        isAccessible = true
    }

    private val customGuis = mutableMapOf<TerminalTypes, TermGui>()
    private var lastClickTime = 0L
    private var lastClickedSlot: Int? = null

    init {
        EventBus.registerListener(
            javaClass,
            ScreenEvent.Render::class.java,
            EventPriority.LOWEST,
            false
        ) { event -> handleFrame(event) }
    }

    private fun handleFrame(event: ScreenEvent.Render) {
        if (HoverTerm.mode != "Odin" || !TerminalSolver.enabled) {
            return
        }

        val handler = TerminalUtils.currentTerm ?: return
        if (handler.type == TerminalTypes.MELODY) {
            return
        }
        if (HoverTerm.isAutoTermEnabledFor(handler.type.autoTermSettingName())) {
            return
        }

        val hoveredSlot = getHoveredSlot(event, handler) ?: return

        val button = getClickButton(handler, hoveredSlot) ?: return
        val now = System.currentTimeMillis()
        val requiredDelay = if (handler is RubixHandler && hoveredSlot == lastClickedSlot) {
            maxOf(HoverTerm.clickDelay.toLong(), RUBIX_SAME_SLOT_MIN_DELAY_MS)
        } else HoverTerm.clickDelay.toLong()
        if (now - lastClickTime < requiredDelay) return
        if (handler.shouldProtect()) return

        handler.click(hoveredSlot, button, true)
        lastClickTime = now
        lastClickedSlot = hoveredSlot
    }

    private fun getHoveredSlot(event: ScreenEvent.Render, handler: TerminalHandler): Int? {
        if (!TerminalSolver.customGuiEnabled) {
            val screen = event.screen as? AbstractContainerScreen<*> ?: return null
            val relativeMouseX = event.mouseX - screen.leftPos
            val relativeMouseY = event.mouseY - screen.topPos
            return screen.menu.slots.firstOrNull { slot ->
                slot.isActive &&
                        relativeMouseX >= slot.x && relativeMouseX < slot.x + 16 &&
                        relativeMouseY >= slot.y && relativeMouseY < slot.y + 16
            }?.index
        }

        val gui = customGuis.getOrPut(handler.type) {
            // Each TerminalTypes entry is an anonymous, package-private subclass.
            // Its getGUI method is public, but the declaring subclass is not, so
            // Java reflection still requires access override before invocation.
            val getGui = handler.type.javaClass.getMethod("getGUI").apply {
                isAccessible = true
            }
            getGui.invoke(handler.type) as TermGui
        }
        return customHoveredSlotField.get(gui) as? Int
    }

    private fun getClickButton(handler: TerminalHandler, slot: Int): Int? = when {
        handler.canClick(slot, 0) -> 0
        handler.canClick(slot, 1) -> 1
        else -> null
    }

    private fun TerminalTypes.autoTermSettingName(): String = when (this) {
        TerminalTypes.PANES -> "Red-Green"
        TerminalTypes.RUBIX -> "Rubix"
        TerminalTypes.NUMBERS -> "Numbers"
        TerminalTypes.STARTS_WITH -> "Start-With"
        TerminalTypes.SELECT -> "Colors"
        TerminalTypes.MELODY -> "Melody"
    }

    override fun start() {
        resetHoverState()
        EventBus.subscribe(this)
    }

    override fun stop() {
        EventBus.unsubscribe(this)
        resetHoverState()
    }

    private fun resetHoverState() {
        lastClickTime = 0L
        lastClickedSlot = null
    }
}
