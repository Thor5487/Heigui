package com.iq200.heigui.features.impl.dungeon

import com.iq200.heigui.Heigui
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.events.ChatPacketEvent
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.PlayerInputEvent
import com.iq200.heigui.events.ScreenEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.clickSlot
import com.iq200.heigui.utils.equalsOneOf
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.skyblock.dungeon.DungeonClass
import com.iq200.heigui.utils.skyblock.dungeon.DungeonListener
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import kotlinx.coroutines.delay as waitMillis
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import java.util.concurrent.ConcurrentLinkedQueue

object AutoLeap : Module(
    name = "Auto Leap",
    description = "Auto Leap on Blood Rush",
    category = Category.DUNGEON
) {
    private data class PendingLeapUse(
        val flowId: Int,
        val targetName: String,
        val fallbackToSpiritLeap: Boolean,
        var ticksRemaining: Int
    )

    private data class PendingContainerUpdate(
        val flowId: Int,
        val containerId: Int
    )

    private val CLASS_VALUES = listOf(
        DungeonClass.ARCHER,
        DungeonClass.BERSERK,
        DungeonClass.HEALER,
        DungeonClass.MAGE,
        DungeonClass.TANK
    )
    private val CLASS_OPTIONS = listOf("Archer", "Berserk", "Healer", "Mage", "Tank")

    private val targetClass by SelectorSetting(
        name = "Class",
        default = "Healer",
        options = CLASS_OPTIONS,
        desc = "The fake class to leap to when a Wither Key is obtained."
    )
    private val delay by NumberSetting(
        name = "Delay",
        default = 0,
        min = 0,
        max = 5,
        increment = 1,
        desc = "The delay between switching to a leap item and using it.",
        unit = "tick"
    )
    private val timeout by NumberSetting(
        name = "Timeout",
        default = 250,
        min = 50,
        max = 500,
        increment = 10,
        desc = "Milliseconds to wait for the leap menu before trying Spirit Leap or cancelling.",
        unit = "ms"
    )
    private var pendingTargetName: String? = null
    @Volatile
    private var flowId = 0
    private var leapMenuOpenedFlowId: Int? = null
    private var pendingLeapUse: PendingLeapUse? = null
    private val pendingContainerUpdates = ConcurrentLinkedQueue<PendingContainerUpdate>()
    private var permittedUseFlowId: Int? = null
    private var permittedUseConsumed = false

    init {
        on<ChatPacketEvent> {
            if (!value.contains(WITHER_KEY_MESSAGE, ignoreCase = true)) return@on
            startLeapFlow()
        }

        on<ScreenEvent.Open> {
            val leapScreen = screen as? AbstractContainerScreen<*> ?: return@on
            handleLeapMenuOpened(leapScreen)
        }

        on<ScreenEvent.Render>(priority = HIDE_MENU_PRIORITY) {
            if (pendingTargetName == null) return@on
            val leapScreen = screen as? AbstractContainerScreen<*> ?: return@on
            if (isLeapMenu(leapScreen)) cancel()
        }

        on<PacketEvent.Receive> {
            val containerId = when (val containerPacket = packet) {
                is ClientboundContainerSetContentPacket -> containerPacket.containerId
                is ClientboundContainerSetSlotPacket -> containerPacket.containerId
                else -> return@on
            }
            val packetFlowId = flowId
            pendingContainerUpdates.add(PendingContainerUpdate(packetFlowId, containerId))
        }

        on<TickEvent.End> {
            processContainerUpdates()
            processPendingLeapUse()
        }

        on<PlayerInputEvent.Use>(priority = USE_GUARD_PRIORITY) {
            val activeFlow = pendingTargetName != null
            val permitted = activeFlow && permittedUseFlowId == flowId && !permittedUseConsumed
            if (permitted) {
                permittedUseConsumed = true
            } else if (activeFlow) {
                cancel()
            }
        }

        on<WorldEvent.Load> {
            clearPendingFlow()
        }
    }

    override fun onDisable() {
        clearPendingFlow()
        super.onDisable()
    }

    private fun startLeapFlow() {
        if (!DungeonUtils.inDungeons || DungeonListener.dungeonTeammates.size <= 1) return

        val selectedClass = CLASS_VALUES.getOrNull(targetClass) ?: return
        val target = DungeonListener.dungeonTeammatesNoSelf
            .firstOrNull { LeapMenu.effectiveClass(it) == selectedClass }
        if (target == null) {
            modMessage("§cAuto Leap failed: no other $selectedClass player was found.")
            return
        }

        val currentFlowId = ++flowId
        pendingTargetName = target.name
        useLeapItem(
            itemNames = INFINILEAP_NAMES,
            targetName = target.name,
            currentFlowId = currentFlowId,
            fallbackToSpiritLeap = true
        )
    }

    private fun useLeapItem(
        itemNames: Array<String>,
        targetName: String,
        currentFlowId: Int,
        fallbackToSpiritLeap: Boolean
    ) {
        if (!isCurrentFlow(currentFlowId, targetName)) return

        val leapSlot = PlayerUtils.findItemInHotbar(*itemNames)
        if (leapSlot == null) {
            val itemName = if (fallbackToSpiritLeap) "Infinileap" else "Spirit Leap"
            modMessage("§cAuto Leap failed: $itemName was not found in the hotbar.")
            clearPendingFlow()
            return
        }

        PlayerUtils.setHotbarSlot(leapSlot)

        if (delay == 0) {
            performLeapUse(currentFlowId, targetName, fallbackToSpiritLeap)
        } else {
            pendingLeapUse = PendingLeapUse(currentFlowId, targetName, fallbackToSpiritLeap, delay)
        }
    }

    private fun performLeapUse(currentFlowId: Int, targetName: String, fallbackToSpiritLeap: Boolean) {
        if (!isCurrentFlow(currentFlowId, targetName)) return

        permittedUseFlowId = currentFlowId
        permittedUseConsumed = false
        PlayerUtils.rightClick()
        scheduleTimeout(timeout) {
            if (!isCurrentFlow(currentFlowId, targetName)) return@scheduleTimeout
            if (leapMenuOpenedFlowId == currentFlowId) return@scheduleTimeout

            if (fallbackToSpiritLeap) {
                modMessage("§eAuto Leap: Infinileap did not open the Leap Menu. Trying Spirit Leap.")
                useLeapItem(
                    itemNames = SPIRIT_LEAP_NAMES,
                    targetName = targetName,
                    currentFlowId = currentFlowId,
                    fallbackToSpiritLeap = false
                )
            } else {
                modMessage("§cAuto Leap: Spirit Leap did not open the Leap Menu. Flow cancelled.")
                clearPendingFlow()
            }
        }
    }

    private fun processPendingLeapUse() {
        val pending = pendingLeapUse ?: return
        if (!isCurrentFlow(pending.flowId, pending.targetName)) {
            pendingLeapUse = null
            return
        }
        if (--pending.ticksRemaining > 0) return

        pendingLeapUse = null
        performLeapUse(pending.flowId, pending.targetName, pending.fallbackToSpiritLeap)
    }

    private fun processContainerUpdates() {
        while (true) {
            val update = pendingContainerUpdates.poll() ?: return
            if (update.flowId != flowId || pendingTargetName == null) continue

            val leapScreen = mc.gui.screen() as? AbstractContainerScreen<*> ?: continue
            if (!isLeapMenu(leapScreen) || leapScreen.menu.containerId != update.containerId) continue
            trySelectTarget(leapScreen)
        }
    }

    private fun isCurrentFlow(currentFlowId: Int, targetName: String): Boolean =
        enabled && currentFlowId == flowId && pendingTargetName == targetName

    private fun handleLeapMenuOpened(screen: AbstractContainerScreen<*>) {
        val targetName = pendingTargetName ?: return
        if (!isLeapMenu(screen)) return

        val openedFlowId = flowId
        leapMenuOpenedFlowId = openedFlowId
        trySelectTarget(screen)

        scheduleTimeout(timeout) {
            if (!isCurrentFlow(openedFlowId, targetName)) return@scheduleTimeout
            if (leapMenuOpenedFlowId != openedFlowId) return@scheduleTimeout
            modMessage("§cAuto Leap failed: $targetName did not appear in the Leap Menu.")
            clearPendingFlow()
        }
    }

    private fun trySelectTarget(screen: AbstractContainerScreen<*>) {
        val targetName = pendingTargetName ?: return
        val leapSlot = screen.menu.slots
            .asSequence()
            .drop(LEAP_SLOT_START)
            .take(LEAP_SLOT_COUNT)
            .firstOrNull { slot ->
                slot.item.hoverName.string
                    .substringAfter(' ')
                    .noControlCodes
                    .equals(targetName, ignoreCase = true)
            } ?: return

        mc.player?.clickSlot(screen.menu.containerId, leapSlot.index)
        mc.player?.closeContainer()
        clearPendingFlow()
    }

    private fun isLeapMenu(screen: AbstractContainerScreen<*>): Boolean =
        screen.title.string.equalsOneOf("Spirit Leap", "Teleport to Player")

    private fun clearPendingFlow() {
        pendingTargetName = null
        leapMenuOpenedFlowId = null
        pendingLeapUse = null
        pendingContainerUpdates.clear()
        permittedUseFlowId = null
        permittedUseConsumed = false
        flowId++
    }

    private fun scheduleTimeout(milliseconds: Int, task: () -> Unit) {
        Heigui.scope.launch {
            waitMillis(milliseconds.toLong())
            mc.execute { task() }
        }
    }

    private const val WITHER_KEY_MESSAGE = "has obtained wither key!"
    private const val HIDE_MENU_PRIORITY = 1_000
    private const val USE_GUARD_PRIORITY = 10_000
    private const val LEAP_SLOT_START = 11
    private const val LEAP_SLOT_COUNT = 5
    private val INFINILEAP_NAMES = arrayOf("Infinileap", "Infini Leap")
    private val SPIRIT_LEAP_NAMES = arrayOf("Spirit Leap")
}
