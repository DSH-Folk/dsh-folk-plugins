@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * 浏览器桥扩展。
 *
 * ## 插件声明只有 agp.lib，没有 kotlin.android
 *
 * AGP 9 起 android 插件已内置 Kotlin 支持，再应用 `org.jetbrains.kotlin.android`
 * 会被直接判错（提示 Remove the org.jetbrains.kotlin.android plugin）。
 * 这条与宿主的 :ext-api 一致 —— 那边是踩过 CI 才对的。
 *
 * ## 不引 Compose
 *
 * 这个扩展没有界面，只有 onLoad 里两次能力设置。引 Compose 会让 .dshx 里的
 * classes.dex 凭空大一圈，而宿主打包时不做裁剪。
 */
plugins {
    alias(libs.plugins.agp.lib)
}

val androidCompileSdkVersion: Int by rootProject.extra
val androidMinSdkVersion: Int by rootProject.extra

android {
    namespace = "me.bmax.apatch.ext.browserbridge"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
    }
}

// 与 :app / :ext-api 一致的 JDK/Kotlin 目标：产物会进同一个 dex，
// 目标版本不一致会在合并时报「class file has wrong version」。
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    // 契约来自宿主仓库（复合构建 substitution，见 settings.gradle.kts）。
    // compileOnly：宿主运行时已经带着这些类，打进 .dshx 只会重复。
    compileOnly("me.bmax.apatch:ext-api:1.0")
}
