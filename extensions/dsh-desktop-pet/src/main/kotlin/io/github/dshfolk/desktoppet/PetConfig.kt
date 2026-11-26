package io.github.dshfolk.desktoppet

import android.content.SharedPreferences
import me.bmax.apatch.dsh.ext.DshExtensionHost
import java.io.File

/** 显示位置。 */
enum class PetDisplay {
    /**
     * 只在 dsh 网页里浮动（`Slot.WEBUI_OVERLAY`）。
     *
     * 不需要任何权限，装上就能用。代价是只在 dsh 页面内可见。
     */
    WEBUI,

    /**
     * 在宿主 App 的任何界面里浮动（`Slot.HOME_CARD` 之外的自绘层）。
     *
     * 同样不需要悬浮窗权限 —— 它画在宿主自己的窗口里。比 WEBUI 宽一些
     * （终端页、设置页都能看到），但切到别的 App 就看不见了。
     */
    HOST,

    /**
     * 全局系统悬浮窗（`TYPE_APPLICATION_OVERLAY`），能盖在任何应用上面。
     *
     * 需要「显示在其他应用上层」权限。宿主负责挂载与摘除（见 `:ext-api` 的
     * `UiSeam.showGlobalOverlay`），扩展不用起 Service、不用挂通知。
     */
    OVERLAY;

    companion object {
        fun of(name: String?): PetDisplay = entries.firstOrNull { it.name == name } ?: HOST
    }
}

/** 尺寸档位。 */
enum class PetSize(val dp: Int) {
    SMALL(64), MEDIUM(96), LARGE(140);

