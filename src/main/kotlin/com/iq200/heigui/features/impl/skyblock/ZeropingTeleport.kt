package com.iq200.heigui.features.impl.skyblock

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.DropdownSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.PlayerInputEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.*
import com.iq200.heigui.utils.camera.CameraHandler
import com.iq200.heigui.utils.camera.CameraPositionProvider
import com.iq200.heigui.utils.skyblock.ActionBarParser
import com.iq200.heigui.utils.skyblock.EtherUtils
import com.iq200.heigui.utils.skyblock.dungeon.DungeonUtils
import com.iq200.heigui.utils.skyblock.dungeon.ScanUtils
import com.iq200.heigui.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.*
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
//#if PRIVATE
import java.lang.reflect.Method
//#endif



object ZeropingTeleport : Module (
    name = "Zeroping Teleport",
    description = "Zero-ping teleport prediction for supported teleport abilities.",
    category = Category.SKYBLOCK
), CameraPositionProvider {

    private val etherwarpEnabled by BooleanSetting("Etherwarp", true, "Predict Etherwarp teleports")
    private val etherwarpType by SelectorSetting(
        name = "Etherwarp Type",
        default = "ZPCM",
        options = listOf(
            "ZPCM",
            //#if PRIVATE
            "ZPBW"
            //#endif
        ),
        desc = "Select whether Etherwarp uses camera prediction or Blinkwarp."
    ).withDependency { etherwarpEnabled }
    private val instantTransmissionEnabled by BooleanSetting("Instant Transmission", true, "Predict AOTE/AOTV Instant Transmission teleports")
    private val witherImpactEnabled by BooleanSetting("Wither Impact", false, "Predict Wither Impact teleports")
    private val manaCheckEnabled by BooleanSetting("Mana Check", true, "Only predict teleports when you have enough mana")
    private val timeOutMs by NumberSetting("Timeout", 500, 100, 1000, 50, unit = "ms", desc = "Time before an unconfirmed camera prediction is cleared")

    //#if PRIVATE
    private val zpbwSettingsDropdown by DropdownSetting(
        "ZPBW Settings",
        desc = "Advanced settings for Zero Ping Blinkwarp."
    ).withDependency(::usesZpbw)
    private val noSneakDelay by BooleanSetting(
        "No Sneak Delay",
        false,
        "Removes the sneak delay before Etherwarp."
    ).withDependency(::showZpbwSettings)
    private val blinkwarpTimeoutTicks by NumberSetting(
        "Blinkwarp Timeout",
        20,
        2,
        20,
        1,
        "Ticks before an unconfirmed Blinkwarp times out.",
        " ticks"
    ).withDependency(::showZpbwSettings)
    private val chainMaximum by NumberSetting(
        "Chain Maximum",
        5,
        1,
        5,
        1,
        "Maximum number of queued Blinkwarps."
    ).withDependency(::showZpbwSettings)
    private val disableInRestrictedLocations by BooleanSetting(
        "Restricted Locations",
        true,
        "Disables ZPBW in locations where prediction is unsafe."
    ).withDependency(::showZpbwSettings)
    private val smoothLandings by BooleanSetting(
        "Smooth Landings",
        false,
        "Smooths movement after a confirmed Blinkwarp landing."
    ).withDependency(::showZpbwSettings)
    private val serverLagCompensation by BooleanSetting(
        "Server Lag Compensation",
        true,
        "Enables ZPBW's experimental recovery and lag compensation."
    ).withDependency(::showZpbwSettings)
    private val failureNotification by BooleanSetting(
        "Failure Notification",
        false,
        "Shows a short chat message when Blinkwarp's predicted destination differs from the server result."
    ).withDependency(::showZpbwSettings)

    private var lastAppliedZpbwSettings: ManagedZpbwSettings? = null
    private var zpbwActive = false
    //#endif

    private var renderPos: Vec3? = null
    private val zpcmSent = mutableListOf<Vec3>()
    private var lastZpcmPredictionAt = 0L
    private var lastWIMP: Long = 0

    internal val activeZpcmPosition: Vec3?
        get() = if (enabled) renderPos else null

    private val ignoredBlocks = listOf(
        ChestBlock::class.java, EnderChestBlock::class.java, TrappedChestBlock::class.java,
        LeverBlock::class.java, ButtonBlock::class.java, HopperBlock::class.java,
        AnvilBlock::class.java, DropperBlock::class.java, DispenserBlock::class.java,
        CauldronBlock::class.java
    )

    init {
        on<TickEvent.Start> {
            if (renderPos != null && System.currentTimeMillis() - lastZpcmPredictionAt >= timeOutMs) clearZpcm()
            //#if PRIVATE
            applyZpbwSettings()
            //#endif
        }

        on<PlayerInputEvent.Use> {
            if (!isRoomAllowed() || (DungeonUtils.isFloor(7) && DungeonUtils.inBoss)) return@on
            val stack = mc.player?.mainHandItem ?: return@on

            if (!isTpItem(stack)) return@on

            if (result is BlockHitResult) {
                val hitBlock = mc.level?.getBlockState(result.blockPos)?.block
                if (hitBlock != null && isIgnored(hitBlock)) return@on
            }

            if (!manaCheckEnabled || hasEnoughMana(stack)) {
                checkZpcm(stack, yRot, xRot)
            }
        }

        // Passive observation only: vanilla still applies and acknowledges the teleport packet.
        on<PacketEvent.Receive> {
            if (renderPos == null || packet !is ClientboundPlayerPositionPacket) return@on
            val player = mc.player ?: return@on
            val newPos = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(player),
                packet.change(),
                packet.relatives()
            )
            handleZpcm(newPos)
        }

        on<WorldEvent.Load> {
            clearZpcm()
        }
    }

    private fun handleZpcm(newPos : PositionMoveRotation) {
        if (zpcmSent.isEmpty()) {
            clearZpcm()
            return
        }

        val old : Vec3 = zpcmSent.removeFirst()
        val correct =
            old.x() == newPos.position().x() && old.y() == newPos.position().y() && old.z() == newPos.position().z()
        if (!correct || zpcmSent.isEmpty()) {
            clearZpcm()
        }
    }

    private fun setZpcmPosition(position: Vec3) {
        renderPos = position
        lastZpcmPredictionAt = System.currentTimeMillis()
        zpcmSent.add(position)
        CameraHandler.registerProvider(this)
    }

    private fun clearZpcm() {
        renderPos = null
        zpcmSent.clear()
        lastZpcmPredictionAt = 0L
    }



    private fun isIgnored(block: Block): Boolean {
        return ignoredBlocks.any { it.isInstance(block) }
    }

    private fun isTpItem(stack: ItemStack): Boolean {
        val id = stack.itemId
        if (id.containsOneOf(
            "ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID", "ETHERWARP_CONDUIT",
            "ASPECT_OF_THE_LEECH_1", "ASPECT_OF_THE_LEECH_2", "ASPECT_OF_THE_LEECH_3",
        )) return true

        if (id.containsOneOf("NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA")
            && stack.customData.getListOrEmpty("ability_scroll").size == 3)
            return true

        return false
    }

    private fun checkZpcm(stack: ItemStack, yaw: Float, pitch: Float) {
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (!isTpItem(stack) || !isRoomAllowedZPEW()) return

        if (mc.hitResult is BlockHitResult) {
            val hitBlock = level.getBlockState((mc.hitResult as BlockHitResult).blockPos).block
            if (isIgnored(hitBlock)) return
        }

        val sneaking = player.isShiftKeyDown
        // Continue chained predictions from the pending ZPCM destination; otherwise use
        // the player's current client position as the teleport origin.
        val currentPos = renderPos ?: player.position()
        val eyePos = currentPos.add(0.0, EtherUtils.getEyeHeight().toDouble(), 0.0)

        if (sneaking && stack.isEtherwarpItem()) {
            if (!etherwarpEnabled || etherwarpType != 0) return
            val ether = EtherUtils.getEtherPosFromOrigin(eyePos, yaw, pitch, 57 + stack.getTunerDistance())
            val targetBlock = ether.first ?: return
            if (!ether.second) return

            setZpcmPosition(Vec3(targetBlock.x + 0.5, targetBlock.y + 1.05, targetBlock.z + 0.5))
        } else if (!sneaking) {
            val wimp = isWitherImpactItem(stack)
            if (wimp && !witherImpactEnabled) return
            if (!wimp && !instantTransmissionEnabled) return
            val now = System.currentTimeMillis()
            if (wimp && (now - lastWIMP < 125L)) return

            val distance = getTpDistance(stack)
            if (distance == 0) return

            // Check predicted voxels from farthest to nearest, matching the server's fallback behavior.
            var target: Vec3? = null
            for (prediction in EtherUtils.predictTeleportCandidates(distance, currentPos, yaw, pitch)) {
                val resolved = resolveZptpTarget(prediction.subtract(0.0, 1.0, 0.0)) ?: continue
                target = resolved
                break
            }
            target ?: return


            if (target.toBlockPos() == currentPos.toBlockPos()) return

            setZpcmPosition(target)

            if (wimp) lastWIMP = now
        }

    }


    private fun isWitherImpactItem(item: ItemStack): Boolean {
        val itemId: String = item.itemId
        if (!itemId.containsOneOf("NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA")) {
            return false
        }

        return item.customData.getListOrEmpty("ability_scroll").size == 3
    }

    private fun getTpDistance(item: ItemStack): Int {
        return when (item.itemId) {
            "ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID" -> 8 + item.getTunerDistance()
            "ASPECT_OF_THE_LEECH_1" -> 3
            "ASPECT_OF_THE_LEECH_2" -> 4
            "ASPECT_OF_THE_LEECH_3" -> 5
            "NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA" -> if (item.customData.getListOrEmpty("ability_scroll").size == 3
            ) 10 else 0

            else -> 0
        }
    }

    private fun resolveZptpTarget(target: Vec3): Vec3? {
        if (isSafeZptpTarget(target)) return target

        val level = mc.level ?: return null
        val feet = target.toBlockPos()
        val feetState = level.getBlockState(feet)
        val feetShape = feetState.getCollisionShape(level, feet)

        // Only correct an eye/feet Y mismatch. If the feet block is already empty,
        // the failure came from another collision and must fall back to the previous voxel.
        if (feetShape.isEmpty || feetState.block is CarpetBlock) return null

        val above: Vec3 = target.add(0.0, 1.0, 0.0)
        return if (isSafeZptpTarget(above)) above else null
    }

    private fun isSafeZptpTarget(target: Vec3): Boolean {
        val level = mc.level ?: return false

        val feet = target.toBlockPos()
        if (!level.hasChunk(feet.x shr 4, feet.z shr 4)) return false

        val below = feet.below()
        val head = feet.above()
        val belowState = level.getBlockState(below)
        val belowShape = belowState.getCollisionShape(level, below)
        val feetState = level.getBlockState(feet)
        val headState = level.getBlockState(head)

        // Only reject blocks whose collision shape extends into the player's feet block space.
        // Air, slabs, stairs, and normal blocks are valid; walls and fences extend above 1.0.
        val belowClear = belowShape.isEmpty || belowShape.bounds().maxY <= 1.0

        val feetClear =
            feetState.block is CarpetBlock ||
                    feetState.getCollisionShape(level, feet).isEmpty

        val headClear =
            headState.getCollisionShape(level, head).isEmpty

        return belowClear && feetClear && headClear
    }

    private fun isRoomAllowedZPEW(): Boolean {
        val room: String? = if (ScanUtils.currentRoom == null) null else ScanUtils.currentRoom!!.data.name
        return room == null || !room.containsOneOf("Boulder", "Teleport Maze") && ScanUtils.currentRoom!!.data
            .type !== RoomType.TRAP
    }

    private fun isRoomAllowed(): Boolean {
        val room = ScanUtils.currentRoom
        return room == null || !room.data.name.containsOneOf(
            "Boulder",
            "Teleport Maze"
        ) && room.data.type !== RoomType.TRAP
    }

    private fun hasEnoughMana(stack: ItemStack): Boolean {
        val currentMana = ActionBarParser.currentMana
        val id = stack.itemId

        return when {

            id.containsOneOf("NECRON_BLADE", "SCYLLA", "HYPERION", "VALKYRIE", "ASTRAEA") -> {
                currentMana >= 150
            }

            id.containsOneOf("ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID", "ETHERWARP_CONDUIT", "ASPECT_OF_THE_LEECH_1", "ASPECT_OF_THE_LEECH_2", "ASPECT_OF_THE_LEECH_3") -> {
                currentMana >= 50
            }
            else -> true
        }
    }

    //#if PRIVATE
    private fun usesZpbw(): Boolean = etherwarpEnabled && etherwarpType == 1
    private fun showZpbwSettings(): Boolean = usesZpbw() && zpbwSettingsDropdown

    private fun applyZpbwSettings(force: Boolean = false) {
        if (!usesZpbw()) {
            if (zpbwActive) ZpbwManagedBridge.disable()
            zpbwActive = false
            lastAppliedZpbwSettings = null
            return
        }

        val settings = ManagedZpbwSettings(
            noSneakDelay = noSneakDelay,
            timeoutTicks = blinkwarpTimeoutTicks,
            chainMaximum = chainMaximum,
            disableInRestrictedLocations = disableInRestrictedLocations,
            smoothLandings = smoothLandings,
            serverLagCompensation = serverLagCompensation,
            failureNotification = failureNotification
        )
        if (!force && zpbwActive && settings == lastAppliedZpbwSettings) return
        if (ZpbwManagedBridge.apply(settings)) {
            zpbwActive = true
            lastAppliedZpbwSettings = settings
        }
    }

    private data class ManagedZpbwSettings(
        val noSneakDelay: Boolean,
        val timeoutTicks: Int,
        val chainMaximum: Int,
        val disableInRestrictedLocations: Boolean,
        val smoothLandings: Boolean,
        val serverLagCompensation: Boolean,
        val failureNotification: Boolean
    )

    private object ZpbwManagedBridge {
        private const val API_CLASS = "com.hawkslol.zpbw.api.HeiguiZpbwApi"
        private const val LOOKUP_RETRY_NANOS = 1_000_000_000L
        private var configureMethod: Method? = null
        private var disableMethod: Method? = null
        private var nextLookupAtNanos = 0L

        fun apply(settings: ManagedZpbwSettings): Boolean {
            val method = configureMethod ?: findMethod(
                "configureCore",
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!
            )?.also { configureMethod = it } ?: return false

            return runCatching {
                method.invoke(
                    null,
                    true,
                    settings.noSneakDelay,
                    settings.timeoutTicks,
                    settings.chainMaximum,
                    settings.disableInRestrictedLocations,
                    settings.smoothLandings,
                    settings.serverLagCompensation,
                    settings.failureNotification
                ) == true
            }.getOrDefault(false)
        }

        fun disable() {
            val method = disableMethod ?: findMethod("disable")?.also { disableMethod = it } ?: return
            runCatching { method.invoke(null) }
        }

        private fun findMethod(name: String, vararg parameterTypes: Class<*>): Method? {
            val now = System.nanoTime()
            if (now < nextLookupAtNanos) return null

            return runCatching {
                Class.forName(API_CLASS).getMethod(name, *parameterTypes)
            }.onSuccess {
                nextLookupAtNanos = 0L
            }.onFailure {
                nextLookupAtNanos = now + LOOKUP_RETRY_NANOS
            }.getOrNull()
        }
    }
    //#endif

    override fun onEnable() {
        super.onEnable()
        //#if PRIVATE
        applyZpbwSettings(force = true)
        //#endif
    }

    override fun onDisable() {
        clearZpcm()
        //#if PRIVATE
        ZpbwManagedBridge.disable()
        zpbwActive = false
        lastAppliedZpbwSettings = null
        //#endif
        super.onDisable()
    }

    override fun shouldOverridePosition(): Boolean {

        return enabled && renderPos != null
    }

    override fun getCameraPosition(): Vec3? {
        return renderPos
    }

    override fun shouldOverrideHitPos(): Boolean {

        return false
    }

    override fun shouldOverrideHitRot(): Boolean {
        return false
    }

    override fun shouldBlockKeyboardMovement(): Boolean {
        return false
    }

    override fun getPosForHit(): Vec3? {
        return renderPos
    }

    override fun getRotForHit(): Vec3 {
        return Vec3.ZERO
    }
}
