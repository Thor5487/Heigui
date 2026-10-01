import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.tasks.Jar

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String
val modId = property("mod_id") as String
val isPrivateBuild = (project.findProperty("isPrivate") as? String)?.toBoolean() ?: false
val sourceVariant = if (isPrivateBuild) "private" else "public"
val preprocessSources = registerSourcePreprocessor(sourceVariant)
val preprocessedKotlin = layout.buildDirectory.dir("preprocessed/$sourceVariant/kotlin")
val preprocessedJava = layout.buildDirectory.dir("preprocessed/$sourceVariant/java")
val preprocessedResources = layout.buildDirectory.dir("preprocessed/$sourceVariant/resources")

// ====================================================
// Release and beta version metadata
// ====================================================

// Returns null when Git is unavailable or the command fails.
fun gitOutput(vararg args: String): String? = try {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(rootDir)
        // Keep Git errors out of the value consumed by the build script.
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && output.isNotEmpty()) output else null
} catch (e: Exception) {
    null
}

val modVersionBase = property("mod_version") as String
val mcVersion = property("minecraft_version") as String

// A build is a release only when HEAD is on the matching version tag.
// The property is a fallback for environments where Git is unavailable.
val isReleaseBuild = project.hasProperty("release") ||
        gitOutput("describe", "--exact-match", "--tags", "HEAD") == "v$modVersionBase"

val commitHash = gitOutput("rev-parse", "--short", "HEAD") ?: "unknown"

// The number of commits since the last tag becomes the beta sequence number.
val betaNumber = gitOutput("rev-list", "--count", "HEAD", "--not", "--tags")?.toIntOrNull() ?: 0

val buildChannel = if (isReleaseBuild) "release" else "beta"
val buildOutputDirectory = if (isReleaseBuild) "release" else "beta.$betaNumber"

// Release: 1.4.2; beta: 1.4.2-beta.3.
val modVersion = if (isReleaseBuild) {
    modVersionBase
} else {
    "$modVersionBase-beta.$betaNumber"
}


version = "$modVersion-$mcVersion"

base {
    val originalBaseName = property("archives_base_name") as String
    val suffix = if (isPrivateBuild) "Private" else "Public"
    archivesName.set("$originalBaseName-$suffix")
}

// ====================================================
// Dependencies
// ====================================================
repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.terraformersmc.com/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")

    // Bundle Commodore because it is required at runtime.
    property("commodore_version").let {
        implementation("com.github.stivais:Commodore:$it")
        include("com.github.stivais:Commodore:$it")
    }

    // Discover and register modules without maintaining a manual registry.
    property("classgraph_version").let {
        implementation("io.github.classgraph:classgraph:$it")
        include("io.github.classgraph:classgraph:$it")
    }

    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    compileOnly("maven.modrinth:iris:${property("iris")}")

    // Optional terminal solver integrations. These are available at compile time only
    // and are never bundled into Heigui or required at runtime.
    compileOnly("com.github.odtheking:Odin:${property("odin_version")}") {
        isTransitive = false
    }
    compileOnly("maven.modrinth:noammaddons:${property("noammaddons_version")}")
}
// ====================================================

loom {
    accessWidenerPath = file("src/main/resources/heigui.accesswidener")
    runConfigs.named("client") {
        generateRunConfig.set(true)
        jvmArguments.addAll(
            "-Dmixin.debug.export=true",
            "-Ddevauth.enabled=true",
            "-Ddevauth.account=main",
            "-Dfabric.log.disableAnsi=false",
            "-XX:StackShadowPages=32",
            "-XX:+AllowEnhancedClassRedefinition",
            "-XX:+IgnoreUnrecognizedVMOptions"
        )
    }
    runConfigs.named("server") {
        generateRunConfig.set(false)
    }
}

afterEvaluate {
    loom.runs.named("client") {
        jvmArguments.add("-javaagent:${configurations.compileClasspath.get().find { it.name.contains("sponge-mixin") }}")
    }
}

tasks {
    withType<AbstractArchiveTask>().configureEach {
        destinationDirectory.set(
            layout.buildDirectory.dir("libs/$modVersionBase-$mcVersion/$buildOutputDirectory")
        )
    }

    processResources {
        dependsOn(preprocessSources)

        // These inputs prevent stale generated metadata after a Git revision changes.
        inputs.property("modVersion", modVersion)
        inputs.property("buildChannel", buildChannel)
        inputs.property("commitHash", commitHash)

        exclude("fabric.mod.json5", "$modId.mixins.json5")

        from(preprocessedResources) {
            include("fabric.mod.json", "$modId.mixins.json")
        }

        filesMatching("fabric.mod.json") {
            expand(getProperties() + mapOf("mod_version" to modVersion))
        }
        filesMatching("build_type.properties") {
            expand(
                mapOf(
                    "buildChannel" to buildChannel,
                    "commitHash" to commitHash,
                    "modVersion" to modVersion
                )
            )
        }
    }

    compileKotlin {
        dependsOn(preprocessSources)
        setSource(preprocessedKotlin)

        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
            freeCompilerArgs.add("-Xlambdas=class")
            freeCompilerArgs.add("-Xjava-source-roots=${preprocessedJava.get().asFile.invariantSeparatorsPath}")
        }
    }

    compileJava {
        dependsOn(preprocessSources, compileKotlin)
        setSource(preprocessedJava)
        classpath += files(compileKotlin.flatMap { it.destinationDirectory })
        sourceCompatibility = "25"
        targetCompatibility = "25"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    dependsOn(preprocessSources)
    archiveClassifier.set("sources")
    from(preprocessedKotlin)
    from(preprocessedJava)
    from("src/main/resources") {
        exclude("fabric.mod.json5", "$modId.mixins.json5")
    }
    from(preprocessedResources)
}

tasks.named("assemble") {
    dependsOn(sourcesJar)
}

// ====================================================
// Public and private build tasks
// ====================================================
fun runVariantBuild(isPrivate: Boolean) {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val gradlew = if (isWindows) rootDir.resolve("gradlew.bat") else rootDir.resolve("gradlew")
    val variantName = if (isPrivate) "Private" else "Public"
    val command = mutableListOf(
        gradlew.absolutePath,
        "build",
        "-PisPrivate=$isPrivate"
    )

    if (project.hasProperty("release")) command += "-Prelease"

    println("Building $variantName version...")
    val exitCode = ProcessBuilder(command)
        .directory(rootDir)
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        throw GradleException("$variantName build failed with exit code $exitCode.")
    }
}

tasks.named("build") {
    group = null
    description = "Internal lifecycle task used by the variant build tasks."
}

tasks.register("buildPublic") {
    group = "build"
    description = "Builds the Public version."

    doLast {
        runVariantBuild(isPrivate = false)
    }
}

tasks.register("buildPrivate") {
    group = "build"
    description = "Builds the Private version."

    doLast {
        runVariantBuild(isPrivate = true)
    }
}

tasks.register("buildAllVersions") {
    group = "build"
    description = "Builds both Public and Private versions."

    doLast {
        runVariantBuild(isPrivate = false)
        runVariantBuild(isPrivate = true)
        println("Both versions are available in build/libs/$modVersionBase-$mcVersion/$buildOutputDirectory.")
    }
}
