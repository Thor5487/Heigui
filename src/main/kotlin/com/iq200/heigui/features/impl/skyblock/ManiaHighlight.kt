package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.render.drawStyledBox
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object ManiaHighlight : Module(
    name = "Mania Highlight",
    description = "Highlights Mania safe zones for Vampire Slayer",
    category = Category.SKYBLOCK
) {
    private val maniaHighlight by BooleanSetting("Mania Highlight", false, "Useful when encountering a griefer or wanting to be a griefer")

    private val MANIA_LAYERS = mapOf(
        1 to setOf(
            Pair(2, 1), Pair(2, 0),
            Pair(2, 2), Pair(3, 1), Pair(3, 0),
            Pair(3, 2), Pair(4, 0), Pair(4, 1), Pair(4, 2), Pair(3, 3)
        ),
        2 to setOf(
            Pair(5, 1), Pair(5, 0), Pair(4, 3), Pair(5, 2),
            Pair(6, 2), Pair(6, 1), Pair(6, 0), Pair(5, 3), Pair(5, 4), Pair(4, 4),
            Pair(7, 1), Pair(7, 2), Pair(7, 0), Pair(6, 3), Pair(5, 5), Pair(6, 4)
        ),
        3 to setOf(
            Pair(7, 3), Pair(8, 2), Pair(7, 4), Pair(8, 1), Pair(8, 0), Pair(6, 5), Pair(6, 6),
            Pair(9, 3), Pair(8, 3), Pair(9, 2), Pair(9, 1), Pair(7, 6), Pair(8, 5), Pair(7, 5), Pair(8, 4), Pair(9, 0),
            Pair(9, 4), Pair(7, 7)
        ),
        4 to setOf(
            Pair(10, 1), Pair(10, 2), Pair(10, 3), Pair(10, 0), Pair(8, 6), Pair(9, 5),
            Pair(11, 3), Pair(11, 2), Pair(10, 4), Pair(10, 5), Pair(8, 7), Pair(9, 7), Pair(9, 6), Pair(11, 1), Pair(11, 0), Pair(8, 8),
            Pair(12, 2), Pair(12, 3), Pair(11, 4), Pair(11, 5), Pair(10, 7), Pair(10, 6), Pair(9, 8), Pair(12, 1), Pair(12, 0),
            Pair(11, 6)
        )
    )

    // ✅ 修正 1：必須使用 Concurrent 集合以防止多執行緒讀寫崩潰
    private val maniaHighlightBlocks = ConcurrentHashMap.newKeySet<BlockPos>()
    private val greenBlocksBuffer = ConcurrentHashMap.newKeySet<BlockPos>()
    private var myActiveBoss: ArmorStand? = null

    init {
        on<PacketEvent.Receive> {
            val player = mc.player ?: return@on

            // ✅ 修正 2：收包時不要嚴格綁定 Boss (可能還沒重生完)，只要在玩家附近就先存進 Buffer
            if (packet is ClientboundBlockUpdatePacket) {
                val state = packet.blockState
                if (state.`is`(Blocks.GREEN_TERRACOTTA)) {
                    val pos = packet.pos
                    if (pos.closerToCenterThan(player.position(), 40.0)) {
                        greenBlocksBuffer.add(pos.immutable())
                    }
                }
            } else if (packet is ClientboundSectionBlocksUpdatePacket) {
                packet.runUpdates { pos, state ->
                    if (state.`is`(Blocks.GREEN_TERRACOTTA)) {
                        if (pos.closerToCenterThan(player.position(), 40.0)) {
                            greenBlocksBuffer.add(pos.immutable())
                        }
                    }
                }
            }
        }

        on<TickEvent.Start> {
            if (!enabled || mc.player == null || mc.level == null) {
                resetState()
                return@on
            }

            // === 1. 綁定當前 Boss 與 Mania 狀態 ===
            val playerName = mc.player!!.name.string.lowercase()
            myActiveBoss = null
            var foundMania = false

            mc.level!!.entitiesForRendering().forEach { entity ->
                if (entity is ArmorStand) {
                    val entityName = entity.name.string.noControlCodes.lowercase()
                    if (entityName.contains("spawned by") && entityName.contains(playerName)) {
                        myActiveBoss = entity
                    }
                }
            }

            if (myActiveBoss != null) {
                val currentBoss = myActiveBoss!!
                mc.level!!.entitiesForRendering().forEach { entity ->
                    if (entity is ArmorStand) {
                        val entityName = entity.name.string.noControlCodes.lowercase()
                        val isMyBossColumn = abs(entity.x - currentBoss.x) < 0.5 && abs(entity.z - currentBoss.z) < 0.5

                        if (isMyBossColumn && entityName.contains("mania") && entity.y >= currentBoss.y) {
                            foundMania = true
                        }
                    }
                }
            }

            // === 2. 嚴格過濾與結算 Buffer ===
            if (greenBlocksBuffer.isNotEmpty() && myActiveBoss != null) {
                val bossPos = myActiveBoss!!.blockPosition()
                val bossX = bossPos.x
                val bossZ = bossPos.z

                val bufferLayerHits = mutableMapOf<Int, Int>()
                val validBlocks = mutableListOf<BlockPos>()

                for (pos in greenBlocksBuffer) {
                    val dx = abs(pos.x - bossX)
                    val dz = abs(pos.z - bossZ)
                    val dy = abs(pos.y - bossPos.y)

                    // 在這裡才做嚴格的物理過濾
                    if (dx <= 15 && dz <= 15 && dy <= 5) {
                        val u = kotlin.math.max(dx, dz)
                        val v = kotlin.math.min(dx, dz)
                        val uvPair = Pair(u, v)

                        // 尋找這個座標屬於哪個 Layer
                        val matchedLayers = MANIA_LAYERS.filterValues { it.contains(uvPair) }.keys
                        if (matchedLayers.isNotEmpty()) {
                            validBlocks.add(pos)
                            for (layer in matchedLayers) {
                                bufferLayerHits[layer] = bufferLayerHits.getOrDefault(layer, 0) + 1
                            }
                        }
                    }
                }

                // 取票數最高的 Layer (防止路人亂放方塊干擾)
                if (bufferLayerHits.isNotEmpty()) {
                    val targetLayer = bufferLayerHits.maxByOrNull { it.value }?.key

                    if (targetLayer != null) {
                        // 利用確實吻合的方塊來做 Y 軸眾數統計，精準找出地板高度
                        val floorY = validBlocks.groupingBy { it.y }.eachCount().maxByOrNull { it.value }?.key ?: (bossPos.y - 1)

                        maniaHighlightBlocks.clear()
                        val layerCoords = MANIA_LAYERS[targetLayer]!!

                        // 展開 8 方位對稱座標，一波還原整個安全區！
                        for ((u, v) in layerCoords) {
                            val symmetries = setOf(
                                Pair(u, v), Pair(u, -v), Pair(-u, v), Pair(-u, -v),
                                Pair(v, u), Pair(v, -u), Pair(-v, u), Pair(-v, -u)
                            )
                            for ((ox, oz) in symmetries) {
                                maniaHighlightBlocks.add(BlockPos(bossX + ox, floorY, bossZ + oz))
                            }
                        }
                    }
                }

                greenBlocksBuffer.clear()
            }

            // === 3. Mania 結束時清空 Highlight 清單 ===
            if (!foundMania) {
                maniaHighlightBlocks.clear()
            }
        }

        on<RenderEvent.Extract> {
            if (!enabled || mc.player == null || mc.level == null) return@on

            if (maniaHighlight && maniaHighlightBlocks.isNotEmpty()) {
                for (pos in maniaHighlightBlocks) {
                    val box = AABB(
                        pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                        pos.x.toDouble() + 1.0, pos.y.toDouble() + 1.0, pos.z.toDouble() + 1.0
                    )
                    drawStyledBox(
                        aabb = box,
                        color = Color(0, 255, 0, 120f),
                        style = 0,
                        depth = true
                    )
                }
            }
        }
    }

    override fun onDisable() {
        resetState()
    }

    private fun resetState() {
        greenBlocksBuffer.clear()
        maniaHighlightBlocks.clear()
        myActiveBoss = null
    }
}