package io.github.dshfolk.minimalshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.ExtensionShellTabs
import me.bmax.apatch.dsh.ext.ShellHost

/**
 * 极简外壳的根。
 *
 * ## 两类界面，一条分界线
 *
 * 外壳接管的是**导航**，不是全部页面。`shellHost.currentTabStates()` 告诉我们用户
 * 现在在哪一页：
 *
 * - `home`：渲染我们自己的极简启动屏；
 * - 其它：把宿主传进来的 `hostContent` 原样渲染，只在顶部加一条返回栏。
 *
 * 这条界线必须守住。把宿主页面也一起重画的话，用户会失去终端、扩展管理、设置
 * 等全部原生功能 —— 那等于用"极简"把 App 关进一个空房间。
 *
 * ## `hostContent` 是参数，不是回调
 *
 * `ShellContent` 的签名是 `(ShellHost, @Composable (Modifier) -> Unit) -> Unit`。
 * 第二个参数**就是**宿主当前页面的内容，调用它才会渲染出来。它被设计成
 * composable 而不是函数，是为了让我们能决定放在哪、给多大空间。
 */
@Composable
fun MinimalShellScreen(
    shellHost: ShellHost,
    host: DshExtensionHost,
    hostContent: @Composable (Modifier) -> Unit,
) {
    val palette = minimalPalette()
    val currentTab by shellHost.currentTabStates().collectAsStateWithLifecycle()
    // 直接从 holder 读：用户在配置面板改完，回到 home 立刻生效。
    val config = remember(currentTab) { MinimalConfigHolder.read() }

    // ## 为什么 hostContent 必须**始终**在组合树里
    //
    // hostContent 里是宿主的 DestinationsNavHost(navController)。它一旦没被组合，
    // navController.graph 就是未初始化的；而 shellHost.navigate() 内部要读
    // navController.graph.startDestinationId —— 瞬间抛 IllegalStateException。
    //
    // 真机表现：极简壳在首页点「设置」或「终端」直接闪退。
    //
    // 第一版写的是 if (HOME) MinimalHome else { ...; hostContent(...) }，
    // 于是 HOME 那一支里 hostContent 完全脱离了组合树 —— NavHost 从未组合，
    // graph 自然从未初始化。这不是"延迟一帧"的问题，是**永远**没初始化过。
    //
    // 现在改成：NavHost 永远挂在底层，极简启动屏作为浮层盖在它上面。
    // 切到子页面时把浮层收起来，露出下面的宿主页面。
    Box(Modifier.fillMaxSize().background(palette.background)) {
        // 底层：宿主页面树。永远存在，只是被浮层盖住时看不见。
        hostContent(Modifier.fillMaxSize())

        if (currentTab == ExtensionShellTabs.HOME) {
            // 浮层：极简启动屏。用不透明底色盖住底层，视觉上与"只有启动屏"一致。
            Box(Modifier.fillMaxSize().background(palette.background)) {
                MinimalHome(
                    shellHost = shellHost,
                    host = host,
                    config = config,
                    palette = palette,
                )
            }
        } else {
            // 子页面：露出宿主页面，只加一条返回栏。
            Column(Modifier.fillMaxSize()) {
                SubPageBar(
                    title = when (currentTab) {
                        ExtensionShellTabs.TERMINAL -> "终端"
                        ExtensionShellTabs.PLUGINS -> "扩展"
                        ExtensionShellTabs.SETTINGS -> "设置"
                        else -> currentTab
                    },
                    palette = palette,
                    onBack = { shellHost.navigate(ExtensionShellTabs.HOME) },
                )
            }
        }
    }
}

/** 宿主子页面顶部的返回栏。 */
@Composable
private fun SubPageBar(title: String, palette: MinimalPalette, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("← 返回", color = palette.body, fontSize = 14.sp)
        }
        Text(
            text = title,
            color = palette.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
