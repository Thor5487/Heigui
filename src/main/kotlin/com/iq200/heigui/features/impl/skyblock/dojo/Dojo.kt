package com.iq200.heigui.features.impl.skyblock.dojo

import com.iq200.heigui.clickgui.settings.Setting.Companion.withDependency
import com.iq200.heigui.clickgui.settings.impl.BooleanSetting
import com.iq200.heigui.clickgui.settings.impl.NumberSetting
import com.iq200.heigui.events.BlockUpdateEvent
import com.iq200.heigui.events.EntityLoadEvent
import com.iq200.heigui.events.RenderEvent
import com.iq200.heigui.events.TickEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import com.iq200.heigui.features.Category
import com.iq200.heigui.features.Module
import com.iq200.heigui.utils.ServerUtils
import com.iq200.heigui.utils.getScoreboardLines
import com.iq200.heigui.utils.noControlCodes

internal enum class DojoChallenge {
    NONE,
    DISCIPLINE,
    CONTROL,
    MASTERY
}

object Dojo : Module(
    name = "Dojo",
    description = "Helpers for the Discipline, Control, and Mastery dojo challenges.",
    category = Category.SKYBLOCK
) {
    internal val disciplineHelper by BooleanSetting(
        "Discipline Helper",
        true,
        "Selects the sword matching the helmet of the zombie you are aiming at."
    )
    internal val controlHelper by BooleanSetting(
        "Control Helper",
        true,
        "Predicts where to aim during the Test of Control."
    )
    internal val controlAutoAim by BooleanSetting(
        "Control Auto Aim",
        false,
        "Locks your aim to the predicted Control target."
    ).withDependency { controlHelper }
    internal val masteryHelper by BooleanSetting(
        "Mastery Helper",
        true,
        "Smoothly aims, draws the bow, and fires at Mastery targets."
    )
    internal val masteryAimSpeed by NumberSetting(
        "Mastery Aim Speed",
        720,
        180,
        1440,
        60,
        "Maximum Mastery turning speed.",
        "deg/s"
    ).withDependency { masteryHelper }
    internal val pingAdjustment by NumberSetting(
        "Ping Adjustment",
        0,
        -500,
        500,
        10,
        "Adds or subtracts latency from Control prediction and Mastery shot timing.",
        "ms"
    ).withDependency { controlHelper || masteryHelper }

    private var challenge = DojoChallenge.NONE

    init {
        on<WorldEvent.Load> {
            resetAll()
        }

        on<EntityLoadEvent> {
            if (controlHelper) Control.onEntityLoad(entity)
        }

        on<BlockUpdateEvent> {
            if (masteryHelper) Mastery.onBlockUpdate(pos, updated)
        }

        on<TickEvent.Start> {
            switchChallenge(detectChallenge())
            when (challenge) {
                DojoChallenge.DISCIPLINE -> if (disciplineHelper) Discipline.update()
                DojoChallenge.CONTROL -> if (controlHelper) Control.update()
                DojoChallenge.MASTERY -> if (masteryHelper) Mastery.update()
                DojoChallenge.NONE -> Unit
            }
        }

        on<RenderEvent.Extract> {
            when (challenge) {
                DojoChallenge.CONTROL -> if (controlHelper) Control.render(this)
                DojoChallenge.MASTERY -> if (masteryHelper) Mastery.render(this)
                else -> Unit
            }
        }
    }

    internal fun activateFromEvent(expected: DojoChallenge): Boolean {
        if (detectChallenge() != expected) return false
        switchChallenge(expected)
        return true
    }

    internal fun adjustedPing(): Int = (ServerUtils.currentPing + pingAdjustment).coerceAtLeast(0)

    private fun detectChallenge(): DojoChallenge {
        for (line in getScoreboardLines()) {
            val cleanLine = line.noControlCodes.trim()
            if (cleanLine.contains("Challenge: Discipline", ignoreCase = true)) return DojoChallenge.DISCIPLINE
            if (cleanLine.contains("Challenge: Control", ignoreCase = true)) return DojoChallenge.CONTROL
            if (cleanLine.contains("Challenge: Mastery", ignoreCase = true)) return DojoChallenge.MASTERY
        }
        return DojoChallenge.NONE
    }

    private fun switchChallenge(newChallenge: DojoChallenge) {
        if (challenge == newChallenge) return
        Control.reset()
        Mastery.reset()
        challenge = newChallenge
    }

    private fun resetAll() {
        Control.reset()
        Mastery.reset()
        challenge = DojoChallenge.NONE
    }

    override fun onDisable() {
        resetAll()
        super.onDisable()
    }
}
