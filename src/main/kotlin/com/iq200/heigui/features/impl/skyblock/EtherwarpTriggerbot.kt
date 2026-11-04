package com.iq200.heigui.features.impl.skyblock

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.clickgui.settings.AlwaysActive
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color.Companion.withAlpha
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.getTunerDistance
import com.iq200.heigui.utils.isEtherwarpItem
import com.iq200.heigui.utils.itemId
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.render.drawText
import com.iq200.heigui.utils.skyblock.EtherUtils
import com.iq200.heigui.utils.skyblock.Island
import com.iq200.heigui.utils.skyblock.LocationUtils
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

data class EtherwarpTriggerPos(
    val x: Int,
    val y: Int,
    val z: Int
) {
    constructor(pos: BlockPos) : this(pos.x, pos.y, pos.z)

    fun toBlockPos(): BlockPos = BlockPos(x, y, z)
}

data class EtherwarpTriggerData(
    val targets: MutableMap<String, MutableList<EtherwarpTriggerPos>> = linkedMapOf()
)

@AlwaysActive
object EtherwarpTriggerbot : Module(
    name = "Etherwarp Triggerbot",
    description = "Automatically Etherwarps when aiming at a saved target.",
    category = Category.SKYBLOCK
) {
    private const val BASE_ETHERWARP_DISTANCE = 57
    private const val AOTV_ID = "ASPECT_OF_THE_VOID"

    private val editColor by ColorSetting(
        "Edit Color",
        Colors.MINECRAFT_GREEN.withAlpha(0.8f),
        allowAlpha = true,
        desc = "Color used to highlight saved targets in edit mode."
    )

    private val config = JsonConfig(
        fileName = "etherwarp_triggerbot.json",
        typeToken = object : TypeToken<EtherwarpTriggerData>() {}.type,
        defaultData = { EtherwarpTriggerData() }
    )

    private var editMode = false
    private var triggeredTarget: BlockPos? = null

    init {
        config.load()

        on<InputEvent> {
            if (!editMode || !isPress || mc.gui.screen() != null) return@on

            val useKey = (mc.options.keyUse as KeyMappingAccessor).key
            if (key != useKey) return@on

            val hitResult = mc.hitResult as? BlockHitResult ?: return@on
            if (hitResult.type != HitResult.Type.BLOCK) return@on

            addTarget(LocationUtils.currentArea, hitResult.blockPos)
            cancel()
        }

        on<RenderEvent.Extract> {
            if (editMode) renderEditTargets(this)
            if (!enabled || editMode || mc.gui.screen() != null) {
                triggeredTarget = null
                return@on
            }

            val player = mc.player ?: return@on
            val stack = player.mainHandItem
            if (!player.isShiftKeyDown || stack.itemId != AOTV_ID || !stack.isEtherwarpItem()) {
                triggeredTarget = null
                return@on
            }

            val islandTargets = targetsFor(LocationUtils.currentArea)
            if (islandTargets.isEmpty()) {
                triggeredTarget = null
                return@on
            }

            val origin = TeleportOptimization.activeZpcmPosition ?: player.position()
            val eyePosition = origin.add(0.0, EtherUtils.getEyeHeight().toDouble(), 0.0)
            val (target, canEtherwarp) = EtherUtils.getEtherPosFromOrigin(
                eyePosition,
                player.yRot,
                player.xRot,
                BASE_ETHERWARP_DISTANCE + stack.getTunerDistance()
            )

            if (!canEtherwarp || target == null || islandTargets.none { it.toBlockPos() == target }) {
                triggeredTarget = null
                return@on
            }

            if (triggeredTarget == target) return@on
            triggeredTarget = target.immutable()
            PlayerUtils.rightClick()
        }

        on<WorldEvent.Load> {
            triggeredTarget = null
        }
    }

    override fun onEnable() {
        config.load()
        super.onEnable()
    }

    fun toggleEditMode() {
        editMode = !editMode
        triggeredTarget = null

        if (editMode) {
            modMessage("§aEtherwarp Triggerbot edit mode enabled. §7Right-click a block to add it to the current island.")
            modMessage("§7Run §e/hg et edit §7again to leave edit mode.")
        } else {
            modMessage("§cEtherwarp Triggerbot edit mode disabled.")
        }
    }

    fun listTargets(island: Island) {
        val targets = targetsFor(island)
        if (targets.isEmpty()) {
            modMessage("§eNo Etherwarp targets are saved for §6${island.displayName}§e.")
            return
        }

        modMessage("§aEtherwarp targets for §e${island.displayName} §8(§7${targets.size}§8):")
        targets.forEachIndexed { index, target ->
            modMessage("§8${index + 1}. §e${formatPos(target)}", prefix = "")
        }
    }

    fun removeTarget(island: Island, index: Int): EtherwarpTriggerPos? {
        val targets = targetsFor(island)
        if (index !in targets.indices) return null

        var removed: EtherwarpTriggerPos? = null
        config.update { data ->
            removed = data.targets[island.name]?.removeAt(index)
            if (data.targets[island.name].isNullOrEmpty()) data.targets.remove(island.name)
        }
        return removed
    }

    fun clearTargets(island: Island): Int {
        val count = targetsFor(island).size
        if (count == 0) return 0

        config.update { data -> data.targets.remove(island.name) }
        return count
    }

    private fun addTarget(island: Island, pos: BlockPos) {
        if (island == Island.Unknown) {
            modMessage("§cThe current island is unknown. Wait for the island to load before adding a target.")
            return
        }

        val savedPos = EtherwarpTriggerPos(pos)
        if (savedPos in targetsFor(island)) {
            modMessage("§eThat target is already saved for §6${island.displayName}§e.")
            return
        }

        config.update { data ->
            data.targets.getOrPut(island.name) { mutableListOf() }.add(savedPos)
        }
        modMessage("§aAdded Etherwarp target §e${formatPos(savedPos)} §afor §e${island.displayName}§a.")
    }

    private fun renderEditTargets(event: RenderEvent.Extract) {
        val targets = targetsFor(LocationUtils.currentArea)
        targets.forEachIndexed { index, savedPos ->
            val pos = savedPos.toBlockPos()
            event.drawStyledBox(AABB(pos), editColor, style = 2, depth = false)
            event.drawText(
                text = (index + 1).toString(),
                pos = Vec3.atCenterOf(pos).add(0.0, 0.75, 0.0),
                scale = 0.035f,
                depth = false
            )
        }
    }

    private fun targetsFor(island: Island): List<EtherwarpTriggerPos> =
        config.data.targets[island.name].orEmpty()

    private fun formatPos(pos: EtherwarpTriggerPos): String = "${pos.x}, ${pos.y}, ${pos.z}"
}
