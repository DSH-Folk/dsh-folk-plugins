package io.github.dshfolk.minimalshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.ExtensionShellTabs
import me.bmax.apatch.dsh.ext.ShellHost
import me.bmax.apatch.dsh.ext.seam.RuntimeState

/**
 * 极简启动屏（对齐 SillyDroid bootstrapOverlay）。
 *
 * ## 布局
 *
 * - 右上角：一个圆形描边齿轮，进极简壳自己的管理中心；
 * - 中央：进度条 / 阶段+耗时 / 标题 / 状态文案 / 滚动日志；
 * - 右下角：悬浮日志气泡（点开是完整面板）；
 * - 底部：按状态给 1–2 个操作按钮。
 *
 * ## 自动启动与自动进 WebUI
 *
 * 两个"自动"都必须防重复：
 * - **自动启动**只在「已安装、未运行、这次还没试过」时调一次。不加判断的话，
 *   每次重组（日志来一行就触发）都会再调 start()，去抢启动锁。
 * - **自动进 WebUI** 用 autoOpened 标记。用户从 WebUI 按返回键回来时服务仍 running，
 *   不记标记会立刻又把他弹回去，形成死循环。
 */
@Composable
fun MinimalHome(
    shellHost: ShellHost,
    host: DshExtensionHost,
    config: MinimalConfig,
    palette: MinimalPalette,
    onOpenManager: () -> Unit,
) {
    val runtime = host.seam.runtime
    var state by remember { mutableStateOf(runtime.state()) }
    var elapsed by remember { mutableIntStateOf(0) }
    var logOpen by remember { mutableStateOf(false) }

    val lines = remember { mutableStateListOf<String>() }
    val clipboard = LocalClipboardManager.current

    // 接管外壳后宿主首页没机会执行 —— attach 原本在那里调。
    // 不 attach 的话 state 读到的是一份未绑定快照：installed 恒 false。
    LaunchedEffect(Unit) {
        runCatching { runtime.attach(host.appContext) }
        state = runtime.state()
    }

    DisposableEffect(Unit) {
        val unsubscribe = runtime.observe { s -> state = s }
        onDispose { unsubscribe() }
    }

    // 日志：先铺底再订阅（中途切回来不会从半截开始）。
    DisposableEffect(Unit) {
        lines.clear()
        runCatching { lines.addAll(runtime.recentLogs(120)) }
        val unsubscribe = runtime.observeLogs { line ->
            if (lines.size > 400) lines.removeRange(0, lines.size - 400)
            lines.add(line)
        }
        onDispose { unsubscribe() }
    }

    // 秒表：只在「已安装但没就绪」时走。未安装时不动 —— 否则显示「未安装 · 14s」，
    // 像在等一个永远不会来的东西。
    LaunchedEffect(state.installed, state.running) {
        elapsed = 0
        if (!state.installed || state.running) return@LaunchedEffect
        while (!state.running && elapsed < 600) {
            delay(1000)
            elapsed++
        }
    }

    var autoStartTried by remember { mutableStateOf(false) }
    LaunchedEffect(state.installed, state.running, config.autoStart) {
        if (!config.autoStart || autoStartTried) return@LaunchedEffect
        if (!state.installed || state.running || state.installing) return@LaunchedEffect
        autoStartTried = true
        runCatching { runtime.start(host.appContext) }
            .onFailure { host.log("自动启动失败：" + (it.message ?: it.javaClass.simpleName)) }
    }

    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(state.running, config.autoOpenWebUi) {
        if (!config.autoOpenWebUi) return@LaunchedEffect
        if (!state.running) {
            autoOpened = false
            return@LaunchedEffect
        }
        if (autoOpened) return@LaunchedEffect
        autoOpened = true
        runCatching { shellHost.openWebUi() }
    }

    val accent = config.accent.color(androidx.compose.foundation.isSystemInDarkTheme())

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
        ) {
            SillyTopBar(palette = palette, onSettings = onOpenManager)

            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SillyStatusCore(state = state, elapsed = elapsed, palette = palette, accent = accent)

                if (config.showLogs) {
                    Spacer(Modifier.height(20.dp))
                    SillyLogWindow(
                        lines = lines,
                        palette = palette,
                        accent = accent,
                        fontSize = config.logTextSize,
                    )
                }

                Spacer(Modifier.height(16.dp))
                HomeActions(
                    state = state,
                    palette = palette,
                    onStart = { runCatching { runtime.start(host.appContext) } },
                    onRestart = {
                        runCatching { host.seam.runtime.hold(host.appContext) }
                        runCatching { runtime.start(host.appContext) }
                    },
                    onEnter = { runCatching { shellHost.openWebUi() } },
                )
            }
        }

        // 悬浮日志气泡（右下角）。只在日志面板关闭时显示。
        if (!logOpen) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(palette.panelBackground)
                    .border(1.dp, palette.panelStroke, CircleShape)
                    .clickable { logOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("≡", color = palette.logContent, fontSize = 20.sp)
            }
        }

        if (logOpen) {
            SillyLogPanel(
                lines = lines,
                palette = palette,
                accent = accent,
                onClose = { logOpen = false },
                onCopy = {
                    clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                },
                onScrollToBottom = { },
            )
        }
    }
}

/** 底部操作区：按状态给 1–2 个按钮。 */
@Composable
private fun HomeActions(
    state: RuntimeState,
    palette: MinimalPalette,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onEnter: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.running -> {
                PillButton("进入对话", palette, primary = true, onClick = onEnter)
                PillButton("重启服务", palette, primary = false, onClick = onRestart)
            }
            state.installed -> {
                PillButton("重试启动", palette, primary = true, onClick = onStart)
            }
            else -> {
                Text("请先在设置里安装运行时", color = palette.secondary, fontSize = 12.sp)
            }
        }
    }
}
