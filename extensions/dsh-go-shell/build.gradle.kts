@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.lib)
    // 只应用 compose 编译器插件，**不要**再应用 org.jetbrains.kotlin.android：
    // AGP 9 起 android 插件已内置 Kotlin 支持，重复应用会被直接判错。
    alias(libs.plugins.kotlin.compose.compiler)
}

android {
    namespace = "io.github.ipf_sinon.dshgoshell"

    // TODO: 这两个值必须与宿主一致（宿主根 build.gradle.kts 的 androidCompileSdkVersion
    //  = 37、androidMinSdkVersion = 26）。复合构建不会把宿主的 extra 传进来，所以只能
    // 手抄；宿主升 targetSdk 时这里跟着改，否则可能出现「本地能编、装上不认」。
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    // 刻意不写 compileOptions / kotlin.jvmTarget：JVM 目标交给 AGP 的默认值统一决定，
    // 免得 Java 与 Kotlin 两边设成不同版本而报 "Inconsistent JVM-target"。
    // 产物出去会被 d8 转成 dex，class 文件版本不影响运行。
}

dependencies {
    // 版本号是占位：settings.gradle.kts 的 includeBuild dependencySubstitution 会把它
    // 替换成宿主 :ext-api 项目。所以这里写什么版本都不会去网络上下载。
    implementation("me.bmax.apatch:ext-api:1.0")

    // Compose 依赖必须自己声明：ext-api 里 Compose 是 implementation，不会传递给扩展。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.kotlinx.coroutines.core)
}
