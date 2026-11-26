package io.github.dshfolk.minimalshell

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.ShellHost




















































/**
 * 极简启动屏本体。
 *
 * ## 自动启动与自动进 WebUI
 *
 * 两个"自动"都必须防重复：
 *
 * - **自动启动**：只在「已安装、未运行、且这次进页面还没试过」时调一次。
 *   不加这个判断的话，每次重组（比如日志来一行触发）都会再调一次 start()，
 *   而 start 会去抢启动锁，用户看到的是启动被反复打断。
 *
 * - **自动进 WebUI**：用 `autoOpened` 标记。用户从 WebUI 按返回键回到这里时，
 *   服务仍然是 running —— 如果不记标记，会立刻又把他弹回 WebUI，形成死循环，
 *   用户根本回不到启动屏。标记只在「服务从非运行变为运行」时重置。
 */
@Composable
fun MinimalHome(
    shellHost: ShellHost,
    host: DshExtensionHost,
    config: MinimalConfig,
    palette: MinimalPalette,
) {
    val runtime = host.seam.runtime
    // 初始值取一次快照，之后靠 observe 增量更新。
    // 只订阅不取快照的话，进页面到第一次状态变化之间会显示"未安装" —— 那一瞬间
    // 的误判会让用户以为运行时丢了。
    var state by remember { mutableStateOf(runtime.state()) }

    // 接管外壳后，宿主首页（HomeDsh）没有机会执行 —— 而 attach 原本是在那里调的。
    // 不 attach 的话 runtime.state() 读到的是一份未绑定的快照：installed 恒 false、
    // 端口恒默认值。真机表现就是「未安装」，而容器其实装好了。
    //
    // attach 是幂等的（宿主在 Application.onCreate 里也调过），这里补一次没有副作用。
    LaunchedEffect(Unit) {
        runCatching { runtime.attach(host.appContext) }
        // attach 之后再取一次：attach 会刷新宿主事实（installed 等），
        // 不重取的话界面还停在 attach 之前那份快照上。
        state = runtime.state()
    }
    var elapsed by remember { mutableStateOf(0) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // 订阅运行时状态。回调在宿主主线程上，直接写 Compose state 是安全的。
    DisposableEffect(Unit) {
        val unsubscribe = runtime.observe { s -> state = s }
        onDispose { unsubscribe() }
    }

    // 启动耗时秒表：只在「已安装但没就绪」期间走。
    //
    // 条件是 installed && !running，**不是**单纯 !running —— 后者在"没装"时也会
    // 一直计数，用户看到的是「未安装 · 14s」，像在等一个永远不会来的东西。
    LaunchedEffect(state.installed, state.running, state.installing) {
        elapsed = 0
        if (!state.installed || state.running) return@LaunchedEffect
        while (!state.running && elapsed < 600) {
            delay(1000)
            elapsed++
        }
    }

    // 自动启动：只试一次。
    var autoStartTried by remember { mutableStateOf(false) }
    LaunchedEffect(state.installed, state.running, config.autoStart) {
        if (!config.autoStart) return@LaunchedEffect
        if (autoStartTried) return@LaunchedEffect
        if (!state.installed) return@LaunchedEffect
        if (state.running || state.installing) return@LaunchedEffect
        autoStartTried = true
        runCatching { runtime.start(host.appContext) }
            .onFailure { host.log("自动启动失败：" + (it.message ?: it.javaClass.simpleName)) }
    }

    // 自动进 WebUI：只在「从非运行变为运行」那一次触发。
    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(state.running, config.autoOpenWebUi) {
        if (!config.autoOpenWebUi) return@LaunchedEffect
        if (!state.running) {
            // 服务停了：下次再起来时应该重新获得一次自动进入的机会。
            autoOpened = false
            return@LaunchedEffect
        }
        if (autoOpened) return@LaunchedEffect
        autoOpened = true
        runCatching { shellHost.openWebUi() }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
        ) {
            TopBar(shellHost = shellHost, palette = palette)

            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StatusCore(
                    state = state,
                    elapsed = elapsed,
                    palette = palette,
                    accent = config.accent.color(androidx.compose.foundation.isSystemInDarkTheme()),
                )

                Spacer(Modifier.height(20.dp))

                if (config.showLogs) {
                    LogWindow(host = host, palette = palette, config = config)
                }

                errorText?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = palette.secondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(16.dp))
                ActionRow(
                    state = state,
                    palette = palette,
                    onStart = {
                        errorText = null
                        runCatching { runtime.start(host.appContext) }
                            .onFailure { errorText = it.message ?: "启动失败" }
                    },
                    onEnter = { runCatching { shellHost.openWebUi() } },
                )
            }
        }
    }
}
