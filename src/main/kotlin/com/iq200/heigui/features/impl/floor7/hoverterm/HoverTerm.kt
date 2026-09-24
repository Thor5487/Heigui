package com.iq200.heigui.features.impl.floor7.hoverterm

import com.iq200.heigui.clickgui.settings.impl.SelectorSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import net.fabricmc.loader.api.FabricLoader

object HoverTerm : Module(
    name = "Hover Term",
    description = "Automatically clicks the required terminal slot when it is hovered.",
    category = Category.FLOOR7
) {
    private val loader = FabricLoader.getInstance()

    private val availableModes = buildList {
        if (loader.isModLoaded("odin")) add("Odin")
        if (loader.isModLoaded("noammaddons")) add("Noamm")
    }
    private val modeOptions = availableModes.ifEmpty { listOf("No Solver Available") }

    private val noammCompat: HoverTermCompat by lazy {
        if (loader.isModLoaded("noammaddons")) NoammHoverTermCompat else HoverTermCompatNoOp
    }
    private val odinCompat: HoverTermCompat by lazy {
        if (loader.isModLoaded("odin")) OdinHoverTermCompat else HoverTermCompatNoOp
    }
    private val autoTermGuard: AutoTermGuard by lazy {
        if (loader.isModLoaded("noammaddons")) NoammAutoTermGuard else AutoTermGuardNoOp
    }

    private val modeIndex by SelectorSetting(
        name = "Mode",
        default = modeOptions.first(),
        options = modeOptions,
        desc = "The installed terminal solver GUI to use."
    )

    val mode: String
        get() = modeOptions[modeIndex]

    internal val clickDelay by NumberSetting(
        name = "Click Delay",
        default = 0,
        min = 0,
        max = 100,
        increment = 10,
        desc = "Minimum delay between each Hover Term click.",
        unit = "ms"
    )

    internal fun isAutoTermEnabledFor(settingName: String): Boolean =
        autoTermGuard.isEnabledFor(settingName)

    override fun onEnable() {
        super.onEnable()
        noammCompat.start()
        odinCompat.start()
    }

    override fun onDisable() {
        noammCompat.stop()
        odinCompat.stop()
        super.onDisable()
    }
}
