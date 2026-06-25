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


    private val secretRegex = Regex("""(\d+)/(\d+) Secrets""")

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
        val matchResult = secretRegex.find(text)
        if (matchResult != null) {
            currentSecrets = matchResult.groupValues[1].toIntOrNull() ?: currentSecrets
            maxSecrets = matchResult.groupValues[2].toIntOrNull() ?: maxSecrets
        }
    }

    fun reset() {
        currentSecrets = 0
        maxSecrets = 0
    }
}