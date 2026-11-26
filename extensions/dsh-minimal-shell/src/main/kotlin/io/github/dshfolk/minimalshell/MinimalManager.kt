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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.ExtensionShellTabs
import me.bmax.apatch.dsh.ext.ShellHost

/**
 * 极简壳自己的管理中心（对齐 SillyDroid 的多 Tab 设置页）。
 *
 * ## 为什么要重写，而不是跳到宿主的设置页
 *
 * 宿主的设置页有四五十个开关（主题、备份、内核、无障碍…），对一个只想用 dsh 对话的
 * 用户来说全是噪音。这里只放他真正会改的几项，加一个「打开完整设置」的出口。
 *
 * ## Tab 设计
 *
 * 五个 Tab 而不是一个长列表：每类内容性质不同（开关 / 只读日志 / 嵌入页面 / 关于），
 * 混在一起滚动找起来很累。Tab 用等宽字体做标签，跟启动屏的极客调性一致。
 */
private enum class ManagerTab(val label: String) {
    BASIC("基础"),
    LOGS("日志"),
    TERMINAL("终端"),
    PLUGINS("扩展"),
    ABOUT("关于"),
}

@Composable
fun MinimalManager(
    shellHost: ShellHost,
    host: DshExtensionHost,
    palette: MinimalPalette,
    onBack: () -> Unit,
    onOpenHostTab: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(ManagerTab.BASIC) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // 顶栏：返回 + 标题
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("←", color = palette.body, fontSize = 16.sp)
            }
            Spacer(Modifier.width(4.dp))
            Text("管理", color = palette.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        // Tab 条
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (t in ManagerTab.entries) {
                val selected = tab == t
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) palette.buttonBackground else palette.background)
                        .border(
                            1.dp,
                            if (selected) palette.panelStroke else palette.buttonStroke,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { tab = t }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = t.label,
                        color = if (selected) palette.title else palette.secondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                ManagerTab.BASIC -> BasicTab(host, palette, shellHost, onOpenHostTab)
                ManagerTab.LOGS -> LogsTab(host, palette)
                ManagerTab.TERMINAL -> EmbedTab(
                    title = "终端",
                    detail = "终端页由宿主提供。点下面的按钮切过去，返回键会回到启动屏。",
                    palette = palette,
                    onOpen = { onOpenHostTab(ExtensionShellTabs.TERMINAL) },
                )
                ManagerTab.PLUGINS -> EmbedTab(
                    title = "扩展与插件",
                    detail = "扩展管理页由宿主提供。点下面的按钮切过去。",
                    palette = palette,
                    onOpen = { onOpenHostTab(ExtensionShellTabs.PLUGINS) },
                )
                ManagerTab.ABOUT -> AboutTab(host, palette, shellHost)
            }
        }
    }
}

