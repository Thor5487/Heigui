package com.iq200.heigui.features.impl.dungeon

import com.google.gson.reflect.TypeToken
import com.iq200.heigui.clickgui.settings.AlwaysActive
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.config.JsonConfig
import com.iq200.heigui.events.BlockBreakEvent
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

data class BlockWrongBreakPos(
    val x: Int,
    val y: Int,
    val z: Int
) {
    constructor(pos: BlockPos) : this(pos.x, pos.y, pos.z)

    fun toBlockPos(): BlockPos = BlockPos(x, y, z)
}

data class BlockWrongBreakData(
    val blocks: MutableSet<BlockWrongBreakPos> = linkedSetOf()
)

@AlwaysActive
object BlockWrongBreak : Module(
    name = "Block Wrong Break",
    description = "Prevents Dungeon Breaker from breaking configured blocks",
    category = Category.DUNGEON
) {
    private val highlightColor by ColorSetting(
        "Highlight Color",
        Colors.MINECRAFT_RED,
        allowAlpha = true,
        desc = "Color used to highlight protected blocks while editing"
    )

    private val config = JsonConfig(
        fileName = "block_wrong_break.json",
        typeToken = object : TypeToken<BlockWrongBreakData>() {}.type,
        defaultData = { BlockWrongBreakData() },
        legacyFileNames = listOf("block-wrong-break.json")
    )

    private var editMode = false

    init {
        config.load()

        on<BlockBreakEvent> {
            if (enabled && shouldBlockBreak(pos)) cancel()
        }

        on<PacketEvent.Send> {
            if (!enabled) return@on

            val actionPacket = packet as? ServerboundPlayerActionPacket ?: return@on
            if (actionPacket.action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return@on
            if (shouldBlockBreak(actionPacket.pos)) cancel()
        }

        on<InputEvent> {
            if (!editMode || !isPress || mc.gui.screen() != null) return@on

            val useKey = (mc.options.keyUse as KeyMappingAccessor).key
            if (key != useKey) return@on

            val hitResult = mc.hitResult as? BlockHitResult ?: return@on
            if (hitResult.type != HitResult.Type.BLOCK) return@on

            toggleBlock(hitResult.blockPos)
            cancel()
        }

        on<RenderEvent.Extract> {
            if (!editMode) return@on

            for (savedPos in config.data.blocks) {
                drawStyledBox(
                    aabb = AABB(savedPos.toBlockPos()),
                    color = highlightColor,
                    style = 2,
                    depth = false
                )
            }
        }
    }

    override fun onEnable() {
        config.load()
        super.onEnable()
    }

    fun toggleEditMode() {
        editMode = !editMode

        if (editMode) {
            modMessage("§aBlock Wrong Break edit mode enabled. §7Right-click a block to add or remove it.")
            modMessage("§7Run §e/hg bwb edit §7again to leave edit mode.")
        } else {
            modMessage("§cBlock Wrong Break edit mode disabled.")
        }
    }

    fun listBlocks() {
        val blocks = config.data.blocks.sortedWith(
            compareBy<BlockWrongBreakPos> { it.x }
                .thenBy { it.y }
                .thenBy { it.z }
        )

        if (blocks.isEmpty()) {
            modMessage("§eBlock Wrong Break list is empty.")
            return
        }

        modMessage("§aBlock Wrong Break protected blocks §8(§7${blocks.size}§8):")
        for (pos in blocks) {
            modMessage("§8- §e${formatPos(pos)}", prefix = "")
        }
    }

    fun clearBlocks() {
        val blockCount = config.data.blocks.size
        if (blockCount == 0) {
            modMessage("§eBlock Wrong Break list is already empty.")
            return
        }

        config.update { data -> data.blocks.clear() }
        modMessage("§aCleared §e$blockCount §aprotected block coordinate${if (blockCount == 1) "" else "s"}.")
    }

    private fun toggleBlock(pos: BlockPos) {
        val savedPos = BlockWrongBreakPos(pos)

        config.update { data ->
            if (data.blocks.remove(savedPos)) {
                modMessage("§cRemoved protected block at §e${formatPos(savedPos)}§c.")
            } else {
                data.blocks.add(savedPos)
                modMessage("§aAdded protected block at §e${formatPos(savedPos)}§a.")
            }
        }
    }

    private fun shouldBlockBreak(pos: BlockPos): Boolean {
        if (BlockWrongBreakPos(pos) !in config.data.blocks) return false

        val itemName = mc.player?.mainHandItem?.hoverName?.string ?: return false
        return itemName.filterNot(Char::isWhitespace).contains("dungeonbreaker", ignoreCase = true)
    }

    private fun formatPos(pos: BlockWrongBreakPos): String = "${pos.x}, ${pos.y}, ${pos.z}"
}
