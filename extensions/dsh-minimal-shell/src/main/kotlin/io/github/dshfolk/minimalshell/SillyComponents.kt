package io.github.dshfolk.minimalshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
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
import me.bmax.apatch.dsh.ext.seam.RuntimeState

/**
 * SillyDroid 风格的视觉组件集。
 *
 * ## 尺寸与圆角出处
 *
 * 全部对齐 SillyDroid 的 `dimens.xml` 与 `activity_main.xml`：
 * 悬浮日志面板 20dp 圆角 + 1dp 描边、气泡 56dp 圆形、顶栏按钮 44dp 圆形描边。
 * 这些数字不是随便挑的 —— 20dp 圆角在深色下刚好让描边形成一圈细亮线，
 * 小于 16 就看不出来，大于 24 会显得像对话框而不是卡片。
 */

/** 顶栏：只放一个圆形描边设置齿轮（对齐 SillyDroid bootstrapTopActionBar）。 */
@Composable
fun SillyTopBar(palette: MinimalPalette, onSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(Modifier.align(Alignment.CenterEnd)) {
            CircleIconButton("⚙", palette, onSettings)
        }
    }
}

/** 44dp 圆形描边按钮（SillyDroid sillydroid_overlay_icon_button_size）。 */
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
        Text(label, color = palette.title, fontSize = 16.sp)
    }
}

/**
 * 居中启动区（对齐 SillyDroid bootstrapOverlay 的层次）。
 *
 * 顺序严格照它：进度条 → 阶段+耗时 → 标题 → 状态文案。这个顺序是有讲究的 ——
 * 进度条在最上是"正在进行"的第一眼信号，标题在中间是身份确认，
 * 状态文案在最下是细节。倒过来（标题在最上）会让每次看都要先略过身份再找进度。
 */
@Composable
fun SillyStatusCore(
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

    // 进度条：安装时有确定进度，其余给不确定态（容器启动没有可读百分比）。
    // 未安装时**不画** —— 画一条永远不动的进度条比不画更像卡死。
    if (state.installing && state.progress > 0f) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(0.62f).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = palette.buttonStroke,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = (state.progress * 100).toInt().toString() + "%",
            color = palette.secondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    } else if (state.installed && !state.running) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.62f).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = palette.buttonStroke,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stage + " · " + elapsed + "s",
            color = palette.secondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    } else if (state.running) {
        Spacer(Modifier.height(11.dp))
        Text(
            text = stage,
            color = accent,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    } else {
        Spacer(Modifier.height(11.dp))
        Text(
            text = stage,
            color = palette.secondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }

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
 * 内嵌的滚动日志视窗（启动屏中央那块）。
 *
 * 与悬浮面板的区别：这个始终显示在启动屏上，高度有限、只读；悬浮面板可展开、
 * 能复制、能暂停滚底。SillyDroid 也是两块 —— 中央一块 `tavernLogTail` 单行提示，
 * 右下角一块完整面板。这里把中央那块做成了多行滚动（比单行有用得多）。
 */
@Composable
fun SillyLogWindow(
    lines: List<String>,
    palette: MinimalPalette,
    accent: Color,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val size = when (fontSize) { 0 -> 10.sp; 2 -> 13.sp; else -> 11.sp }

    // 自动滚到底，但用户手动往上翻时不抢。
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && !listState.isScrollInProgress) {
            runCatching { listState.animateScrollToItem(lines.lastIndex) }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp, max = 190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.logSurface)
            .border(1.dp, palette.buttonStroke, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (lines.isEmpty()) {
            Text("等待日志…", color = palette.secondary, fontSize = size, fontFamily = FontFamily.Monospace)
        } else {
            LazyColumn(state = listState) {
                items(lines) { line ->
                    Text(
                        text = "> " + line,
                        color = if (line.startsWith("!") || line.startsWith("[err")) accent else palette.logText,
                        fontSize = size,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 悬浮日志面板（对齐 SillyDroid floatingLogsPanel）。
 *
 * 20dp 圆角、1dp 描边、深一档的底色 —— 这三样一起才让它看起来是"浮在内容之上"，
 * 而不是页面的一部分。
 */
@Composable
fun SillyLogPanel(
    lines: List<String>,
    palette: MinimalPalette,
    accent: Color,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onScrollToBottom: () -> Unit,
) {
    val listState = rememberLazyListState()
    var pinned by remember { mutableStateOf(true) }

    LaunchedEffect(lines.size, pinned) {
        if (pinned && lines.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(lines.lastIndex) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.panelBackground)
            .border(1.dp, palette.panelStroke, RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            // 标题行
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "运行日志",
                    color = palette.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                PanelAction("复制", palette, onCopy)
                PanelAction(if (pinned) "已跟随" else "跟到底", palette) {
                    pinned = !pinned
                    if (pinned) onScrollToBottom()
                }
                PanelAction("关闭", palette, onClose)
            }
            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.panelSurface)
                    .padding(10.dp),
            ) {
                if (lines.isEmpty()) {
                    Text("还没有日志", color = palette.secondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                } else {
                    LazyColumn(state = listState) {
                        items(lines) { line ->
                            Text(
                                text = line,
                                color = if (line.startsWith("!") || line.startsWith("[err")) accent else palette.logContent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 面板标题行的小按钮。 */
@Composable
private fun PanelAction(label: String, palette: MinimalPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = palette.secondary, fontSize = 11.sp)
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
