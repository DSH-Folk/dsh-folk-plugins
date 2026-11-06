@file:Suppress("UnstableApiUsage")

// 这是一个 **复合构建（composite build）**，不是一个发布 artifact 的库。
//
// 为什么不去发 me.bmax.apatch:ext-api 到 Maven：契约还在快速变，发版等于给自己制造
// 版本对齐的麻烦。用 includeBuild 直接引用宿主那份源码，宿主改了 API，扩展这边下次
// 编译就跟着变 —— 编译期就能发现不兼容，而不是等用户装上之后崩在运行期。
//
// 前提：DSH-Folk 仓库与本仓库**同级**（CI 里 clone 到 ../DSH-Folk）。
// 本地开发时把 DSH-Folk 放在兄弟目录即可，不需要改任何路径常量。
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_PROJECT
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "dsh-folk-plugins"

includeBuild("../DSH-Folk") {
    dependencySubstitution {
        // 把坐标 me.bmax.apatch:ext-api 映射到宿主仓库里的 :ext-api 项目。
        // 扩展模块因此可以照常写 implementation("me.bmax.apatch:ext-api:1.0")
        // —— 版本号只是个占位，解析前就被替换掉了。
        substitute(module("me.bmax.apatch:ext-api")).using(project(":ext-api"))
    }
}

include(":extensions:dsh-go-shell")
