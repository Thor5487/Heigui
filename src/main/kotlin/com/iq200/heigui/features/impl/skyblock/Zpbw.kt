package com.iq200.heigui.features.impl.skyblock

//#if PRIVATE
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import java.lang.reflect.Method

/** Private-build control surface for the Heigui-managed ZPBW distribution. */
object Zpbw : Module(
    name = "ZPBW",
    description = "Zero Ping Blinkwarp managed by Heigui.",
    category = Category.SKYBLOCK
) {
    private val noSneakDelay by BooleanSetting(
        "No Sneak Delay",
        false,
        "Removes the sneak delay before Etherwarp."
    )
    private val timeoutTicks by NumberSetting(
        "Timeout",
        20,
        2,
        20,
        1,
        "Ticks before an unconfirmed Blinkwarp times out.",
        " ticks"
    )
    private val chainMaximum by NumberSetting(
        "Chain Maximum",
        5,
        1,
        5,
        1,
        "Maximum number of queued Blinkwarps."
    )
    private val disableInRestrictedLocations by BooleanSetting(
        "Restricted Locations",
        true,
        "Disables ZPBW in locations where prediction is unsafe."
    )
    private val smoothLandings by BooleanSetting(
        "Smooth Landings",
        false,
        "Smooths movement after a confirmed Blinkwarp landing."
    )
    private val serverLagCompensation by BooleanSetting(
        "Server Lag Compensation",
        true,
        "Enables ZPBW's experimental recovery and lag compensation."
    )
    private val failureNotification by BooleanSetting(
        "Failure Notification",
        false,
        "Shows a short chat message when Blinkwarp's predicted destination differs from the server result."
    )

    private var lastApplied: ManagedSettings? = null

    init {
        on<TickEvent.Start> {
            applySettings()
        }
    }

    override fun onEnable() {
        super.onEnable()
        applySettings(force = true)
    }

    override fun onDisable() {
        ZpbwManagedBridge.disable()
        lastApplied = null
        super.onDisable()
    }

    private fun applySettings(force: Boolean = false) {
        val settings = ManagedSettings(
            noSneakDelay = noSneakDelay,
            timeoutTicks = timeoutTicks,
            chainMaximum = chainMaximum,
            disableInRestrictedLocations = disableInRestrictedLocations,
            smoothLandings = smoothLandings,
            serverLagCompensation = serverLagCompensation,
            failureNotification = failureNotification
        )
        if (!force && settings == lastApplied) return
        if (ZpbwManagedBridge.apply(settings)) lastApplied = settings
    }

    private data class ManagedSettings(
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

        fun apply(settings: ManagedSettings): Boolean {
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
}
//#endif
