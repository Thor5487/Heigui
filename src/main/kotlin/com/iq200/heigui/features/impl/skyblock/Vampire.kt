package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.ColorSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.config.BuildConfig
import com.iq200.heigui.events.HudRenderEvent
import com.iq200.heigui.events.InputEvent
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.PlayerInputEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.Color
import com.iq200.heigui.utils.Colors
import com.iq200.heigui.utils.PlayerUtils
import com.iq200.heigui.utils.camera.ClientRotationHandler
import com.iq200.heigui.utils.camera.ClientRotationProvider
import com.iq200.heigui.utils.handlers.schedule
import com.iq200.heigui.utils.modMessage
import com.iq200.heigui.utils.noControlCodes
import com.iq200.heigui.utils.render.drawStyledBox
import com.iq200.heigui.utils.render.drawTracer
import com.iq200.heigui.utils.render.textDim
import com.iq200.heigui.utils.texture
import com.iq200.mixin.accessors.KeyMappingAccessor
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import org.joml.Vector2f
import java.lang.Math.toRadians
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Vampire : Module(
    name = "Vampire",
    description = "Useful QOL for Vampire Slayer",
    category = Category.SKYBLOCK
), ClientRotationProvider {
    private val autoImpel by BooleanSetting("Auto Impel", false, "Auto Do Impel")
    private val rotationSpeed by NumberSetting("Speed", 40, 0, 100, 10,
        unit = "°/t", desc = "Rotation Speed For Click Up/Down").withDependency { autoImpel }
    private  val IMPEL_TIMEOUT_MS by NumberSetting("Impel Timeout", 200, 100, 500, 50,
        unit = "ms", desc = "Timeout For Impel To Be Considered Finished").withDependency { autoImpel }
    private val autoIce by BooleanSetting("Auto Ice", false, "Auto use Holy Ice on Twinclaws")
    private val autoMelon by BooleanSetting("Auto Melon", false, "Auto use Healing Melon on low HP")
    private val melonHealth by NumberSetting("Melon Health", 12, 4, 26, 1,
        desc = "Health threshold for Auto Melon").withDependency { autoMelon }
    private val ichorEsp by BooleanSetting("Ichor ESP", false, "Highlight Blood Ichor")
    private val ichorTracer by BooleanSetting("Ichor Tracer", false, "Draw line to Blood Ichor").withDependency { ichorEsp }
    private val autoKillerSpring by BooleanSetting("Auto Killer Spring", false, "Auto left/right click when aiming at Killer Spring")
    private val killerSpringTracer by BooleanSetting("Killer Spring Tracer", false, "Draw a line to  Killer Spring")
    private val bossEsp by BooleanSetting("Boss ESP", false, "Highlight your boss with a solid box")
    private val bossArrow by BooleanSetting("Boss Arrow", false, "Draw an arrow pointing to the boss")
    private val arrowRadius by NumberSetting("Arrow Radius", 50, 10, 100, 10, desc = "Radius of the arrow circle").withDependency { bossArrow }
    private val arrowScale by NumberSetting("Arrow Size", 2f, 1f, 3f, 0.1f, desc = "Size of Arrow").withDependency { bossArrow }
    private val arrowColor by ColorSetting("Arrow Color", Colors.MINECRAFT_AQUA, desc = "Arrow Color").withDependency { bossArrow }
    private val testArrow by BooleanSetting("Test Arrow", false, "Show a rotating test arrow (Turn off in real game)").withDependency { bossArrow }
    private val maniaHud by HUD("Mania Hud", "Display Mania Time") { example ->
        if (example) {
            return@HUD textDim("§5Mania §b2.5s", 0, 0, Colors.WHITE)
        }
        else if (currentManiaTime != null){
            return@HUD textDim("§5Mania §b${currentManiaTime}s", 0, 0, Colors.WHITE)
        }

        0 to 0
    }
    private val maniaHighlight by BooleanSetting("Mania Highlight", false, "Useful when encountering a griefer or wanting to be a griefer")
    private val autoTuba by BooleanSetting("Auto Tuba", false, "Auto Use Tuba at The End of Mania")
    private val autoSteak by BooleanSetting("Auto Steak", false, "Auto swap to Steak when boss HP <= 20%")

    private const val BLOOD_ICHOR_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTYxNTg4ODAwMDU1MywKICAicHJvZmlsZUlkIiA6ICI5ZDIyZGRhOTVmZGI0MjFmOGZhNjAzNTI1YThkZmE4ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJTYWZlRHJpZnQ0OCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jMDM0MDkyM2E2ZGU0ODI1YTE3NjgxM2QxMzM1MDNlZmYxODZkYjA4OTZlMzJiNjcwNDkyOGMyYTJiZjY4NDIyIgogICAgfQogIH0KfQ=="
    private const val KILLER_SPRING_B64 = "ewogICJ0aW1lc3RhbXAiIDogMTcxOTU5NDQxNjY5NywKICAicHJvZmlsZUlkIiA6ICJjY2MxNGM2ZDUwMDE0MjBmYmMxYjkyMTM2Y2JmOWU4MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJXaGlybGluZ0F0b2w5NDQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzdmN2E3YmM4YWM4NmYyM2NhN2JmOThhZmViNzY5NjAyMjdlMTgzMmZlMjA5YTMwMjZmNmNlYjhiZGU3NGY1NCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"
    private const val BOSS_SKIN_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTY2MTQwMDI0MTc2NiwKICAicHJvZmlsZUlkIiA6ICI4YTg3NGJhNmFiZDM0ZTc5OTljOWM1ODMwYWYyY2NmNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZXphMTExIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVhYTI5ZWE5NjE3NTdkYzNjOTBiZmFiZjMwMmM1YWJlOWQzMDhmYjRhN2QzODY0ZTU3ODhhZDJjYzkxNjBhYTIiCiAgICB9CiAgfQp9"
    private val ARROW_TEXTURE = Identifier.parse("heigui:textures/gui/arrow.png")


    enum class ImpelState {
        IDLE, ACTIVE_ROTATING, ACTIVE_CLICKING, ACTIVE_HOLDING, RETURNING, SYNCING
    }

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
    private val maniaHighlightBlocks = ConcurrentHashMap.newKeySet<BlockPos>()
    private var currentRenderedLayer: Int? = null
    private var announceSkin: Boolean = false


    private val trackedIchorIds = ConcurrentHashMap.newKeySet<Int>()
    private val preExistingIchorIds = ConcurrentHashMap.newKeySet<Int>()
    private var isWaitingForIchorSpawn = false
    private var ichorGraceTicks = 0

    private val trackedKillerSpringIds = ConcurrentHashMap.newKeySet<Int>()
    private val knownKillerSpringIds = ConcurrentHashMap.newKeySet<Int>()

    private var currentState: ImpelState = ImpelState.IDLE
    private var isDecoupled = false

    private var lastImpelTitleTime = 0L
    private var activeImpelType = ""
    private var clickCooldown = 0
    private var actionTick = 0

    private var isUsingItem = false
    private var lastMelonTime = 0L
    private const val MELON_COOLDOWN_MS = 1000L
    private var pendingIce = false

    private var hasSwappedForCurrentTwinclaws = false
    private var myActiveBoss: ArmorStand? = null
    private var myActiveRealBoss: Player? = null

    private var currentManiaTime: Float? = null

    private var currentBossId: Int? = null
    private var hasUsedTubaForBossSpawn = false
    private var hasUsedTubaForCurrentMania = false

    private var maxBossHealth: Float? = null
    private var currentBossHealth: Float? = null

    private var hasSwappedToSteakForCurrentBoss = false

    private var clickTickTimer = 0

    var isHandlingKillerSpring = false
    private var isHoldingAttack = false

    private val greenBlocksBuffer = ConcurrentHashMap.newKeySet<BlockPos>()

    override fun isClientRotationActive() = isDecoupled
    override fun allowClientKeyInputs() = true
    override fun shouldAdjustMovement() = true

    init {
        on<InputEvent> {
            if (!enabled || mc.player == null) return@on

            val attackKey = (mc.options.keyAttack as KeyMappingAccessor).key

            if (key == attackKey) {
                if (isPress) {
                    isHoldingAttack = true
                } else if (isRelease) {
                    isHoldingAttack = false
                }
            }
        }

        on<PacketEvent.Receive> {
            val myBoss = myActiveBoss ?: return@on
            if (packet is ClientboundBlockUpdatePacket) {
                val state = packet.blockState
                if (state.`is`(Blocks.DYED_TERRACOTTA.green())) {
                    val pos = packet.pos

                    if (mc.player != null && pos.closerToCenterThan(myBoss.position(), 30.0)) {
                        greenBlocksBuffer.add(pos.immutable())
                    }
                }
            }
            else if (packet is ClientboundSectionBlocksUpdatePacket) {
                packet.runUpdates { pos, state ->
                    if (state.`is`(Blocks.DYED_TERRACOTTA.green())) {
                        if (mc.player != null && pos.closerToCenterThan(myBoss.position(), 30.0)) {
                            greenBlocksBuffer.add(pos.immutable())
                        }
                    }
                }
            }
        }

        on<PacketEvent.Receive> {
            val text = when (packet) {
                is ClientboundSetTitleTextPacket -> packet.text.string.noControlCodes.lowercase()
                is ClientboundSetSubtitleTextPacket -> packet.text.string.noControlCodes.lowercase()
                else -> return@on
            }


            if (autoImpel){
                if (text.contains("impel:")) {
                    val currentType = when {
                        text.contains("click up") -> "click up"
                        text.contains("click down") -> "click down"
                        text.contains("sneak") -> "sneak"
                        text.contains("jump") -> "jump"
                        else -> return@on
                    }

                    lastImpelTitleTime = System.currentTimeMillis()
                    if (activeImpelType != currentType || currentState == ImpelState.IDLE) {
                        activeImpelType = currentType
                        when (currentType) {
                            "click up" -> startImpelRotation(-90f)
                            "click down" -> startImpelRotation(90f)
                            "sneak" -> {
                                currentState = ImpelState.ACTIVE_HOLDING
                                actionTick = 0
                            }
                            "jump" -> {
                                currentState = ImpelState.ACTIVE_HOLDING
                                actionTick = 0
                            }
                        }
                    }
                }
                else {
                    if (currentState == ImpelState.ACTIVE_ROTATING ||
                        currentState == ImpelState.ACTIVE_CLICKING ||
                        currentState == ImpelState.ACTIVE_HOLDING) {

                        lastImpelTitleTime = 0L
                    }

                }
            }
        }

        on<TickEvent.Start> {
            if (!enabled || mc.player == null || mc.level == null) {
                resetAllStates()
                return@on
            }


            if (autoKillerSpring && !isUsingItem && currentState == ImpelState.IDLE) {
                if (isHoldingAttack) {
                    val hit = mc.hitResult
                    if (hit is EntityHitResult) {
                        val targetEntity = hit.entity


                        if (targetEntity.isKillerSpring() && mc.player!!.distanceTo(targetEntity) < 5.0) {
                            isHandlingKillerSpring = true
                            clickTickTimer++

                            if (clickTickTimer >= 2) {
                                PlayerUtils.leftClick()
                                PlayerUtils.rightClick()


                                clickTickTimer = 0
                            }
                        } else {
                            isHandlingKillerSpring = false
                            resetClickState()
                        }
                    } else {
                        isHandlingKillerSpring = false
                        resetClickState()
                    }
                }

            } else {
                isHandlingKillerSpring = false
                resetClickState()
            }

            val playerName = mc.player!!.name.string.lowercase()
            myActiveBoss = null
            var anyBossNearby = false

            mc.level!!.entitiesForRendering().forEach { entity ->
                if (entity is ArmorStand) {
                    val entityName = entity.name.string.noControlCodes.lowercase()


                    if (entityName.contains("spawned by")) {
                        anyBossNearby = true


                        if (entityName.contains(playerName)) {
                            myActiveBoss = entity

                            if (currentBossId != entity.id) {
                                currentBossId = entity.id
                                hasUsedTubaForBossSpawn = false

                                maxBossHealth = null
                                currentBossHealth = null
                                hasSwappedToSteakForCurrentBoss = false
                            }
                        }
                    }
                }
            }

            myActiveRealBoss = null
            if (myActiveBoss != null) {
                val textStand = myActiveBoss!!
                var closestDistSqr = 25.0

                mc.level!!.entitiesForRendering().forEach { entity ->
                    if (entity is Player && entity != mc.player) {
                        val distSqr = entity.distanceToSqr(textStand)
                        if (distSqr < closestDistSqr) {
                            val profile = entity.gameProfile
                            val textureProperty = profile.properties.get("textures").firstOrNull()
                            val skinBase64 = textureProperty?.value ?: ""

                            if (skinBase64 == BOSS_SKIN_TEXTURE) {
                                closestDistSqr = distSqr
                                myActiveRealBoss = entity
                            }
                        }
                    }
                }
            }

            val currentSpringsInWorld = mutableSetOf<Int>()
            mc.level!!.entitiesForRendering().forEach { entity ->
                if (entity.isKillerSpring()) {
                    currentSpringsInWorld.add(entity.id)


                    if (!knownKillerSpringIds.contains(entity.id)) {
                        knownKillerSpringIds.add(entity.id)


                        if (myActiveBoss != null && myActiveBoss!!.distanceToSqr(entity) <= 25.0) {
                            trackedKillerSpringIds.add(entity.id)
                        }
                    }
                }
            }


            knownKillerSpringIds.retainAll(currentSpringsInWorld)
            trackedKillerSpringIds.retainAll(currentSpringsInWorld)


            var foundMania = false
            var seeingIchorTextThisTick = false

            if (myActiveBoss != null) {
                val currentBoss = myActiveBoss!!

                mc.level!!.entitiesForRendering().forEach { entity ->
                    if (entity is ArmorStand) {
                        val entityName = entity.name.string.noControlCodes.lowercase()


                        val isMyBossColumn = abs(entity.x - currentBoss.x) < 0.5 &&
                                abs(entity.z - currentBoss.z) < 0.5 && abs(entity.y - currentBoss.y) < 3

                        if (isMyBossColumn) {


                            if (entityName.contains("mania") && entity.y >= currentBoss.y) {
                                val match = Regex("mania\\s*([0-9.]+)s").find(entityName)
                                val timeString = match?.groupValues?.get(1)
                                if (timeString != null) {
                                    currentManiaTime = timeString.toFloatOrNull()
                                    foundMania = true
                                }
                            }


                            if (entityName.contains("❤") && entityName.contains("bloodfiend")) {
                                val healthMatch = Regex("([0-9,]+)\\s*❤").find(entityName)
                                val healthStr = healthMatch?.groupValues?.get(1)?.replace(",", "")

                                if (healthStr != null) {
                                    val hp = healthStr.toFloatOrNull()
                                    if (hp != null) {
                                        currentBossHealth = hp


                                        if (maxBossHealth == null || hp > maxBossHealth!!) {
                                            maxBossHealth = hp
                                        }
                                    }
                                }
                            }

                            if (entityName.contains("ichor")) {
                                val match = Regex("ichor\\s*([0-9.]+)s").find(entityName)
                                val countdown = match?.groupValues?.get(1)?.toFloatOrNull()
                                if (countdown != null) {
                                    seeingIchorTextThisTick = true

                                    if (countdown <= 0.5f) {
                                        isWaitingForIchorSpawn = true
                                        ichorGraceTicks = 10

                                        mc.level!!.entitiesForRendering().forEach { oldEntity ->
                                            if (oldEntity is ArmorStand && isBloodIchor(oldEntity)) {
                                                preExistingIchorIds.add(oldEntity.id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (greenBlocksBuffer.isNotEmpty()) {

                    val allBosses = mutableListOf<ArmorStand>()
                    mc.level!!.entitiesForRendering().forEach { entity ->
                        if (entity is ArmorStand) {
                            val entityName = entity.name.string.noControlCodes.lowercase()
                            if (entityName.contains("spawned by")) {
                                allBosses.add(entity)
                            }
                        }
                    }

                    var highestScore = 0
                    var winnerBossId: Int? = null
                    var winnerBestLayer: Int? = null
                    var winnerEvidenceBlocks = setOf<BlockPos>()


                    for (boss in allBosses) {
                        val bossPos = boss.blockPosition()
                        val bossX = bossPos.x
                        val bossY = bossPos.y
                        val bossZ = bossPos.z


                        val layerEvidenceMap = mutableMapOf<Int, MutableSet<BlockPos>>()

                        for (pos in greenBlocksBuffer) {
                            val dx = abs(pos.x - bossX)
                            val dz = abs(pos.z - bossZ)
                            val dy = pos.y - bossY


                            if (dx <= 15 && dz <= 15 && dy in -5..-2) {
                                val u = kotlin.math.max(dx, dz)
                                val v = kotlin.math.min(dx, dz)
                                val uvPair = Pair(u, v)


                                for ((layer, coords) in MANIA_LAYERS) {
                                    if (coords.contains(uvPair)) {
                                        layerEvidenceMap.computeIfAbsent(layer) { mutableSetOf() }.add(pos)
                                    }
                                }
                            }
                        }


                        for ((layer, blocks) in layerEvidenceMap) {
                            val currentScore = blocks.size
                            if (currentScore > highestScore) {
                                highestScore = currentScore
                                winnerBossId = boss.id
                                winnerBestLayer = layer
                                winnerEvidenceBlocks = blocks
                            }
                        }
                    }


                    if (winnerBossId != null && myActiveBoss != null && winnerBossId == myActiveBoss!!.id) {

                        if (currentRenderedLayer != winnerBestLayer) {
                            maniaHighlightBlocks.clear()
                            currentRenderedLayer = winnerBestLayer
                        }

                        maniaHighlightBlocks.addAll(winnerEvidenceBlocks)
                    }


                    greenBlocksBuffer.clear()
                }

                if (isWaitingForIchorSpawn && !seeingIchorTextThisTick) {
                    isWaitingForIchorSpawn = false
                }


                if (ichorGraceTicks > 0 && !seeingIchorTextThisTick) {
                    mc.level!!.entitiesForRendering().forEach { entity ->
                        if (entity is ArmorStand && isBloodIchor(entity)) {
                            val dx = abs(entity.x - currentBoss.x)
                            val dz = abs(entity.z - currentBoss.z)
                            if (dx * dx + dz * dz <= 400 && !preExistingIchorIds.contains(entity.id)) {
                                trackedIchorIds.add(entity.id)
                            }
                        }
                    }

                    ichorGraceTicks--

                    if (ichorGraceTicks <= 0) {
                        preExistingIchorIds.clear()
                    }
                }


            } else {
                trackedIchorIds.clear()
                isWaitingForIchorSpawn = false
                preExistingIchorIds.clear()
                ichorGraceTicks = 0

                trackedKillerSpringIds.clear()
            }

            if (!foundMania) {
                currentManiaTime = null
                hasUsedTubaForCurrentMania = false
                maniaHighlightBlocks.clear()
                currentRenderedLayer = null
            }

            if (autoIce) {
                var myTwinclawsStand: ArmorStand? = null


                if (myActiveBoss != null) {
                    mc.level!!.entitiesForRendering().forEach { entity ->
                        if (entity is ArmorStand) {
                            val entityName = entity.name.string.noControlCodes.lowercase()

                            if (entityName.contains("twinclaws") && entity.distanceTo(myActiveBoss!!) < 3.0f) {
                                myTwinclawsStand = entity
                            }
                        }
                    }
                }

                if (myTwinclawsStand != null) {
                    val name = myTwinclawsStand.name.string.noControlCodes.lowercase()
                    val match = Regex("twinclaws\\s*([0-9.]+)?s").find(name)
                    val timeRemaining = match?.groupValues?.get(1)?.toFloatOrNull() ?: 99.0f

                    if (timeRemaining <= 0.5f) {
                        if (!hasSwappedForCurrentTwinclaws) {
                            pendingIce = true
                            hasSwappedForCurrentTwinclaws = true
                        }
                    }
                } else {
                    hasSwappedForCurrentTwinclaws = false
                }
            }

            if (!isUsingItem) {

                if (pendingIce) {
                    if (triggerAutoItem("holy ice")) {
                        pendingIce = false
                    }
                }

                else if (autoMelon && anyBossNearby && mc.player!!.health <= melonHealth.toFloat() && (System.currentTimeMillis() - lastMelonTime > MELON_COOLDOWN_MS)) {
                    if (triggerAutoItem("healing melon")) {
                        lastMelonTime = System.currentTimeMillis()
                    }
                }
                else if (autoSteak && !hasSwappedToSteakForCurrentBoss && maxBossHealth != null && currentBossHealth != null && currentBossHealth!! <= (maxBossHealth!! * 0.2f)) {
                    val twentyPercent = maxBossHealth!! * 0.2f
                    if (currentBossHealth!! <= twentyPercent) {
                        val steakSlot = PlayerUtils.findItemInHotbar("steak")
                        if (steakSlot != null) {
                            PlayerUtils.setHotbarSlot(steakSlot)
                            hasSwappedToSteakForCurrentBoss = true
                        }
                    }
                }
                else if (autoTuba && myActiveBoss != null && !hasUsedTubaForBossSpawn) {
                    if (triggerAutoItem("tuba")) {
                        hasUsedTubaForBossSpawn = true
                    }
                }

                else if (autoTuba && currentManiaTime != null && currentManiaTime!! <= 1.0f && !hasUsedTubaForCurrentMania) {
                    if (triggerAutoItem("tuba")) {
                        hasUsedTubaForCurrentMania = true
                    }
                }

            } else {
                while (mc.options.keyAttack.consumeClick()) { }
                mc.options.keyAttack.isDown = false
            }

            val isTitleActive = (System.currentTimeMillis() - lastImpelTitleTime) < IMPEL_TIMEOUT_MS

            if (!isTitleActive && (currentState == ImpelState.ACTIVE_ROTATING || currentState == ImpelState.ACTIVE_CLICKING || currentState == ImpelState.ACTIVE_HOLDING)) {
                activeImpelType = ""
                mc.options.keyShift.isDown = false
                mc.options.keyJump.isDown = false

                if (currentState == ImpelState.ACTIVE_ROTATING || currentState == ImpelState.ACTIVE_CLICKING) {

                    PlayerUtils.smoothRotate(
                        yaw = null,
                        pitch = null,
                        speed = rotationSpeed.toFloat(),
                        targetProvider = { ClientRotationHandler.clientYaw to ClientRotationHandler.clientPitch }
                    )
                    currentState = ImpelState.RETURNING
                } else {
                    currentState = ImpelState.IDLE
                }
            }

            when (currentState) {
                ImpelState.ACTIVE_ROTATING -> {
                    if (!PlayerUtils.isRotating) {
                        currentState = ImpelState.ACTIVE_CLICKING
                        clickCooldown = 0
                    }
                }
                ImpelState.ACTIVE_CLICKING -> {
                    mc.player!!.yRot = ClientRotationHandler.clientYaw
                    mc.player!!.yRotO = ClientRotationHandler.clientYaw


                    if (!isUsingItem) {
                        if (clickCooldown <= 0) {
                            performClick()
                            clickCooldown = 1
                        } else {
                            clickCooldown--
                        }
                    }
                }
                ImpelState.ACTIVE_HOLDING -> {
                    actionTick++

                    if (activeImpelType == "sneak") {
                        if (actionTick <= 2) {

                            mc.options.keyShift.isDown = false
                        } else if (actionTick <= 5) {

                            mc.options.keyShift.isDown = true
                        } else {
                            mc.options.keyShift.isDown = false
                            currentState = ImpelState.IDLE
                            activeImpelType = ""
                        }
                    }
                    else if (activeImpelType == "jump") {
                        if (!mc.player!!.onGround()) {

                            mc.options.keyJump.isDown = false
                            currentState = ImpelState.IDLE
                            activeImpelType = ""
                        } else {

                            if (actionTick <= 3) {
                                mc.options.keyJump.isDown = true
                            } else {
                                mc.options.keyJump.isDown = false
                                currentState = ImpelState.IDLE
                                activeImpelType = ""
                            }
                        }
                    }
                }
                ImpelState.RETURNING -> {
                    if (!PlayerUtils.isRotating) {
                        currentState = ImpelState.SYNCING
                    }
                }
                ImpelState.SYNCING -> {
                    val player = mc.player!!
                    player.yRot = ClientRotationHandler.clientYaw
                    player.xRot = ClientRotationHandler.clientPitch
                    player.yRotO = ClientRotationHandler.clientYaw
                    player.xRotO = ClientRotationHandler.clientPitch

                    isDecoupled = false
                    currentState = ImpelState.IDLE
                }
                ImpelState.IDLE -> { /* Do nothing */ }

            }
        }

        on<RenderEvent.Extract> {
            if (!enabled || mc.player == null || mc.level == null) return@on

            if (isUsingItem) {
                mc.options.keyAttack.isDown = false
                while (mc.options.keyAttack.consumeClick()) { }
            }

            val pt = context.gameRenderer().mainCamera().getCameraEntityPartialTicks(mc.deltaTracker)

            if (bossEsp && myActiveRealBoss != null) {
                val realBoss = myActiveRealBoss!!


                val lerpedPos = realBoss.getPosition(pt)
                val aabb = realBoss.boundingBox.move(
                    lerpedPos.x - realBoss.x,
                    lerpedPos.y - realBoss.y,
                    lerpedPos.z - realBoss.z
                )


                var boxColor = Color(255, 0, 0, 80f)
                if (maxBossHealth != null && currentBossHealth != null) {
                    if (currentBossHealth!! <= (maxBossHealth!! * 0.2f)) {
                        boxColor = Color(148, 0, 211, 80f)
                    }
                }


                drawStyledBox(aabb = aabb, color = boxColor, style = 0, depth = false)
            }

            if (ichorEsp && trackedIchorIds.isNotEmpty()) {
                val currentValidIds = mutableSetOf<Int>()

                mc.level!!.entitiesForRendering().forEach { entity ->
                    if (entity is ArmorStand && isBloodIchor(entity)) {

                        if (trackedIchorIds.contains(entity.id)) {
                            currentValidIds.add(entity.id)


                            drawStyledBox(
                                aabb = entity.boundingBox,
                                color = Color(220, 20, 60, 60f),
                                style = 2,
                                depth = false
                            )


                            if (ichorTracer) {
                                val targetCenter = entity.position().add(0.0, entity.bbHeight / 2.0, 0.0)
                                drawTracer(
                                    to = targetCenter,
                                    color = Colors.MINECRAFT_GREEN,
                                    depth = false,
                                    thickness = 2.5f
                                )
                            }
                        }
                    }
                }


                trackedIchorIds.retainAll(currentValidIds)
            }

            if (killerSpringTracer && myActiveBoss != null && trackedKillerSpringIds.isNotEmpty()) {

                val springs = trackedKillerSpringIds.mapNotNull { id ->
                    val entity = mc.level!!.getEntity(id)
                    if (entity != null && entity.isKillerSpring()) entity else null
                }

                val lowestSpring = springs.minByOrNull { it.y }

                if (lowestSpring != null) {
                    val yOffset = 1.5
                    val targetCenter = lowestSpring.position().add(0.0, yOffset, 0.0)

                    drawTracer(
                        to = targetCenter,
                        color = Colors.MINECRAFT_RED,
                        depth = false,
                        thickness = 2.5f
                    )
                }
            }

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

        on<HudRenderEvent> {
            if (mc.player == null || !bossArrow) return@on

            val player = mc.player!!

            val pt = dt.gameTimeDeltaTicks

            val playerPos = player.getPosition(pt)

            val pX = playerPos.x
            val pZ = playerPos.z

            val tX: Double
            val tZ: Double

            if (testArrow) {
                val timeSec = System.currentTimeMillis() / 1000.0
                val fakeDistance = 10.0
                tX = pX + fakeDistance * cos(timeSec)
                tZ = pZ + fakeDistance * sin(timeSec)
            }
            else {
                val target = myActiveRealBoss ?: return@on
                val targetPos = target.getPosition(pt)
                tX = targetPos.x
                tZ = targetPos.z
            }

            val dx = tX - pX
            val dz = tZ - pZ
            val angleToTarget = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f

            val relativeAngle = angleToTarget - player.yRot


            val rad = toRadians(relativeAngle.toDouble()) - Math.PI / 2

            val screenCenterX = mc.window.guiScaledWidth / 2f
            val screenCenterY = mc.window.guiScaledHeight / 2f
            val r = arrowRadius.toFloat()

            val renderX = screenCenterX + (r * cos(rad)).toFloat()
            val renderY = screenCenterY + (r * sin(rad)).toFloat()

            val poseStack = graphics.pose()

            poseStack.pushMatrix()

            poseStack.translate(renderX, renderY)
            val rotationRad = toRadians(relativeAngle.toDouble()).toFloat()
            poseStack.rotate(rotationRad)


            poseStack.scale(arrowScale, arrowScale)

            graphics.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                ARROW_TEXTURE,
                -16, -16,
                0f, 0f,
                32, 32,
                512, 512,
                512, 512,
                arrowColor.rgba
            )

            poseStack.popMatrix()
        }

    }

    private fun resetClickState() {
        clickTickTimer = 0
    }

    fun startImpelRotation(targetPitch: Float) {
        mc.player ?: return

        lastImpelTitleTime = System.currentTimeMillis()


        ClientRotationHandler.registerProvider(this)

        isDecoupled = true

        ClientRotationHandler.setYaw(mc.player!!.yRot)

        PlayerUtils.smoothRotate(
            yaw = null,
            pitch = null,
            speed = rotationSpeed.toFloat(),
            bodyOnly = true,
            targetProvider = { ClientRotationHandler.clientYaw to targetPitch }
        )
        currentState = ImpelState.ACTIVE_ROTATING
    }

    private fun performClick() {
        PlayerUtils.rightClick()
    }

    override fun onDisable() {
        resetAllStates()
        super.onDisable()
    }

    fun triggerAutoItem(itemName: String): Boolean {
        if (isUsingItem || mc.player == null) return false

        val targetSlot = PlayerUtils.findItemInHotbar(itemName) ?: return false


        val originalSlot = mc.player!!.inventory.selectedSlot


        isUsingItem = true


        PlayerUtils.setHotbarSlot(targetSlot)

        if (itemName.equals("holy ice", ignoreCase = true)) {

            schedule(2, true) {
                PlayerUtils.rightClick()

                schedule(1, true) {
                    PlayerUtils.rightClick()

                    schedule(2, true) {

                        PlayerUtils.setHotbarSlot(originalSlot)
                        isUsingItem = false
                    }
                }
            }
        } else {

            schedule(2, true) {
                PlayerUtils.rightClick()

                schedule(2, true) {

                    PlayerUtils.setHotbarSlot(originalSlot)
                    isUsingItem = false
                }
            }
        }

        return true
    }

    private fun isBloodIchor(entity: ArmorStand): Boolean {

        val headItem = entity.getItemBySlot(EquipmentSlot.HEAD)

        return !(headItem.isEmpty || !headItem.`is`(Items.PLAYER_HEAD)) && headItem.texture == BLOOD_ICHOR_TEXTURE

    }

    private fun Entity.isKillerSpring(): Boolean {
        if (this !is ArmorStand || !this.isInvisible) return false
        val headItem = this.getItemBySlot(EquipmentSlot.HEAD)
        if (!headItem.`is`(Items.PLAYER_HEAD) && headItem.isEmpty) return false
        return headItem.texture == KILLER_SPRING_B64
    }

    private fun resetAllStates() {
        isDecoupled = false
        currentState = ImpelState.IDLE
        activeImpelType = ""
        clickCooldown = 0
        actionTick = 0
        isUsingItem = false
        pendingIce = false
        hasSwappedForCurrentTwinclaws = false
        currentBossId = null
        hasUsedTubaForBossSpawn = false
        hasUsedTubaForCurrentMania = false
        myActiveBoss = null
        maxBossHealth = null
        currentBossHealth = null
        hasSwappedToSteakForCurrentBoss = false
        isHandlingKillerSpring = false
        isHoldingAttack = false
        PlayerUtils.stopRotation()
        if (mc.options.keyShift.isDown) mc.options.keyShift.isDown = false
        if (mc.options.keyJump.isDown) mc.options.keyJump.isDown = false
        trackedIchorIds.clear()
        isWaitingForIchorSpawn = false
        preExistingIchorIds.clear()
        ichorGraceTicks = 0
        trackedKillerSpringIds.clear()
        knownKillerSpringIds.clear()
        currentRenderedLayer = null
        currentManiaTime = null
        myActiveRealBoss = null
    }
}