/** 基础：开关 + 外观 + 服务操作。 */
@Composable
private fun BasicTab(
    host: DshExtensionHost,
    palette: MinimalPalette,
    shellHost: ShellHost,
    onOpenHostTab: (String) -> Unit,
) {
    var config by remember { mutableStateOf(MinimalConfigHolder.read()) }

    fun persist(next: MinimalConfig) {
        config = next
        MinimalConfigHolder.write(next)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Card(palette) {
            ToggleRow("点开自动启动容器", "进入启动屏时，若已安装且未运行就自动拉起。", config.autoStart, palette) {
                persist(config.copy(autoStart = it))
            }
            ToggleRow("就绪后自动进入对话", "服务起来后自动打开 dsh 网页。从网页返回时不会再次弹出。", config.autoOpenWebUi, palette) {
                persist(config.copy(autoOpenWebUi = it))
            }
            ToggleRow("显示实时日志", "启动屏中央滚动显示容器输出。", config.showLogs, palette) {
                persist(config.copy(showLogs = it))
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(palette) {
            SectionLabel("日志强调色", palette)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentChoice.entries.forEach { c ->
                    Chip(
                        label = when (c) {
                            AccentChoice.GREEN -> "终端绿"
                            AccentChoice.AMBER -> "琥珀"
                            AccentChoice.NEUTRAL -> "冷灰白"
                        },
                        selected = config.accent == c,
                        palette = palette,
                    ) { persist(config.copy(accent = c)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("日志字号", palette)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("小", "中", "大").forEachIndexed { i, label ->
                    Chip(label, config.logTextSize == i, palette) {
                        persist(config.copy(logTextSize = i))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(palette) {
            SectionLabel("服务", palette)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("启动", palette, primary = true) {
                    runCatching { host.seam.runtime.start(host.appContext) }
                }
                PillButton("重启", palette, primary = false) {
                    runCatching { host.seam.runtime.hold(host.appContext) }
                    runCatching { host.seam.runtime.start(host.appContext) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(palette) {
            SectionLabel("其它", palette)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("打开完整设置", palette, primary = false) {
                    onOpenHostTab(ExtensionShellTabs.SETTINGS)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 日志：全屏实时日志 + 复制。 */
@Composable
private fun LogsTab(host: DshExtensionHost, palette: MinimalPalette) {
    val lines = remember { mutableStateListOf<String>() }
    val clipboard = LocalClipboardManager.current
    val config = MinimalConfigHolder.read()
    val accent = config.accent.color(androidx.compose.foundation.isSystemInDarkTheme())

    androidx.compose.runtime.DisposableEffect(Unit) {
        lines.clear()
        runCatching { lines.addAll(host.seam.runtime.recentLogs(200)) }
        val unsubscribe = host.seam.runtime.observeLogs { line ->
            if (lines.size > 800) lines.removeRange(0, lines.size - 800)
            lines.add(line)
        }
        onDispose { unsubscribe() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("复制全部", palette, primary = false) {
                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxSize()) {
            SillyLogWindow(
                lines = lines,
                palette = palette,
                accent = accent,
                fontSize = config.logTextSize,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 嵌入页：说明 + 跳转按钮（真正的内容由宿主 hostContent 渲染）。 */
@Composable
private fun EmbedTab(
    title: String,
    detail: String,
    palette: MinimalPalette,
    onOpen: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = palette.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(detail, color = palette.body, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        PillButton("打开" + title, palette, primary = true, onClick = onOpen)
    }
}

/** 关于：运行时信息 + 切回宿主默认界面。 */
@Composable
private fun AboutTab(host: DshExtensionHost, palette: MinimalPalette, shellHost: ShellHost) {
    val state = host.seam.runtime.state()
    val summary = shellHost.runtimeSummary()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Card(palette) {
            InfoRow("运行时版本", summary.version.ifBlank { "—" }, palette)
            InfoRow("运行方式", summary.mode.ifBlank { "—" }, palette)
            InfoRow("监听端口", if (summary.port > 0) summary.port.toString() else "—", palette)
            InfoRow("是否就绪", if (state.running) "是" else "否", palette)
        }

        Spacer(Modifier.height(12.dp))

        Card(palette) {
            Text(
                "切回宿主默认界面会停用这个外壳扩展，恢复四 Tab 布局。之后可以在扩展页重新启用。",
                color = palette.body,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            PillButton("切回宿主默认界面", palette, primary = false) {
                runCatching { shellHost.resetToDefaultShell() }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── 小组件 ──

@Composable
private fun Card(palette: MinimalPalette, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.buttonBackground)
            .border(1.dp, palette.buttonStroke, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun SectionLabel(text: String, palette: MinimalPalette) {
    Text(
        text = text,
        color = palette.title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    palette: MinimalPalette,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.title, fontSize = 13.sp)
            Text(detail, color = palette.secondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, palette: MinimalPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.logSurface else palette.background)
            .border(
                1.dp,
                if (selected) palette.panelStroke else palette.buttonStroke,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (selected) palette.title else palette.body, fontSize = 12.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String, palette: MinimalPalette) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = palette.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = palette.title, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
