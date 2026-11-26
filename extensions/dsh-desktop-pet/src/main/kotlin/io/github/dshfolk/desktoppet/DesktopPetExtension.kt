package io.github.dshfolk.desktoppet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import me.bmax.apatch.dsh.ext.DshExtension
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.ExtensionUiRegistry

/**
 * 桌宠扩展。
 *
 * ## 显示位置的三种选择，为什么都要有
 *
 * - **WebUI 内**（`Slot.WEBUI_OVERLAY`）：不需要悬浮窗权限，装上就能用。代价是只在
 *   dsh 网页里可见 —— 用户切到别的页面就看不见了。
 * - **宿主内**：跟 WebUI 类似，但覆盖整个 App 而不只是网页区。
 * - **全局悬浮窗**：能盖在任何 App 上，但需要 SYSTEM_ALERT_WINDOW（用户要去系统设置里
 *   单独授权）。很多人不愿意给，所以它不能是唯一选项。
 *
 * 把三者都做出来，用户才能按自己的接受度选；只做全局悬浮窗的话，不给权限的用户就
 * 完全用不了这个扩展。
 *
 * ## 台词来源
 *
 * 默认用内置词库（零消耗、即时）。用户可以在配置里切到 AI 模式，接自己的
 * OpenAI 兼容接口。AI 失败时**回退词库**而不是沉默 —— 桌宠不说话比说错话更像坏了。
 */
class DesktopPetExtension : DshExtension {

    override fun onLoad(host: DshExtensionHost) {
        PetConfigHolder.attach(host)

        // WebUI 内浮动：注册到宿主给网页留的覆盖槽位。
        // INSERT_AFTER 而不是 REPLACE —— 这个槽位可能还有别的扩展在用，
        // REPLACE 会把它们顶掉。
        ExtensionUiRegistry.registerSlot(
            host.id,
            ExtensionUiRegistry.Slot.WEBUI_OVERLAY,
            ExtensionUiRegistry.SlotOp.INSERT_AFTER,
        ) { modifier ->
            PetSlotContent(host, modifier)
        }

        ExtensionUiRegistry.registerConfigPanel(host.id, "桌宠") {
            PetConfigPanel()
        }

        host.log("桌宠已加载")
    }

    override fun onUnload() {
    }
}

/**
 * 槽位内容：桌宠本体 + 状态联动。
 *
 * 为什么在槽位里而不是独立 Activity：槽位由宿主渲染，扩展不需要自己管生命周期、
 * 不需要 FOREGROUND_SERVICE。代价是只能活在宿主界面里 —— 这也正是为什么
 * "全局悬浮窗"需要另一条实现路径（见 PetOverlayService，当前版本未启用）。
 */
@Composable
fun PetSlotContent(host: DshExtensionHost, modifier: Modifier) {
    val config = remember { PetConfigHolder.read() }
    var bubble by remember { mutableStateOf<String?>(null) }
    val lexicon = remember { loadLexicon(host) }
    val runtime = host.seam.runtime

    // 状态联动：容器启动/就绪时冒一句。
    // 注意用 RuntimeSeam.observe 而不是 EventSeam 的 RUNTIME_STATE —— 后者宿主当前
    // 并没有真的 emit（Events.RUNTIME_STATE 只在常量里定义），订阅它收不到任何东西。
    DisposableEffect(config.reactToRuntime) {
        if (!config.reactToRuntime) return@DisposableEffect onDispose { }
        var lastRunning: Boolean? = null
        val unsubscribe = runtime.observe { s ->
            val prev = lastRunning
            lastRunning = s.running
            if (prev == null) return@observe
            if (prev != s.running) {
                bubble = if (s.running) "容器起来了。" else "容器停了。"
            }
        }
        onDispose { unsubscribe() }
    }

    // 闲置冒泡
    LaunchedEffect(config.idleBubbleSeconds) {
        val seconds = config.idleBubbleSeconds
        if (seconds <= 0) return@LaunchedEffect
        while (true) {
            delay(seconds * 1000L)
            lexicon.pick(PetScene.IDLE)?.let { bubble = it }
        }
    }

    // 气泡几秒后自动消失
    LaunchedEffect(bubble) {
        if (bubble != null) {
            delay(4000)
            bubble = null
        }
    }

    DesktopPet(
        config = config,
        bubble = bubble,
        onInteract = { scene ->
            // 立即用词库回一句（不等待 AI）—— 交互要有即时反馈，
            // 等网络回来再说话会显得没反应。
            bubble = lexicon.pick(scene) ?: scene.label
        },
        modifier = modifier,
    )
}

/** 读词库：用户导入过就用合并后的，否则用内置。 */
private fun loadLexicon(host: DshExtensionHost): PetLexicon {
    val store = PetConfigHolder.store() ?: return PetLexicon.DEFAULT
    val file = store.lexiconFile()
    if (!file.isFile) return PetLexicon.DEFAULT
    val text = runCatching { file.readText() }.getOrNull() ?: return PetLexicon.DEFAULT
    return PetLexicon.merge(PetLexicon.fromJson(text))
}
