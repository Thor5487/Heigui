package com.iq200.heigui.config

import com.iq200.heigui.Heigui
import java.io.File
import java.nio.file.Files

internal fun resolveConfigFile(fileName: String, legacyFileNames: List<String> = emptyList()): File {
    val target = File(Heigui.configDir, fileName)
    if (target.exists()) return target

    val legacy = legacyFileNames
        .asSequence()
        .map { File(Heigui.configDir, it) }
        .firstOrNull(File::exists)
        ?: return target

    target.parentFile?.mkdirs()
    return try {
        Files.move(legacy.toPath(), target.toPath())
        Heigui.logger.info("Migrated config file '${legacy.name}' to '${target.name}'.")
        target
    } catch (moveError: Exception) {
        try {
            legacy.copyTo(target, overwrite = false)
            if (!legacy.delete()) {
                Heigui.logger.warn(
                    "Copied config file '${legacy.name}' to '${target.name}', but could not remove the legacy file."
                )
            }
            target
        } catch (copyError: Exception) {
            Heigui.logger.error("Failed to migrate config file '${legacy.name}' to '${target.name}'.", moveError)
            Heigui.logger.error("Failed to copy legacy config file '${legacy.name}' as a fallback.", copyError)
            legacy
        }
    }
}
