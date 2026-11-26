package io.github.dshfolk.minimalshell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 极简壳的色板。
 *
 * ## 色值出处
 *
 * 全部取自 SillyDroid 的 `values/colors.xml` 与 `values-night/colors.xml`
 * （`bootstrap_overlay_*` 那一组）。不是"看着像"的近似色 —— 那套配色的重点在于
 * 低噪点高对比：浅色下用 #F7F7F7 而非纯白（纯白在 AMOLED 上刺眼、在 LCD 上发灰），
 * 深色下用 #111827 而非纯黑（纯黑会让阴影和描边完全消失，层次塌掉）。
 *
 * ## 为什么不直接读宿主的 MaterialTheme
 *
 * 外壳的整个意义就是"跟宿主长得不一样"。跟随宿主色板的话，用户在设置里换主题，
 * 这个壳会跟着变，就失去了"极简独立界面"的定位。这里只跟随**明暗模式**，
 * 不跟随宿主的具体配色。
 */
data class MinimalPalette(
    /** 整屏背景。 */
    val background: Color,
    /** 主标题（"DSH-Folk"）。 */
    val title: Color,
    /** 正文 / 状态文案。 */
    val body: Color,
    /** 次要文字（耗时、提示）。 */
    val secondary: Color,
    /** 圆形图标按钮的底色。 */
    val buttonBackground: Color,
    /** 圆形图标按钮的描边。 */
    val buttonStroke: Color,
    /** 日志视窗底色（比背景更深/更浅一档，让它看起来是"嵌进去的"）。 */
    val logSurface: Color,
    /** 日志文字。 */
    val logText: Color,

    // ── 悬浮日志面板（SillyDroid floating_logs_* 那一组） ──
    /** 面板底色。 */
    val panelBackground: Color,
    /** 面板描边（1dp，深色下是一圈细亮线）。 */
    val panelStroke: Color,
    /** 面板内日志区底色（再深一档，形成"面板里嵌了一块屏幕"的层次）。 */
    val panelSurface: Color,
    /** 面板内日志文字。 */
    val logContent: Color,
)

/** 浅色：SillyDroid values/colors.xml。 */
private val Light = MinimalPalette(
    background = Color(0xFFF7F7F7),
    title = Color(0xFF111827),
    body = Color(0xFF374151),
    secondary = Color(0xFF6B7280),
    buttonBackground = Color(0xFFFFFFFF),
    buttonStroke = Color(0xFFE5E7EB),
    logSurface = Color(0xFFF1F5F9),
    logText = Color(0xFF111827),
    // values/colors.xml: floating_logs_panel_background #F8FAFC / stroke #D1D5DB
    panelBackground = Color(0xFFF8FAFC),
    panelStroke = Color(0xFFD1D5DB),
    panelSurface = Color(0xFFE5E7EB),
    logContent = Color(0xFF111827),
)

/** 深色：SillyDroid values-night/colors.xml。 */
private val Dark = MinimalPalette(
    background = Color(0xFF111827),
    title = Color(0xFFF8FAFC),
    body = Color(0xFFCBD5E1),
    secondary = Color(0xFF9CA3AF),
    buttonBackground = Color(0xFF1F2937),
    buttonStroke = Color(0xFF334155),
    logSurface = Color(0xFF0B1220),
    logText = Color(0xFFCBD5E1),
    // values-night/colors.xml: panel_background #111827 / stroke #334155 / surface #1F2937
    panelBackground = Color(0xFF111827),
    panelStroke = Color(0xFF334155),
    panelSurface = Color(0xFF1F2937),
    logContent = Color(0xFFE5E7EB),
)

/**
 * 强调色：用户可在配置里三选一。
 *
 * 为什么是这三个：终端绿与琥珀是经典终端配色，冷灰白是"不要任何彩色"的选项 ——
 * 有人就是不想在启动屏上看到饱和色。
 */
enum class AccentChoice {
    GREEN, AMBER, NEUTRAL;

    fun color(dark: Boolean): Color = when (this) {
        GREEN -> Color(0xFF10B981)
        AMBER -> Color(0xFFF59E0B)
        // 中性不是"无色"，而是跟随正文色 —— 深色下浅灰、浅色下深灰，
        // 保证在两种模式下都有足够对比。
        NEUTRAL -> if (dark) Color(0xFFCBD5E1) else Color(0xFF374151)
    }

    companion object {
        fun of(name: String?): AccentChoice =
            entries.firstOrNull { it.name == name } ?: GREEN
    }
}

/** 当前应使用的色板。 */
@Composable
fun minimalPalette(): MinimalPalette = if (isSystemInDarkTheme()) Dark else Light
