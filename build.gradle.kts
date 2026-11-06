plugins {
    // 只在根工程声明、**不应用**。
    // com.android.library 与 com.android.application 是同一份 AGP 产物，子模块再
    // alias 时如果根里没先声明，会报 "already on the classpath with an unknown version"。
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
}
