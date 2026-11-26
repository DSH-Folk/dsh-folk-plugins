package io.github.dshfolk.desktoppet

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 桌宠配置面板。
 *
 * ## 不套滚动容器
 *
 * 宿主把面板放进它自己的可滚动区域，这里再套一层会争抢手势。
 *
 * ## apiKey 用密码框
 *
 * 不是为了防谁，而是避免它出现在截屏里 —— 用户报障时经常截图设置页。
 */
@Composable
fun PetConfigPanel() {
    var config by remember { mutableStateOf(PetConfigHolder.read()) }
    val store = PetConfigHolder.store()
    var apiKey by remember { mutableStateOf(store?.apiKey.orEmpty()) }
    var lexiconStatus by remember { mutableStateOf<String?>(null) }

    fun persist(next: PetConfig) {
        config = next
        PetConfigHolder.write(next)
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {

        // ── 显示位置 ──
        SectionTitle("显示位置")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("仅网页内", config.display == PetDisplay.WEBUI) {
                persist(config.copy(display = PetDisplay.WEBUI))
            }
            ChoiceChip("仅应用内", config.display == PetDisplay.HOST) {
                persist(config.copy(display = PetDisplay.HOST))
            }
            ChoiceChip("全局悬浮窗", config.display == PetDisplay.OVERLAY) {
                persist(config.copy(display = PetDisplay.OVERLAY))
            }
        }
        Hint("「全局悬浮窗」需要系统悬浮窗权限，可在系统设置里给本应用授权。")

        Spacer(Modifier.height(14.dp))

        // ── 外观 ──
        SectionTitle("透明度 " + (config.opacity * 100).toInt() + "%")
        Slider(
            value = config.opacity,
            onValueChange = { persist(config.copy(opacity = it.coerceIn(0.2f, 1f))) },
            valueRange = 0.2f..1f,
        )

        Spacer(Modifier.height(8.dp))
        SectionTitle("尺寸")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PetSize.entries.forEach { size ->
                ChoiceChip(
                    label = when (size) {
                        PetSize.SMALL -> "小"
                        PetSize.MEDIUM -> "中"
                        PetSize.LARGE -> "大"
                    },
                    selected = config.size == size,
                ) { persist(config.copy(size = size)) }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 行为 ──
        SectionTitle("行为")
        ToggleRow(
            title = "跟随容器状态说话",
            detail = "容器启动或停止时，桌宠会冒一句话。",
            checked = config.reactToRuntime,
        ) { persist(config.copy(reactToRuntime = it)) }

        SectionTitle("闲置冒泡间隔 " + config.idleBubbleSeconds + " 秒")
        Slider(
            value = config.idleBubbleSeconds.toFloat(),
            onValueChange = { persist(config.copy(idleBubbleSeconds = it.toInt().coerceIn(0, 600))) },
            valueRange = 0f..300f,
        )
        Hint("设为 0 表示不主动冒泡。")

        Spacer(Modifier.height(14.dp))

        // ── 词库 ──
        SectionTitle("词库")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val file = store?.lexiconFile()
                if (file == null) { lexiconStatus = "存储未就绪"; return@TextButton }
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(PetLexicon.DEFAULT.toJson())
                    lexiconStatus = "已导出模板到 " + file.name + "，可编辑后重新导入"
                }.onFailure { lexiconStatus = "导出失败：" + (it.message ?: "未知") }
            }) { Text("导出模板") }

            TextButton(onClick = {
                val file = store?.lexiconFile()
                if (file == null || !file.isFile) {
                    lexiconStatus = "还没有词库文件，请先导出模板"
                    return@TextButton
                }
                val parsed = PetLexicon.fromJson(runCatching { file.readText() }.getOrDefault(""))
                lexiconStatus = if (parsed == null) "解析失败：请检查 JSON 格式"
                else "已导入 " + parsed.lines.size + " 个场景"
            }) { Text("重新导入") }
        }
        lexiconStatus?.let { Hint(it) }

        Spacer(Modifier.height(14.dp))

        // ── 台词模式 ──
        SectionTitle("台词来源")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("预设词库", config.speechMode == PetSpeechMode.PRESET) {
                persist(config.copy(speechMode = PetSpeechMode.PRESET))
            }
            ChoiceChip("AI 生成", config.speechMode == PetSpeechMode.AI) {
                persist(config.copy(speechMode = PetSpeechMode.AI))
            }
        }
        Hint("预设词库零消耗、即时响应。AI 模式需要下面的接口配置，失败时自动回退预设。")

        if (config.speechMode == PetSpeechMode.AI) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = config.aiBaseUrl,
                onValueChange = { persist(config.copy(aiBaseUrl = it)) },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.aiModel,
                onValueChange = { persist(config.copy(aiModel = it)) },
                label = { Text("模型名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    store?.apiKey = it
                },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.aiSystemPrompt,
                onValueChange = { persist(config.copy(aiSystemPrompt = it)) },
                label = { Text("角色设定") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

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
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 12.sp)
    }
}
