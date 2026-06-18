package com.iq200.heigui.utils

import com.iq200.heigui.Heigui.mc
import com.mojang.blaze3d.platform.InputConstants

fun InputConstants.Key.isDown() : Boolean {
    return this != InputConstants.UNKNOWN && InputConstants.isKeyDown(mc.window, this.value)
}