import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String
// 利用 Kotlin 的字串插值，把兩個版本號用 "-" 串接起來
version = "${property("mod_version")}-${property("minecraft_version")}"
val isPrivateBuild = (project.findProperty("isPrivate") as? String)?.toBoolean() ?: false

base {
    val originalBaseName = property("archives_base_name") as String
    val suffix = if (isPrivateBuild) "Private" else "Public"
    archivesName.set("$originalBaseName-$suffix")
}

// ====================================================
// 🛡️ 你的依賴庫區塊 (完全未改動，一字不漏！)
// ====================================================
repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.terraformersmc.com/")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.meteordev.org/releases")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")

    // 🌟 關鍵：使用 include 將指令系統打包進你的 jar
    property("commodore_version").let {
        implementation("com.github.stivais:Commodore:$it")
        include("com.github.stivais:Commodore:$it")
    }

    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    // 🌟 關鍵：使用 include 將 NanoVG UI 渲染引擎打包進你的 jar
    property("minecraft_lwjgl_version").let { lwjglVersion ->
        implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
        include("org.lwjgl:lwjgl-nanovg:$lwjglVersion")

        listOf("windows", "linux", "macos", "macos-arm64").forEach { os ->
            implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
            include("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
        }
    }

    compileOnly("maven.modrinth:iris:${property("iris")}")

}
// ====================================================

loom {
    accessWidenerPath = file("src/main/resources/heigui.accesswidener")
    runConfigs.named("client") {
        isIdeConfigGenerated = true
        vmArgs.addAll(
            arrayOf(
                "-Dmixin.debug.export=true",
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=main",
                "-XX:+AllowEnhancedClassRedefinition"
            )
        )
    }
    runConfigs.named("server") {
        isIdeConfigGenerated = false
    }
}

afterEvaluate {
    loom.runs.named("client") {
        vmArg("-javaagent:${configurations.compileClasspath.get().find { it.name.contains("sponge-mixin") }}")
    }
}

tasks {
    withType<AbstractArchiveTask>().configureEach {
        destinationDirectory.set(layout.buildDirectory.dir("libs/${project.version}"))
    }

    processResources {
        inputs.property("isPrivateBuild", isPrivateBuild)
        filesMatching("fabric.mod.json") {
            expand(getProperties())
        }
        filesMatching("build_type.properties") {
            expand(mapOf("isPrivateBuild" to isPrivateBuild.toString()))
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
// 🚀 一鍵雙 Build 整合任務 (顯示於 IDE 的 Tasks -> build 內)
// ====================================================
tasks.register("buildAllVersions") {
    group = "build"
    description = "Automatically cleans and builds both Public and Private versions."

    doLast {
        // 判斷系統環境來決定執行 gradlew 還是 gradlew.bat
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val gradlew = if (isWindows) "${project.rootDir}\\gradlew.bat" else "${project.rootDir}/gradlew"
        println("============================================")
        println("🔨 [1/2] Building PRIVATE Version...")
        println("============================================")

        // 不執行 clean（保留快取），直接打包 Private 版
        ProcessBuilder(gradlew, "build", "-PisPrivate=true")
            .directory(project.rootDir)
            .inheritIO()
            .start()
            .waitFor()
        println("============================================")
        println("🔨 [2/2] Building PUBLIC Version...")
        println("============================================")

        // 使用純 Kotlin/JVM 的 ProcessBuilder 呼叫指令，完美避開 Gradle 語法報錯
        ProcessBuilder(gradlew, "build", "-PisPrivate=false")
            .directory(project.rootDir) // 設定執行目錄為專案根目錄
            .inheritIO() // 🌟 關鍵魔法：讓子程序的打包進度直接印在你的 IDE 控制台！
            .start()
            .waitFor() // 等待打包完成再進行下一步


        println("============================================")
        println("✅ Done! Check your build/libs folder.")
        println("============================================")
    }
}