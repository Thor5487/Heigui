package com.iq200.heigui.features.impl.floor7.hoverterm

internal interface HoverTermCompat {
    fun start() = Unit
    fun stop() = Unit
}

internal object HoverTermCompatNoOp : HoverTermCompat

internal interface AutoTermGuard {
    fun isEnabledFor(settingName: String): Boolean = false
}

internal object AutoTermGuardNoOp : AutoTermGuard
