package io.github.dshfolk.minimalshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * 配置面板。
 *
 * ## 不套滚动容器
 *
 * 宿主把配置面板放进它自己的可滚动区域里。这里再套一层 LazyColumn / verticalScroll
 * 会与外层争抢滚动手势（真机上表现为"滑不动"或"滑动跳一大截"）。所以只用普通 Column。
 *
 * ## 每个开关都带一句解释
 *
 * 光写"点开自动启动"用户不知道它跟什么有关；补一句"进页面就会拉起容器"才说得清。
 * 这两句在配置面板里比在文档里有用得多 —— 用户不会去读文档。
 */
@Composable
fun MinimalShellConfigPanel() {
    // 面板拿不到 host 参数，但 MinimalConfigHolder 已在 onLoad 时绑定过 store。
    // 读 holder 而不是自己 new 一个，改完立刻对启动屏可见。
    var config by remember { mutableStateOf(MinimalConfigHolder.read()) }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToggleRow(
            title = "点开自动启动容器",
            detail = "进入极简启动屏时，如果容器已安装但没在跑，自动拉起它。",
            checked = config.autoStart,
            onCheckedChange = { config = config.copy(autoStart = it); MinimalConfigHolder.write(config) },
        )
        ToggleRow(
            title = "就绪后自动进入对话",
            detail = "服务起来后自动打开 dsh 网页。从网页按返回键回到启动屏时不会再次弹出。",
            checked = config.autoOpenWebUi,
            onCheckedChange = { config = config.copy(autoOpenWebUi = it); MinimalConfigHolder.write(config) },
        )
        ToggleRow(
            title = "显示实时日志",
            detail = "在启动屏中央滚动显示容器的启动输出，能看出它是在推进还是卡住了。",
            checked = config.showLogs,
            onCheckedChange = { config = config.copy(showLogs = it); MinimalConfigHolder.write(config) },
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = "日志强调色",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccentChoice.entries.forEach { choice ->
                AccentChip(
                    label = when (choice) {
                        AccentChoice.GREEN -> "终端绿"
                        AccentChoice.AMBER -> "琥珀"
                        AccentChoice.NEUTRAL -> "冷灰白"
                    },
                    selected = config.accent == choice,
                    onClick = { config = config.copy(accent = choice); MinimalConfigHolder.write(config) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "日志字号",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("小", "中", "大").forEachIndexed { index, label ->
                AccentChip(
                    label = label,
                    selected = config.logTextSize == index,
                    onClick = { config = config.copy(logTextSize = index); MinimalConfigHolder.write(config) },
                )
            }
        }
    }
}

/** 一行开关：标题 + 说明 + Switch。 */
@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = detail,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 可选中的胶囊。 */
@Composable
private fun AccentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text = label, fontSize = 12.sp)
    }
}
