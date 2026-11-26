package io.github.dshfolk.minimalshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.ExtensionShellTabs
import me.bmax.apatch.dsh.ext.ShellHost
import me.bmax.apatch.dsh.ext.seam.RuntimeState

















































/** 顶栏：右上角两个圆形描边按钮（终端快捷入口 + 设置菜单）。 */
@Composable
fun TopBar(shellHost: ShellHost, palette: MinimalPalette) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircleIconButton(
                label = ">_",
                palette = palette,
                onClick = { shellHost.navigate(ExtensionShellTabs.TERMINAL) },
            )
            Box {
                CircleIconButton(
                    label = "\u2699",
                    palette = palette,
                    onClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("终端") },
                        onClick = { menuOpen = false; shellHost.navigate(ExtensionShellTabs.TERMINAL) },
                    )
                    DropdownMenuItem(
                        text = { Text("扩展管理") },
                        onClick = { menuOpen = false; shellHost.navigate(ExtensionShellTabs.PLUGINS) },
                    )
                    DropdownMenuItem(
                        text = { Text("设置") },
                        onClick = { menuOpen = false; shellHost.navigate(ExtensionShellTabs.SETTINGS) },
                    )
                    DropdownMenuItem(
                        text = { Text("切回默认界面") },
                        onClick = { menuOpen = false; shellHost.resetToDefaultShell() },
                    )
                }
            }
        }
    }
}

/** 44dp 圆形描边按钮。 */
@Composable
fun CircleIconButton(label: String, palette: MinimalPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.buttonBackground)
            .border(1.dp, palette.buttonStroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = palette.title, fontSize = 14.sp)
    }
}

/** 居中状态区：进度条 + 阶段耗时 + 标题 + 状态文案。 */
@Composable
fun StatusCore(
    state: RuntimeState,
    elapsed: Int,
    palette: MinimalPalette,
    accent: Color,
) {
    val stage = when {
        state.running -> "已就绪"
        state.installing -> "安装中"
        state.installed -> "启动中"
        else -> "未安装"
    }

    // 进度条：安装时有确定进度，启动时只能给不确定态（容器启动没有可读的百分比）。
    if (state.installing && state.progress > 0f) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = palette.buttonStroke,
        )
    } else if (!state.running) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = palette.buttonStroke,
        )
    }

    Spacer(Modifier.height(10.dp))
    Text(
        text = if (state.running) stage else "$stage · ${elapsed}s",
        color = palette.secondary,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )

    Spacer(Modifier.height(14.dp))
    Text(
        text = "DSH-Folk",
        color = palette.title,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )

    Spacer(Modifier.height(6.dp))
    Text(
        text = state.message.ifBlank {
            when {
                state.running -> "服务已就绪"
                state.installed -> "正在启动容器…"
                else -> "还没有安装运行时"
            }
        },
        color = palette.body,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 实时滚动日志视窗。
 *
 * ## 为什么先 recentLogs 再 observeLogs
 *
 * 只订阅的话，从进入页面那一刻开始才有日志 —— 而用户很可能是在启动**中途**
 * 切回来的，前面那些行已经过去了，他会看到一片空白然后突然开始滚。
 * 先铺底再订阅，衔接处才不会断。
 */
@Composable
fun LogWindow(host: DshExtensionHost, palette: MinimalPalette, config: MinimalConfig) {
    val runtime = host.seam.runtime
    val lines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    val fontSize = when (config.logTextSize) {
        0 -> 10.sp
        2 -> 13.sp
        else -> 11.sp
    }
    val accent = config.accent.color(androidx.compose.foundation.isSystemInDarkTheme())

    // 铺底 + 订阅。DisposableEffect 保证离开页面时退订，不留监听器。
    DisposableEffect(Unit) {
        lines.clear()
        runCatching { lines.addAll(runtime.recentLogs(80)) }
        val unsubscribe = runtime.observeLogs { line ->
            // 只留最近 200 行：启动日志可能几千行，全留着会让 LazyColumn 越来越重，
            // 而用户能看到的只有窗口内那十几行。
            if (lines.size > 200) lines.removeRange(0, lines.size - 200)
            lines.add(line)
        }
        onDispose { unsubscribe() }
    }

    // 自动滚到底。用户手动往上翻时**不要**抢 —— 那样他根本看不了历史。
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && !listState.isScrollInProgress) {
            runCatching { listState.animateScrollToItem(lines.lastIndex) }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.logSurface)
            .border(1.dp, palette.buttonStroke, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "等待日志…",
                color = palette.secondary,
                fontSize = fontSize,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            LazyColumn(state = listState) {
                items(lines) { line ->
                    Text(
                        text = "> " + line,
                        color = if (line.startsWith("!")) accent else palette.logText,
                        fontSize = fontSize,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 状态操作区：按当前状态给 1–2 个按钮。 */
@Composable
fun ActionRow(
    state: RuntimeState,
    palette: MinimalPalette,
    onStart: () -> Unit,
    onEnter: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.running -> {
                PillButton("进入对话", palette, primary = true, onClick = onEnter)
                PillButton("重启服务", palette, primary = false, onClick = onStart)
            }
            state.installed -> {
                PillButton("重试启动", palette, primary = true, onClick = onStart)
            }
            else -> {
                Text(
                    text = "请先在设置里安装运行时",
                    color = palette.secondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** 圆角胶囊按钮。 */
@Composable
fun PillButton(text: String, palette: MinimalPalette, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) palette.title else palette.buttonBackground)
            .border(1.dp, palette.buttonStroke, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = if (primary) palette.background else palette.body,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
