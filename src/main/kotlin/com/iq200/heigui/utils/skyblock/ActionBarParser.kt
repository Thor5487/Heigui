package com.iq200.heigui.utils.skyblock

import com.iq200.heigui.events.PacketEvent
import com.iq200.heigui.events.WorldEvent
import com.iq200.heigui.events.core.on
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

object ActionBarParser {
    var currentSecrets : Int = 0
        private set
    var maxSecrets : Int = 0
        private set


    // Stats
    var currentHealth: Int = 0
        private set
    var maxHealth: Int = 0
        private set
    var defense: Int = 0
        private set
    var currentMana: Int = 0
        private set
    var maxMana: Int = 0
        private set

    private val secretRegex = Regex("""(\d+)/(\d+) Secrets""")
    private val healthRegex = Regex("""(\d+)/(\d+)\uE010""")
    private val defenseRegex = Regex("""(\d+)\uE008""")
    private val manaRegex = Regex("""(\d+)/(\d+)\uE003""")

    init {
        on<WorldEvent.Load> {
            reset()
        }

        on<PacketEvent.Receive> {
            var rawText: String? = null

            if (packet is ClientboundSystemChatPacket && packet.overlay) {
                rawText = packet.content.string
            }
            else if (packet is ClientboundSetActionBarTextPacket) {
                rawText = packet.text.string
            }

            if (rawText != null) {
                val cleanText = rawText.replace(Regex("§[0-9a-fk-or]"), "")
                parseSecrets(cleanText)
            }
        }
    }

    private fun parseSecrets(text: String) {
        secretRegex.find(text)?.let {
            currentSecrets = it.groupValues[1].toIntOrNull() ?: currentSecrets
            maxSecrets = it.groupValues[2].toIntOrNull() ?: maxSecrets
        }

        // 解析血量 (Health)
        healthRegex.find(text)?.let {
            currentHealth = it.groupValues[1].toIntOrNull() ?: currentHealth
            maxHealth = it.groupValues[2].toIntOrNull() ?: maxHealth
        }

        // 解析防禦 (Defense)
        defenseRegex.find(text)?.let {
            defense = it.groupValues[1].toIntOrNull() ?: defense
        }

        // 解析魔力 (Mana)
        manaRegex.find(text)?.let {
            currentMana = it.groupValues[1].toIntOrNull() ?: currentMana
            maxMana = it.groupValues[2].toIntOrNull() ?: maxMana
        }
    }

    fun reset() {
        currentSecrets = 0
        maxSecrets = 0
        currentHealth = 0
        maxHealth = 0
        defense = 0
        currentMana = 0
        maxMana = 0
    }
}