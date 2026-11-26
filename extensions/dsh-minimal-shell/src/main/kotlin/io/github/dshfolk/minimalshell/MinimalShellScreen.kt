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

    if (currentTab == ExtensionShellTabs.HOME) {
        MinimalHome(
            shellHost = shellHost,
            host = host,
            config = config,
            palette = palette,
        )
    } else {
        Column(Modifier.fillMaxSize().background(palette.background)) {
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
            // 宿主页面：填满剩余空间，我们不加任何装饰。
            hostContent(Modifier.fillMaxSize())
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
