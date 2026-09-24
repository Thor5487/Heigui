import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String
val isPrivateBuild = (project.findProperty("isPrivate") as? String)?.toBoolean() ?: false

// ====================================================
// 🏷️ Release / Beta 判定
// ====================================================
// 執行 git 指令並取回輸出。失敗 (git 不存在、非 git 目錄、指令本身回非 0) 一律回 null
fun gitOutput(vararg args: String): String? = try {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(rootDir)
        // 必須用 DISCARD 而不是 redirectErrorStream(true)，否則 git 的錯誤訊息會混進輸出
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && output.isNotEmpty()) output else null
} catch (e: Exception) {
    null
}

val modVersionBase = property("mod_version") as String
val mcVersion = property("minecraft_version") as String

// HEAD 剛好在 v<mod_version> 這個 tag 上才算正式版，否則都是測試版。
// 推 tag 時 CI 會 checkout 該 tag，HEAD 自然落在 tag 上，所以會自動判定成 release。
// -Prelease=true 是給 git 不可用時 (或想手動打正式包) 的逃生門。
val isReleaseBuild = project.hasProperty("release") ||
        gitOutput("describe", "--exact-match", "--tags", "HEAD") == "v$modVersionBase"

val commitHash = gitOutput("rev-parse", "--short", "HEAD") ?: "unknown"
// 上一個 tag 之後累積了幾個 commit，當作 beta 編號
val betaNumber = gitOutput("rev-list", "--count", "HEAD", "--not", "--tags")?.toIntOrNull() ?: 0

val buildChannel = if (isReleaseBuild) "release" else "beta"

// 正式版: 1.3.9
// 測試版: 1.3.9-beta.3 (合法 semver 2.0.0，Fabric 能解析，且排序上小於 1.3.9)
// commit hash 不放進版本號，改放在 build_type.properties 裡，檔名才不會太長
val modVersion = if (isReleaseBuild) {
    modVersionBase
} else {
    "$modVersionBase-beta.$betaNumber"
}

// 利用 Kotlin 的字串插值，把兩個版本號用 "-" 串接起來
version = "$modVersion-$mcVersion"

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

    // Optional terminal solver integrations. These are available at compile time only
    // and are never bundled into Heigui or required at runtime.
    compileOnly("maven.modrinth:odin:${property("odin_version")}")
    compileOnly("maven.modrinth:noammaddons:${property("noammaddons_version")}")
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
        // 用不含 channel 的基礎版號當資料夾名，否則每個 beta commit (hash 不同) 都會生一個新資料夾
        destinationDirectory.set(layout.buildDirectory.dir("libs/$modVersionBase-$mcVersion"))
    }

    processResources {
        // 這些都要宣告成 input，否則 commit 之後 Gradle 會判定 UP-TO-DATE，
        // jar 裡就留著上一次的舊 hash 與舊 channel
        inputs.property("isPrivateBuild", isPrivateBuild)
        inputs.property("modVersion", modVersion)
        inputs.property("buildChannel", buildChannel)
        inputs.property("commitHash", commitHash)

        filesMatching("fabric.mod.json") {
            // 用組好的 modVersion 覆蓋掉 gradle.properties 裡的原始 mod_version
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
// 🚀 一鍵雙 Build 整合任務 (顯示於 IDE 的 Tasks -> build 內)
// ====================================================
tasks.register("buildAllVersions") {
    group = "build"
    description = "Automatically cleans and builds both Public and Private versions."

    val rootDirFile = project.rootDir
    val rootDirPath = rootDirFile.absolutePath

    doLast {
        // 判斷系統環境來決定執行 gradlew 還是 gradlew.bat
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val gradlew = if (isWindows) "$rootDirPath\\gradlew.bat" else "$rootDirPath/gradlew"
        println("============================================")
        println("🔨 [1/2] Building PRIVATE Version...")
        println("============================================")

        // 不執行 clean（保留快取），直接打包 Private 版
        ProcessBuilder(gradlew, "build", "-PisPrivate=true")
            .directory(rootDirFile)
            .inheritIO()
            .start()
            .waitFor()
        println("============================================")
        println("🔨 [2/2] Building PUBLIC Version...")
        println("============================================")

        // 使用純 Kotlin/JVM 的 ProcessBuilder 呼叫指令，完美避開 Gradle 語法報錯
        ProcessBuilder(gradlew, "build", "-PisPrivate=false")
            .directory(rootDirFile) // 設定執行目錄為專案根目錄
            .inheritIO() // 🌟 關鍵魔法：讓子程序的打包進度直接印在你的 IDE 控制台！
            .start()
            .waitFor() // 等待打包完成再進行下一步


        println("============================================")
        println("✅ Done! Check your build/libs folder.")
        println("============================================")
    }
}
