import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import proguard.gradle.ProGuardTask

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
// 1. 讀取你在指令裡傳入的參數 (如果沒傳，預設就是 false / Free 版)
val isAuthBuild = project.hasProperty("auth") && project.property("auth") == "true"

// 2. 根據目前模式，自動幫你的 .jar 檔加上 -Auth 或 -Free 的後綴
version = if (isAuthBuild) "${property("mod_version")}-Auth" else "${property("mod_version")}-Free"

// 3. 讓 Gradle 在編譯前，自動寫出一個 BuildConfig.kt 讓你的 Kotlin 讀取
val generatedSrcDir = layout.buildDirectory.dir("generated/source/buildConfig/main/kotlin").get().asFile

val generateBuildConfig = tasks.register("generateBuildConfig") {
    outputs.dir(generatedSrcDir)
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

// 4. 確保每次編譯程式碼之前，都會先執行上面的自動產生任務
tasks.named("compileKotlin") {
    dependsOn(generateBuildConfig)
}

// 5. 告訴 Gradle，這個自動產生的資料夾也是我們原始碼的一部分
sourceSets {
    main {
        kotlin.srcDir(generatedSrcDir)
    }
} // 🌟 回歸單一版本號，不再加上 -Auth 後綴

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
    // ❌ 保持移除 withSourcesJar()，保護你的原始碼不被玩家輕易解壓縮看到
}


val proguardTask = tasks.register<ProGuardTask>("proguard") {
    // 告訴 Gradle：必須等普通的 jar 打包完，才能執行混淆
    dependsOn(tasks.jar)

    // 讀取我們剛剛寫好的 proguard.txt 設定檔
    configuration("proguard-rules.pro")

    // 輸入：抓取原本編譯出來的乾淨 jar 檔
    val inputJar = tasks.jar.get().archiveFile.get().asFile
    injars(inputJar)

    // 輸出：生出一顆檔名帶有 "-obf" 的混淆版 jar 檔
    val outputJar = file("${layout.buildDirectory.get().asFile}/libs/${base.archivesName.get()}-${project.version}-obf.jar")
    outjars(outputJar)

    // 提供 Java 基礎環境庫給 ProGuard 分析 (避免報錯)
    val javaHome = System.getProperty("java.home")
    libraryjars(
        fileTree("$javaHome/jmods") { include("java.base.jmod") }
    )

    // 最重要的一步：把 Minecraft、Fabric API 等所有依賴庫餵給 ProGuard 讓它對照
    libraryjars(configurations.compileClasspath.get().files)
}

// 2. 攔截並修改 Fabric Loom 的 remapJar 任務
tasks.remapJar {
    // 確保在 remap 之前，我們的混淆任務已經執行完畢
    dependsOn(proguardTask)

    // 偷天換日：將輸入來源改成剛剛 ProGuard 吐出來的 "-obf.jar"
    inputFile.set(file("${layout.buildDirectory.get().asFile}/libs/${base.archivesName.get()}-${project.version}-obf.jar"))
}