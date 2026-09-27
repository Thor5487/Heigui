import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File

private val directivePrefix = Regex("^//\\s*")
private const val uncommentMarker = "//$"

private data class ConditionalBlock(
    val outerIncluded: Boolean,
    val conditionMatched: Boolean,
)

private fun processSource(
    sourceFile: File,
    variantName: String,
    stripLineComments: Boolean = false,
): List<String> {
    val isPrivate = variantName == "private"
    val blocks = mutableListOf<ConditionalBlock>()
    val output = mutableListOf<String>()
    var include = true

    sourceFile.readLines().forEachIndexed { index, line ->
        val lineNumber = index + 1
        val directive = line.trim().replaceFirst(directivePrefix, "")

        when (directive) {
            "#if PRIVATE" -> {
                blocks += ConditionalBlock(include, isPrivate)
                include = include && isPrivate
            }

            "#if PUBLIC" -> {
                blocks += ConditionalBlock(include, !isPrivate)
                include = include && !isPrivate
            }

            "#else" -> {
                if (blocks.isEmpty()) {
                    throw GradleException("Unmatched #else in $sourceFile at line $lineNumber")
                }

                val block = blocks.last()
                include = block.outerIncluded && !block.conditionMatched
            }

            "#endif" -> {
                if (blocks.isEmpty()) {
                    throw GradleException("Unmatched #endif in $sourceFile at line $lineNumber")
                }

                include = blocks.removeAt(blocks.lastIndex).outerIncluded
            }

            else -> if (include) {
                val trimmed = line.trim()
                if (stripLineComments && trimmed.startsWith("//") && !trimmed.startsWith(uncommentMarker)) {
                    return@forEachIndexed
                }

                val markerIndex = line.indexOf(uncommentMarker)
                output += if (markerIndex >= 0) {
                    line.removeRange(markerIndex, markerIndex + uncommentMarker.length)
                } else {
                    line
                }
            }
        }
    }

    if (blocks.isNotEmpty()) {
        throw GradleException("Unclosed #if in $sourceFile")
    }

    return output
}

fun Project.registerSourcePreprocessor(variantName: String): TaskProvider<Task> {
    require(variantName == "private" || variantName == "public") {
        "Unknown source variant: $variantName"
    }

    val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }
    val outputRoot = layout.buildDirectory.dir("preprocessed/$variantName")
    val modId = property("mod_id").toString()
    val fabricModTemplate = file("src/main/resources/fabric.mod.json5")
    val mixinTemplate = file("src/main/resources/$modId.mixins.json5")

    return tasks.register("preprocess${capitalizedVariant}Sources") {
        inputs.dir("src/main/kotlin")
        inputs.dir("src/main/java")
        inputs.file(fabricModTemplate)
        inputs.file(mixinTemplate)
        inputs.property("variantName", variantName)
        outputs.dir(outputRoot)

        doFirst {
            delete(outputRoot.get().asFile)
        }

        doLast {
            fun processTree(sourceRoot: File, destinationRoot: File) {
                if (!sourceRoot.exists()) return

                fileTree(sourceRoot)
                    .matching { include("**/*.kt", "**/*.java") }
                    .files
                    .forEach { sourceFile ->
                        val relativePath = sourceRoot.toPath().relativize(sourceFile.toPath()).toString()
                        val destination = File(destinationRoot, relativePath)
                        destination.parentFile.mkdirs()
                        destination.writeText(
                            processSource(sourceFile, variantName).joinToString(System.lineSeparator())
                        )
                    }
            }

            val root = outputRoot.get().asFile
            processTree(file("src/main/kotlin"), File(root, "kotlin"))
            processTree(file("src/main/java"), File(root, "java"))

            fun processResource(template: File, outputName: String) {
                val destination = File(root, "resources/$outputName")
                destination.parentFile.mkdirs()
                destination.writeText(
                    processSource(template, variantName, stripLineComments = true)
                        .joinToString(System.lineSeparator())
                )
            }

            processResource(fabricModTemplate, "fabric.mod.json")
            processResource(mixinTemplate, "$modId.mixins.json")
        }
    }
}
