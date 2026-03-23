package com.iq200.heigui.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.iq200.heigui.Heigui.mc
import net.fabricmc.loader.api.FabricLoader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AuthManager {
    @Volatile
    var isAuthorized = false

    // 🌟 Replace with your actual Flask server IP and port
    // If testing on the same computer, use http://127.0.0.1:5000/api/verify
    private val encryptedUrl = byteArrayOf(0x0a, 0x24, 0x4e, 0x4c, 0x7f, 0x4e, 0x4a, 0x41, 0x0a, 0x73, 0x16, 0x53, 0x41, 0x0d, 0x5d, 0x54, 0x75, 0x49, 0x5c, 0x4c, 0x4d, 0x4b, 0x5f, 0x6f, 0x43, 0x57, 0x03, 0x1f, 0x48, 0x49, 0x42, 0x4d, 0x26, 0x5f, 0x4e, 0x2c, 0x07, 0x1c)

    private const val DECRYPT_KEY = "bP:<Eaep3A8as:seCpr}{seZsg30)9+"
    // File location to save the license key securely
    private val licenseFile = FabricLoader.getInstance().gameDir.resolve("Heigui_License.txt").toFile()
    private var onSuccessCallback: (() -> Unit)? = null

    private fun getApiUrl(): String {
        val decrypted = ByteArray(encryptedUrl.size)
        for (i in encryptedUrl.indices) {
            decrypted[i] = (encryptedUrl[i].toInt() xor DECRYPT_KEY[i % DECRYPT_KEY.length].code).toByte()
        }
        return String(decrypted)
    }

    // Auto-check on module startup
    fun verifySavedKey(onSuccess: () -> Unit) {
        onSuccessCallback = onSuccess
        if (licenseFile.exists()) {
            val savedKey = licenseFile.readText().trim()
            if (savedKey.isNotEmpty()) {
                println("[Heigui] Saved license key found, verifying in background...")
                verifyWithServer(savedKey)
            }
        } else {
            println("[Heigui] No saved license file found, waiting for user input.")
        }
    }

    // Called when a player manually inputs a license key via command
    fun registerNewKey(newKey: String) {
        modMessage("§e[Heigui] Connecting to authentication server...")
        verifyWithServer(newKey, saveKeyOnSuccess = true)
    }

    // Core verification logic (communicating with your custom Flask API)
    private fun verifyWithServer(licenseKey: String, saveKeyOnSuccess: Boolean = false) {
        thread {
            try {
                val hwid = HWIDUtils.getHWID()

                // Build the JSON payload to send to Flask
                val payload = """
                    {
                        "key": "$licenseKey",
                        "hwid": "$hwid"
                    }
                """.trimIndent()

                val response = sendPostRequest(getApiUrl(), payload)

                // Check the 'success' boolean from your Flask JSON response
                if (response.get("success").asBoolean) {
                    isAuthorized = true

                    if (saveKeyOnSuccess) {
                        licenseFile.writeText(licenseKey)
                        modMessage("§a[Heigui] ✅ Verification successful!")
                        modMessage("§e[Heigui] ⚠ Please RESTART your game to load the premium GUI!")
                    } else {
                        println("[Heigui Log] Auto-verified successfully.")
                    }

                    // Execute module feature registration
                    mc.execute {
                        onSuccessCallback?.invoke()
                        onSuccessCallback = null
                    }
                } else {
                    isAuthorized = false
                    modMessage("§c[Heigui] ❌ Verification failed: ${response.get("message").asString}")
                }

            } catch (e: Exception) {
                modMessage("§c[Heigui] ❌ Unable to connect to auth server! Please check your network connection.")
                println("[Heigui Error] ${e.message}")
            }
        }
    }

    // Utility for handling HTTP POST requests with JSON payload
    private fun sendPostRequest(urlString: String, jsonInputString: String): JsonObject {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        // 🌟 Updated to application/json for Flask compatibility
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
            writer.write(jsonInputString)
            writer.flush()
        }

        // Handle both 200 OK and 400/403 Error responses properly
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return JsonParser.parseReader(InputStreamReader(stream, "UTF-8")).asJsonObject
    }
}