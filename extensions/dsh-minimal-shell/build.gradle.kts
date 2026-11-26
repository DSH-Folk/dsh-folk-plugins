@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * 极简启动壳扩展。
 *
 * ## 与浏览器桥的差异：这个扩展**要** Compose
 *
 * 它接管整个外壳，界面全部是 Compose 写的。Compose 依赖必须自己声明 ——
 * `:ext-api` 里 Compose 是 `implementation`，不会传递给扩展模块。
 *
 * ## 插件声明只有 agp.lib
 *
 * AGP 9 起 android 插件已内置 Kotlin 支持，再应用 `org.jetbrains.kotlin.android`
 * 会被直接判错。compose 编译器插件则必须显式应用。
 */
plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.kotlin.compose.compiler)
}

android {
    namespace = "io.github.dshfolk.minimalshell"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }
}

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
    // compileOnly：宿主运行时已带着这些类，打进 .dshx 只会重复。
    compileOnly("me.bmax.apatch:ext-api:1.0")

    // Compose 依赖必须自己声明（ext-api 里是 implementation，不传递）。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.kotlinx.coroutines.core)

    // collectAsStateWithLifecycle：StateFlow 在 Compose 里的标准收集方式。
    // 直接用 collectAsState 会在页面不可见时继续收集，浪费电且可能更新已销毁的界面。
    implementation(libs.androidx.lifecycle.runtime.compose)
}
