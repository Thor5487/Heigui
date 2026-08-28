package com.iq200.heigui.utils.ui.animations

object Animations {

    var generation: Int = 0
        private set

    fun settle() {
        generation++
    }

    const val UNSETTLED = Int.MIN_VALUE
}