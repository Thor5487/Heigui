import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import proguard.gradle.ProGuardTask
import net.fabricmc.loom.task.RemapJarTask

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // 🌟 載入官方的 ProGuard 插件
        classpath("com.guardsquare:proguard-gradle:7.6.0")
    }
}

plugins {
    id("fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String

// ====================================================
// 🌟 1. 雙版本判斷與 BuildConfig 自動生成
// ====================================================
// 讀取你在指令裡傳入的參數 (如果沒傳，預設就是 false / Free 版)
val isAuthBuild = project.hasProperty("auth") && project.property("auth") == "true"

// 根據目前模式，自動幫你的 .jar 檔加上 -Auth 或 -Free 的後綴
val modVersion = project.properties["mod_version"] as String? ?: "1.0.0"
version = if (isAuthBuild) "${modVersion}-Auth" else "${modVersion}-No-Auth"

val generatedSrcDir = layout.buildDirectory.dir("generated/source/buildConfig/main/kotlin").get().asFile

val generateBuildConfig = tasks.register("generateBuildConfig") {
    outputs.dir(generatedSrcDir)
    outputs.upToDateWhen { false }
    doLast {
        val file = File(generatedSrcDir, "com/iq200/heigui/BuildConfig.kt")
        file.parentFile.mkdirs()
        file.writeText("""
            package com.iq200.heigui

            object BuildConfig {
                // 這個值會在每次打包時，自動變成 true 或 false！
                const val REQUIRE_AUTH = $isAuthBuild
            }
        """.trimIndent())
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildConfig)
}

sourceSets {
    main {
        kotlin.srcDir(generatedSrcDir)
    }
}

// ====================================================
// 🌟 2. 一鍵雙開魔法：攔截 build 按鈕
// ====================================================
if (!isAuthBuild) {
    val buildAuthTask = tasks.register<Exec>("buildAuthVersion") {
        group = "build"
        description = "自動在背景接力編譯 Auth 版"

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val gradlew = if (isWindows) "${project.rootDir}\\gradlew.bat" else "${project.rootDir}/gradlew"

        commandLine(gradlew, "clean", "build", "-Pauth=true", "--no-daemon")
    }

    tasks.build {
        finalizedBy(buildAuthTask)
    }
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
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")

    // 🌟 關鍵：使用 include 將指令系統打包進你的 jar
    property("commodore_version").let {
        implementation("com.github.stivais:Commodore:$it")
        include("com.github.stivais:Commodore:$it")
    }

    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    // 🌟 關鍵：使用 include 將 NanoVG UI 渲染引擎打包進你的 jar
    property("minecraft_lwjgl_version").let { lwjglVersion ->
        modImplementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
        include("org.lwjgl:lwjgl-nanovg:$lwjglVersion")

        listOf("windows", "linux", "macos", "macos-arm64").forEach { os ->
            modImplementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
            include("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
        }
    }

    modCompileOnly("maven.modrinth:iris:${property("iris")}")
}
// ====================================================

loom {
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
    processResources {
        filesMatching("fabric.mod.json") {
            expand(getProperties())
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            freeCompilerArgs.add("-Xlambdas=class")
        }
    }

    compileJava {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// ====================================================
// 🌟 3. ProGuard 與自動發布至 releases 資料夾
// ====================================================
val proguardTask = tasks.register<ProGuardTask>("proguard") {
    dependsOn(tasks.jar)
    configuration("proguard-rules.pro")

    val inputJar = tasks.jar.get().archiveFile.get().asFile
    injars(inputJar)

    // 混淆後的暫存檔案名稱
    val outputJar = file("${layout.buildDirectory.get().asFile}/libs/${base.archivesName.get()}-${project.version}-obf-temp.jar")
    outjars(outputJar)

    val javaHome = System.getProperty("java.home")
    libraryjars(
        fileTree("$javaHome/jmods") { include("java.base.jmod") }
    )
    libraryjars(configurations.compileClasspath.get().files)
}

tasks.remapJar {
    // 移除原本覆蓋 inputFile 的邏輯，讓它處理預設的 tasks.jar
    doLast {
        val finalJar = archiveFile.get().asFile
        if (finalJar.exists()) {
            println("🛡️ [處理中] 正在將【無混淆版】 ${finalJar.name} 移至安全發布區...")
            val releaseDir = file("${project.rootDir}/releases")
            releaseDir.mkdirs()

            project.copy {
                from(finalJar)
                into(releaseDir)
            }
            println("✅ [成功] 無混淆版已發布至：releases/${finalJar.name}")
        }
    }
}

val remapObfJar = tasks.register<RemapJarTask>("remapObfJar") {
    dependsOn(proguardTask)

    // 把 ProGuard 產生的暫存檔當作輸入
    inputFile.set(file("${layout.buildDirectory.get().asFile}/libs/${base.archivesName.get()}-${project.version}-obf-temp.jar"))

    // 加上 -obf 後綴，避免跟無混淆版本檔名衝突
    archiveClassifier.set("obf")

    doLast {
        val finalJar = archiveFile.get().asFile
        if (finalJar.exists()) {
            println("🛡️ [處理中] 正在將【混淆版】 ${finalJar.name} 移至安全發布區...")
            val releaseDir = file("${project.rootDir}/releases")
            releaseDir.mkdirs()

            project.copy {
                from(finalJar)
                into(releaseDir)
            }
            println("✅ [成功] 混淆版已發布至：releases/${finalJar.name}")
        }
    }
}

tasks.build {
    dependsOn(remapObfJar)
}