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
import com.iq200.heigui.api.dungeon.HeiguiDungeonApi
import com.iq200.heigui.utils.*
import com.iq200.heigui.utils.camera.CameraHandler
import com.iq200.heigui.utils.camera.CameraPositionProvider
import com.iq200.heigui.utils.skyblock.ActionBarParser
import com.iq200.heigui.utils.skyblock.EtherUtils
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.*
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
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
    private val timeOutMs by NumberSetting("Timeout", 500, 100, 1000, 50, unit = "ms", desc = "Time before an unconfirmed teleport prediction times out")

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
    private val chainMaximum by NumberSetting(
        "Chain Maximum",
        5,
        1,
        5,
        1,
        "Maximum number of queued Blinkwarps."
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

    private data class PassiveZpcmPrediction(val ability: Int, val source: Vec3, val target: Vec3)

    private var renderPos: Vec3? = null
    private val zpcmSent = mutableListOf<PassiveZpcmPrediction>()
    private var lastZpcmPredictionAt = 0L
    private var lastWIMP: Long = 0

    internal val activeZpcmPosition: Vec3?
        get() {
            if (!enabled) return null
            //#if PRIVATE
            if (usesZpbw() && ZpbwManagedBridge.ownsPacketChain()) return ZpbwManagedBridge.cameraPosition()
            //#endif
            return renderPos
        }

    private val ignoredBlocks = listOf(
        ChestBlock::class.java, EnderChestBlock::class.java, TrappedChestBlock::class.java,
        LeverBlock::class.java, ButtonBlock::class.java, HopperBlock::class.java,
        AnvilBlock::class.java, DropperBlock::class.java, DispenserBlock::class.java,
        CauldronBlock::class.java
    )

    init {
        on<TickEvent.Start> {
            //#if PRIVATE
            applyZpbwSettings()
            if ((!usesZpbw() || !ZpbwManagedBridge.ownsPacketChain()) && renderPos != null &&
                System.currentTimeMillis() - lastZpcmPredictionAt >= timeOutMs) clearZpcm()
            //#else
            //$if (renderPos != null && System.currentTimeMillis() - lastZpcmPredictionAt >= timeOutMs) clearZpcm()
            //#endif
        }

        on<PlayerInputEvent.Use> {
            if (!isRoomAllowed()) return@on
            val stack = mc.player?.mainHandItem ?: return@on

            if (!isTpItem(stack)) return@on

            //#if PRIVATE
            if (usesZpbw() && ZpbwManagedBridge.ownsPacketChain()) return@on
            //#endif

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
            //#if PRIVATE
            if (usesZpbw() && ZpbwManagedBridge.ownsPacketChain()) return@on
            //#endif
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

        val old = zpcmSent.removeFirst().target
        val correct =
            old.x() == newPos.position().x() && old.y() == newPos.position().y() && old.z() == newPos.position().z()
        if (!correct || zpcmSent.isEmpty()) {
            clearZpcm()
        }
    }

    private fun setZpcmPosition(source: Vec3, target: Vec3, ability: Int) {
        renderPos = target
        lastZpcmPredictionAt = System.currentTimeMillis()
        zpcmSent.add(PassiveZpcmPrediction(ability, source, target))
        CameraHandler.registerProvider(this)
    }

    private fun submitZpcmPrediction(source: Vec3, target: Vec3, ability: Int): Boolean {
        //#if PRIVATE
        if (usesZpbw() && ZpbwManagedBridge.ownsPacketChain()) {
            if (!ZpbwManagedBridge.prepareExternalTeleport(source, target)) return false
            renderPos = target
            lastZpcmPredictionAt = System.currentTimeMillis()
            zpcmSent.clear()
            CameraHandler.registerProvider(this)
            return true
        }
        //#endif
        setZpcmPosition(source, target, ability)
        return true
    }

    private fun predictionOrigin(playerPosition: Vec3): Vec3 {
        //#if PRIVATE
        if (usesZpbw() && ZpbwManagedBridge.ownsPacketChain()) return ZpbwManagedBridge.sharedOrigin() ?: playerPosition
        //#endif
        return renderPos ?: playerPosition
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
        val currentPos = predictionOrigin(player.position())
        val eyePos = currentPos.add(0.0, EtherUtils.getEyeHeight().toDouble(), 0.0)

        if (sneaking && stack.isEtherwarpItem()) {
            if (!etherwarpEnabled || etherwarpType != 0) return
            val ether = EtherUtils.getEtherPosFromOrigin(eyePos, yaw, pitch, 57 + stack.getTunerDistance())
            val targetBlock = ether.first ?: return
            if (!ether.second) return

            submitZpcmPrediction(currentPos, Vec3(targetBlock.x + 0.5, targetBlock.y + 1.05, targetBlock.z + 0.5), 3)
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

            if (submitZpcmPrediction(currentPos, target, if (wimp) 2 else 1) && wimp) lastWIMP = now
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
        return HeiguiDungeonApi.isTeleportPredictionAllowed()
    }

    private fun isRoomAllowed(): Boolean {
        return HeiguiDungeonApi.isTeleportPredictionAllowed()
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

    /**
     * Live, side-effect-free ZPCM prediction queried by managed ZPBW immediately before a
     * real use is admitted. Layout: ability, source xyz, target xyz.
     */
    internal fun predictManagedTeleport(): DoubleArray? {
        //#if PRIVATE
        if (!enabled || !usesZpbw() || !isRoomAllowed()) return null
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val stack = player.mainHandItem
        if (!isTpItem(stack) || player.isShiftKeyDown || mc.options.keyShift.isDown) return null
        if (mc.hitResult is EntityHitResult) return null
        (mc.hitResult as? BlockHitResult)?.let { hit ->
            if (isIgnored(level.getBlockState(hit.blockPos).block)) return null
        }

        val witherImpact = isWitherImpactItem(stack)
        if (witherImpact && !witherImpactEnabled) return null
        if (!witherImpact && !instantTransmissionEnabled) return null
        if (witherImpact && System.currentTimeMillis() - lastWIMP < 125L) return null
        if (manaCheckEnabled && !hasEnoughMana(stack)) return null
        val distance = getTpDistance(stack)
        if (distance == 0) return null

        val source = predictionOrigin(player.position())
        val target = EtherUtils.predictTeleportCandidates(distance, source, player.yRot, player.xRot)
            .firstNotNullOfOrNull { prediction ->
                resolveZptpTarget(prediction.subtract(0.0, 1.0, 0.0))
            } ?: return null
        if (target.toBlockPos() == source.toBlockPos()) return null

        return doubleArrayOf(
            if (witherImpact) 2.0 else 1.0,
            source.x, source.y, source.z,
            target.x, target.y, target.z
        )
        //#else
        //$return null
        //#endif
    }

    internal fun managedTeleportAccepted(ability: Int) {
        //#if PRIVATE
        if (ability == 2) lastWIMP = System.currentTimeMillis()
        CameraHandler.registerProvider(this)
        //#endif
    }

    /** Already-issued passive ZPCM uses that ZPBW must adopt before an immediate EW. */
    internal fun pendingPassiveTeleports(): DoubleArray {
        //#if PRIVATE
        return zpcmSent.flatMap { prediction ->
            listOf(
                prediction.ability.toDouble(),
                prediction.source.x, prediction.source.y, prediction.source.z,
                prediction.target.x, prediction.target.y, prediction.target.z
            )
        }.toDoubleArray()
        //#else
        //$return doubleArrayOf()
        //#endif
    }

    /** Completes a handoff only if the passive queue did not change after it was inspected. */
    internal fun acceptPassiveTeleportHandoff(count: Int): Boolean {
        //#if PRIVATE
        if (count <= 0 || zpcmSent.size != count) return false
        renderPos = null
        zpcmSent.clear()
        lastZpcmPredictionAt = 0L
        CameraHandler.registerProvider(this)
        return true
        //#else
        //$return false
        //#endif
    }

    //#if PRIVATE
    private fun usesZpbw(): Boolean = etherwarpEnabled && etherwarpType == 1
    private fun showZpbwSettings(): Boolean = usesZpbw() && zpbwSettingsDropdown

    private fun applyZpbwSettings(force: Boolean = false) {
        if (!usesZpbw()) {
            if (zpbwActive) {
                ZpbwManagedBridge.disable()
                clearZpcm()
            }
            zpbwActive = false
            lastAppliedZpbwSettings = null
            return
        }

        val settings = ManagedZpbwSettings(
            noSneakDelay = noSneakDelay,
            timeoutTicks = (timeOutMs / 50).coerceIn(2, 20),
            chainMaximum = chainMaximum,
            smoothLandings = smoothLandings,
            serverLagCompensation = serverLagCompensation,
            failureNotification = failureNotification
        )
        if (!force && zpbwActive && settings == lastAppliedZpbwSettings) return
        if (ZpbwManagedBridge.apply(settings)) {
            if (!zpbwActive) clearZpcm()
            zpbwActive = true
            lastAppliedZpbwSettings = settings
        }
    }

    private data class ManagedZpbwSettings(
        val noSneakDelay: Boolean,
        val timeoutTicks: Int,
        val chainMaximum: Int,
        val smoothLandings: Boolean,
        val serverLagCompensation: Boolean,
        val failureNotification: Boolean
    )

    private object ZpbwManagedBridge {
        private const val API_CLASS = "com.hawkslol.zpbw.api.HeiguiZpbwApi"
        private const val LOOKUP_RETRY_NANOS = 1_000_000_000L
        private var configureMethod: Method? = null
        private var disableMethod: Method? = null
        private var prepareExternalTeleportMethod: Method? = null
        private var sharedOriginMethod: Method? = null
        private var cameraPositionMethod: Method? = null
        private var ownsPacketChainMethod: Method? = null
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
                Boolean::class.javaPrimitiveType!!
            )?.also { configureMethod = it } ?: return false

            return runCatching {
                method.invoke(
                    null,
                    true,
                    settings.noSneakDelay,
                    settings.timeoutTicks,
                    settings.chainMaximum,
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

        fun prepareExternalTeleport(source: Vec3, target: Vec3): Boolean {
            val double = Double::class.javaPrimitiveType!!
            val method = prepareExternalTeleportMethod ?: findMethod(
                "prepareExternalTeleport",
                double, double, double,
                double, double, double
            )?.also { prepareExternalTeleportMethod = it } ?: return false
            return runCatching {
                method.invoke(null, source.x, source.y, source.z, target.x, target.y, target.z) == true
            }.getOrDefault(false)
        }

        fun sharedOrigin(): Vec3? {
            val method = sharedOriginMethod ?: findMethod("sharedOrigin")?.also { sharedOriginMethod = it } ?: return null
            return runCatching { (method.invoke(null) as? DoubleArray)?.toVec3() }.getOrNull()
        }

        fun cameraPosition(): Vec3? {
            val method = cameraPositionMethod ?: findMethod("cameraPosition")?.also { cameraPositionMethod = it } ?: return null
            return runCatching { (method.invoke(null) as? DoubleArray)?.toVec3() }.getOrNull()
        }

        fun ownsPacketChain(): Boolean {
            val method = ownsPacketChainMethod ?: findMethod("ownsPacketChain")?.also { ownsPacketChainMethod = it }
                ?: return false
            return runCatching { method.invoke(null) == true }.getOrDefault(false)
        }

        private fun DoubleArray.toVec3(): Vec3? =
            takeIf { it.size == 3 && it.all(Double::isFinite) }?.let { Vec3(it[0], it[1], it[2]) }

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
        return activeZpcmPosition != null
    }

    override fun getCameraPosition(): Vec3? {
        return activeZpcmPosition
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
        return activeZpcmPosition
    }

    override fun getRotForHit(): Vec3 {
        return Vec3.ZERO
    }
}