    companion object {
        fun of(name: String?): PetSize = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** 台词模式。 */
enum class PetSpeechMode {
    /** 只用词库，零消耗。 */
    PRESET,

    /** 用独立 AI 接口实时生成，失败回退词库。 */
    AI;

    companion object {
        fun of(name: String?): PetSpeechMode = entries.firstOrNull { it.name == name } ?: PRESET
    }
}

/**
 * 桌宠配置。
 *
 * AI 的 apiKey 单独存在 prefs 里（见 [PetConfigStore]），**不放进这个 data class** ——
 * 它会被写进日志、会被 ConfigPanel 的 state 持有，随手打一下就泄了。
 */
data class PetConfig(
    // 默认「仅应用内」而不是「仅网页内」。
    //
    // 理由是可发现性：WEBUI 档只在 dsh 网页里出现，用户装完扩展、打开 App 首页
    // 找不到桌宠，第一反应是"没生效"。HOST 档一装就能在宿主界面看到，
    // 用户确认它活着之后，再按需切到其它档。
    val display: PetDisplay = PetDisplay.HOST,
    /** 透明度 0.2–1.0。 */
    val opacity: Float = 1f,
    val size: PetSize = PetSize.MEDIUM,
    val speechMode: PetSpeechMode = PetSpeechMode.PRESET,
    /** 是否在容器状态变化时主动说话。 */
    val reactToRuntime: Boolean = true,
    /** 闲置多久主动冒一句话（秒）；0 = 不冒。 */
    val idleBubbleSeconds: Int = 45,
    // ── AI 配置（不含 apiKey） ──
    val aiBaseUrl: String = "https://api.deepseek.com",
    val aiModel: String = "deepseek-chat",
    val aiSystemPrompt: String = "你是一只住在手机里的桌宠，说话简短、温和、略带俏皮，每次不超过 30 个字。",
)

/**
 * 配置读写。
 *
 * ## apiKey 单独存
 *
 * 它不进 [PetConfig]（那个对象会被到处传、被日志打），只在本类里按 key 读写。
 * 这不是加密 —— 真要加密需要 Keystore，而那会让"换机恢复配置"变复杂。
 * 这里做到的是**不被无意泄漏**：不随配置对象流转、不写日志。
 */
class PetConfigStore(private val host: DshExtensionHost) {

    // 用 fs.prefs() 而不是自己 getSharedPreferences：宿主会加扩展前缀，
    // 免得和别的扩展撞名（两个扩展都用 "config" 就会互相覆盖）。
    private val prefs: SharedPreferences? = runCatching { host.fs.prefs("pet") }.getOrNull()

    fun load(): PetConfig {
        val p = prefs ?: return PetConfig()
        return runCatching {
            PetConfig(
                display = PetDisplay.of(p.getString(KEY_DISPLAY, null)),
                opacity = p.getFloat(KEY_OPACITY, 1f).coerceIn(0.2f, 1f),
                size = PetSize.of(p.getString(KEY_SIZE, null)),
                speechMode = PetSpeechMode.of(p.getString(KEY_SPEECH, null)),
                reactToRuntime = p.getBoolean(KEY_REACT, true),
                idleBubbleSeconds = p.getInt(KEY_IDLE, 45).coerceIn(0, 600),
                aiBaseUrl = p.getString(KEY_AI_BASE, "https://api.deepseek.com").orEmpty(),
                aiModel = p.getString(KEY_AI_MODEL, "deepseek-chat").orEmpty(),
                aiSystemPrompt = p.getString(KEY_AI_PROMPT, PetConfig().aiSystemPrompt).orEmpty(),
            )
        }.getOrDefault(PetConfig())
    }

    fun save(config: PetConfig) {
        val p = prefs ?: return
        runCatching {
            p.edit()
                .putString(KEY_DISPLAY, config.display.name)
                .putFloat(KEY_OPACITY, config.opacity)
                .putString(KEY_SIZE, config.size.name)
                .putString(KEY_SPEECH, config.speechMode.name)
                .putBoolean(KEY_REACT, config.reactToRuntime)
                .putInt(KEY_IDLE, config.idleBubbleSeconds)
                .putString(KEY_AI_BASE, config.aiBaseUrl)
                .putString(KEY_AI_MODEL, config.aiModel)
                .putString(KEY_AI_PROMPT, config.aiSystemPrompt)
                .apply()
        }.onFailure { host.log("保存桌宠配置失败：" + (it.message ?: it.javaClass.simpleName)) }
    }

    var apiKey: String
        get() = runCatching { prefs?.getString(KEY_API_KEY, "").orEmpty() }.getOrDefault("")
        set(value) {
            runCatching { prefs?.edit()?.putString(KEY_API_KEY, value)?.apply() }
        }

    /** 用户自定义词库文件（在扩展私有目录里）。 */
    fun lexiconFile(): File = File(host.fs.ownDir, "lexicon.json")

    /** 自定义图（用户选过的 PNG/GIF 会被拷到这里）。 */
    fun petImageFile(): File = File(host.fs.ownDir, "pet.png")

    private companion object {
        const val KEY_DISPLAY = "display"
        const val KEY_OPACITY = "opacity"
        const val KEY_SIZE = "size"
        const val KEY_SPEECH = "speech"
        const val KEY_REACT = "react_to_runtime"
        const val KEY_IDLE = "idle_bubble_seconds"
        const val KEY_AI_BASE = "ai_base_url"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_PROMPT = "ai_system_prompt"
        const val KEY_API_KEY = "ai_api_key"
    }
}

/**
 * 配置的进程级持有者（与 minimal-shell 同一个理由：面板与桌宠是两块独立 Composable）。
 */
object PetConfigHolder {
    private var store: PetConfigStore? = null
    private var host: DshExtensionHost? = null

    /**
     * 配置的**可观察**副本。
     *
     * ## 为什么必须是 Compose state 而不是普通字段
     *
     * 第一版是 `private var cached: PetConfig?`，桌宠那边用
     * `remember { PetConfigHolder.read() }` 取一份快照 —— 那是**静态值**，
     * 用户在配置面板改完档位/透明度/尺寸，桌宠不会重组，界面上毫无变化，
     * 必须重启 App 才生效。
     *
     * 改成 `mutableStateOf` 之后，读写都走它，任何一处改动都会让读过它的
     * Composable 自动重组。
     */
    private val state = androidx.compose.runtime.mutableStateOf<PetConfig?>(null)

    fun attach(host: DshExtensionHost) {
        if (store == null) store = PetConfigStore(host)
        if (this.host == null) this.host = host
        if (state.value == null) state.value = store?.load() ?: PetConfig()
    }

    /**
     * 宿主能力入口。
     *
     * 配置面板需要它查悬浮窗权限、跳系统设置页 —— 但面板是宿主渲染的纯 Composable，
     * 拿不到 `DshExtensionHost` 参数。这里留一个入口，面板通过它拿到 seam。
     */
    fun uiSeam(): me.bmax.apatch.dsh.ext.seam.UiSeam? =
        runCatching { host?.seam?.ui }.getOrNull()

    /** 宿主句柄（配置面板改完显示档位后要重新挂载悬浮窗）。 */
    fun extensionHost(): DshExtensionHost? = host

    fun store(): PetConfigStore? = store

    /** 读当前配置（Compose 可观察：在 Composable 里读它，write 之后会自动重组）。 */
    fun read(): PetConfig = state.value ?: run {
        val loaded = store?.load() ?: PetConfig()
        state.value = loaded
        loaded
    }

    /** 直接拿可观察对象，给需要 `by` 委托的 Composable 用。 */
    fun observable(): androidx.compose.runtime.MutableState<PetConfig?> = state

    fun write(config: PetConfig) {
        state.value = config
        store?.save(config)
    }
}
