package io.github.dshfolk.minimalshell

import android.content.Context
import me.bmax.apatch.dsh.ext.DshExtensionHost

/**
 * 极简壳的配置。
 *
 * ## 为什么用宿主 prefs 而不是自己写 JSON
 *
 * 配置只有四个开关和一个枚举，写成 JSON 就要自己处理"文件损坏""并发写""首次默认值"。
 * 宿主 prefs 已经解决了这些，而且随扩展卸载一起清掉。
 *
 * ## 读取不能抛
 *
 * 这些值在 Composable 里读，抛异常等于整屏崩掉 —— 而崩掉的正是用户唯一的界面。
 * 所以每个读取都 runCatching 兜底到默认值。
 */
data class MinimalConfig(
    /** 进壳就自动拉起容器。 */
    val autoStart: Boolean = true,
    /** 服务就绪后自动进入对话界面。 */
    val autoOpenWebUi: Boolean = true,
    /** 显示实时滚动日志。 */
    val showLogs: Boolean = true,
    /** 强调色。 */
    val accent: AccentChoice = AccentChoice.GREEN,
    /** 日志字号档位：0 小 / 1 中 / 2 大。 */
    val logTextSize: Int = 1,
)

/**
 * 配置的进程级持有者。
 *
 * ## 为什么需要它
 *
 * 配置面板与启动屏是**两块独立的 Composable**，由宿主分别渲染，彼此拿不到对方的
 * 参数。而用户在面板里改了开关，回到启动屏必须立刻生效 —— 靠"离开面板时再读一次
 * prefs"也能做到，但那样依赖宿主刷新时机的细节，容易在某些导航路径下不触发。
 *
 * 用一个共享的 state 最直接：两边都读它，改完立刻全局可见。
 */
object MinimalConfigHolder {
    private var store: MinimalConfigStore? = null
    private var cached: MinimalConfig? = null

    fun attach(host: DshExtensionHost) {
        if (store == null) store = MinimalConfigStore(host)
    }

    fun read(): MinimalConfig {
        cached?.let { return it }
        val loaded = store?.load() ?: MinimalConfig()
        cached = loaded
        return loaded
    }

    fun write(config: MinimalConfig) {
        cached = config
        store?.save(config)
    }
}

/** 配置读写。 */
class MinimalConfigStore(private val host: DshExtensionHost) {

    private val prefs = runCatching {
        host.appContext.getSharedPreferences("dsh_minimal_shell", Context.MODE_PRIVATE)
    }.getOrNull()

    fun load(): MinimalConfig {
        val p = prefs ?: return MinimalConfig()
        // 注意：这里刻意**不**读 MinimalConfigHolder.cached —— 否则 write 之后
        // 再 load 会拿到自己刚写的值，"从磁盘重读"的语义就没了，磁盘写失败也发现不了。
        return runCatching {
            MinimalConfig(
                autoStart = p.getBoolean(KEY_AUTO_START, true),
                autoOpenWebUi = p.getBoolean(KEY_AUTO_WEBUI, true),
                showLogs = p.getBoolean(KEY_SHOW_LOGS, true),
                accent = AccentChoice.of(p.getString(KEY_ACCENT, null)),
                logTextSize = p.getInt(KEY_LOG_SIZE, 1).coerceIn(0, 2),
            )
        }.getOrDefault(MinimalConfig())
    }

    fun save(config: MinimalConfig) {
        val p = prefs ?: return
        runCatching {
            p.edit()
                .putBoolean(KEY_AUTO_START, config.autoStart)
                .putBoolean(KEY_AUTO_WEBUI, config.autoOpenWebUi)
                .putBoolean(KEY_SHOW_LOGS, config.showLogs)
                .putString(KEY_ACCENT, config.accent.name)
                .putInt(KEY_LOG_SIZE, config.logTextSize)
                .apply()
        }.onFailure { host.log("保存配置失败：" + (it.message ?: it.javaClass.simpleName)) }
    }

    private companion object {
        const val KEY_AUTO_START = "auto_start"
        const val KEY_AUTO_WEBUI = "auto_open_webui"
        const val KEY_SHOW_LOGS = "show_logs"
        const val KEY_ACCENT = "accent"
        const val KEY_LOG_SIZE = "log_text_size"
    }
}
