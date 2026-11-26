package com.iq200.heigui.api.zpbw

import com.iq200.heigui.features.impl.skyblock.ZeropingTeleport

/** Runtime-only prediction provider consumed by the Heigui-dependent ZPBW distribution. */
object HeiguiTeleportPredictionApi {
    @JvmStatic
    fun protocolVersion(): Int = 2

    @JvmStatic
    fun predictCurrentTeleport(): DoubleArray? = ZeropingTeleport.predictManagedTeleport()

    @JvmStatic
    fun predictionAccepted(ability: Int) = ZeropingTeleport.managedTeleportAccepted(ability)

    @JvmStatic
    fun pendingPassiveTeleports(): DoubleArray = ZeropingTeleport.pendingPassiveTeleports()

    @JvmStatic
    fun acceptPassiveTeleportHandoff(count: Int): Boolean =
        ZeropingTeleport.acceptPassiveTeleportHandoff(count)
}
