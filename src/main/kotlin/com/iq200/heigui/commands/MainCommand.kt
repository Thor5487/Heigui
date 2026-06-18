package com.iq200.heigui.commands

import com.github.stivais.commodore.Commodore
import com.iq200.heigui.Heigui.mc
import com.iq200.heigui.clickgui.ClickGUI
import com.iq200.heigui.utils.handlers.schedule


val mainCommand = Commodore("heigui", "hg") {

    runs {
        schedule(0) { mc.setScreen(ClickGUI) }
    }

    setupDebugCommand()
}