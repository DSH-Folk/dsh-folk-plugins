@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * 桌宠扩展。
 *
 * ## 为什么不用本地抠图引擎
 *
 * 洛天依那个插件带了 ONNX 抠图（三档精度）。在宿主里做同样的事要打包几十到几百 MB
 * 的模型，而扩展的 .dshx 是 zip(manifest + classes.dex) —— 模型塞不进去，也不该塞。
 * 这里只做「选图 → 缩放 → 存到扩展私有目录」，抠图交给用户自己准备好透明 PNG。
 */
plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.kotlin.compose.compiler)
}

android {
    namespace = "io.github.dshfolk.desktoppet"
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
    compileOnly("me.bmax.apatch:ext-api:1.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // rememberLauncherForActivityResult / ActivityResultContracts：
    // 换图要拉起系统图片选择器，这是唯一不需要存储权限的做法。
    implementation(libs.androidx.activity.compose)
}
