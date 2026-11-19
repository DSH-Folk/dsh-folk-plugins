plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "me.bmax.apatch.ext.browserbridge"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}

dependencies {
    // 契约来自宿主仓库（复合构建 substitution，见 settings.gradle.kts）。
    // 不引任何 Android 组件库：扩展要打进 .dshx 的只有一个 classes.dex，
    // 依赖越少越好 —— Compose / AppCompat 之类会被整个打进去。
    compileOnly("me.bmax.apatch:ext-api:1.0")
}
