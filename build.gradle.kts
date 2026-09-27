import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String
val isPrivateBuild = (project.findProperty("isPrivate") as? String)?.toBoolean() ?: false

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

    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    // Bundle NanoVG and its platform natives for the ClickGUI renderer.
    property("minecraft_lwjgl_version").let { lwjglVersion ->
        implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
        include("org.lwjgl:lwjgl-nanovg:$lwjglVersion")

        listOf("windows", "linux", "macos", "macos-arm64").forEach { os ->
            implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
            include("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
        }
    }

    compileOnly("maven.modrinth:iris:${property("iris")}")

    // Optional terminal solver integrations. These are available at compile time only
    // and are never bundled into Heigui or required at runtime.
    compileOnly("maven.modrinth:odin:${property("odin_version")}")
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
        // These inputs prevent stale generated metadata after a Git revision changes.
        inputs.property("isPrivateBuild", isPrivateBuild)
        inputs.property("modVersion", modVersion)
        inputs.property("buildChannel", buildChannel)
        inputs.property("commitHash", commitHash)

        filesMatching("fabric.mod.json") {
            expand(getProperties() + mapOf("mod_version" to modVersion))
        }
        filesMatching("build_type.properties") {
            expand(
                mapOf(
                    "isPrivateBuild" to isPrivateBuild.toString(),
                    "buildChannel" to buildChannel,
                    "commitHash" to commitHash,
                    "modVersion" to modVersion
                )
            )
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
            freeCompilerArgs.add("-Xlambdas=class")
        }
    }

    compileJava {
        sourceCompatibility = "25"
        targetCompatibility = "25"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

}

java {
    withSourcesJar()

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// ====================================================
// Combined public and private build task
// ====================================================
tasks.register("buildAllVersions") {
    group = "build"
    description = "Automatically cleans and builds both Public and Private versions."

    val rootDirFile = project.rootDir
    val rootDirPath = rootDirFile.absolutePath

    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val gradlew = if (isWindows) "$rootDirPath\\gradlew.bat" else "$rootDirPath/gradlew"
        println("============================================")
        println("🔨 [1/2] Building PRIVATE Version...")
        println("============================================")

        ProcessBuilder(gradlew, "build", "-PisPrivate=true")
            .directory(rootDirFile)
            .inheritIO()
            .start()
            .waitFor()
        println("============================================")
        println("🔨 [2/2] Building PUBLIC Version...")
        println("============================================")

        ProcessBuilder(gradlew, "build", "-PisPrivate=false")
            .directory(rootDirFile)
            .inheritIO()
            .start()
            .waitFor()


        println("============================================")
        println("✅ Done! Check build/libs/$modVersionBase-$mcVersion/$buildOutputDirectory.")
        println("============================================")
    }
}
