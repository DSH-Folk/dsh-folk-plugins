package io.github.dshfolk.desktoppet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
 * ## 三档显示位置
 *
 * | 档位 | 实现 | 权限 | 可见范围 |
 * |---|---|---|---|
 * | [PetDisplay.WEBUI] | `Slot.WEBUI_OVERLAY` | 无 | 仅 dsh 网页内 |
 * | [PetDisplay.HOST] | `Slot.HOME_CARD` 所在的宿主层 | 无 | 宿主 App 内 |
 * | [PetDisplay.OVERLAY] | `UiSeam.showGlobalOverlay` | 悬浮窗权限 | 任何应用之上 |
 *
 * 前两档都是往宿主窗口里插内容，不需要权限，但离开 App 就看不见；
 * 第三档是独立系统窗口，能盖在别的应用上，代价是要用户去系统设置里授权。
 *
 * **为什么三档都要有**：只做全局悬浮窗的话，不愿意给权限的用户就完全用不了；
 * 只做前两档的话，"桌宠"这个名字就名不副实（离开 App 就没了）。
 *
 * ## 全局悬浮窗的生命周期
 *
 * 由宿主托管（`UiSeam.showGlobalOverlay` → `GlobalOverlayHost`）。扩展被停用、
 * 卸载、或安全模式熔断时，宿主自动摘掉窗口 —— 所以这里**不需要**在 onUnload 里
 * 手动清理，也不该自己起 Service（那会多出一条常驻通知）。
 */
class DesktopPetExtension : DshExtension {

    override fun onLoad(host: DshExtensionHost) {
        PetConfigHolder.attach(host)

        // WebUI 内浮动：宿主给网页留的覆盖槽位。
        // INSERT_AFTER 而不是 REPLACE —— 这个槽位可能还有别的扩展在用。
        ExtensionUiRegistry.registerSlot(
            host.id,
            ExtensionUiRegistry.Slot.WEBUI_OVERLAY,
            ExtensionUiRegistry.SlotOp.INSERT_AFTER,
        ) { modifier ->
            PetSlotContent(host, modifier)
        }

        // 宿主界面内：插到首页卡片区。与 WEBUI 槽位互不冲突 —— 两者都注册着，
        // 但 [PetSlotContent] 会按当前配置决定画不画，所以不会同时出现两只。
        ExtensionUiRegistry.registerSlot(
            host.id,
            ExtensionUiRegistry.Slot.HOME_CARD,
            ExtensionUiRegistry.SlotOp.INSERT_AFTER,
        ) { modifier ->
            PetSlotContent(host, modifier)
        }

        ExtensionUiRegistry.registerConfigPanel(host.id, "桌宠") {
            PetConfigPanel(
                onDisplayChanged = {
                    // 用户切了显示档位：立刻按新档位挂/摘悬浮窗，
                    // 不必等他退出设置页再重进。
                    syncGlobalOverlay(host)
                },
            )
        }

        // 全局悬浮窗：按当前配置决定要不要挂。
        syncGlobalOverlay(host)

        host.log("桌宠已加载")
    }

    /**
     * 卸载时的清理。
     *
     * 全局悬浮窗**不在这里摘** —— 宿主在 `ExtensionEngine.unload` 里按 ownerId
     * 统一摘（`GlobalOverlayHost.hideAllOf`）。这里再摘一次是多余的，而且不可靠：
     * 安全模式熔断时扩展的 onUnload 根本不会被调用（它压根没加载）。
     */
    override fun onUnload() {
    }

    /**
     * 按配置挂载/摘除全局悬浮窗。
     *
     * 在 onLoad 与配置面板保存后各调一次。做成幂等的：重复调用不会叠窗
     * （`show` 内部先 hide 同 owner）。
     */
    fun syncGlobalOverlay(host: DshExtensionHost) {
        val config = PetConfigHolder.read()
        val ui = host.seam.ui
        if (config.display != PetDisplay.OVERLAY) {
            runCatching { ui.hideGlobalOverlay(host.id) }
            return
        }
        if (!ui.hasOverlayPermission()) {
            // 没权限时不挂，也不弹窗打扰 —— 用户在配置面板里能看到引导按钮。
            // 这里静默是刻意的：启动时弹一个"请授权"对只想用网页内的用户是骚扰。
            host.log("桌宠：选择了全局悬浮窗但未授权，暂不显示")
            return
        }
        runCatching {
            ui.showGlobalOverlay(host.id) { modifier ->
                GlobalPetContent(host, modifier)
            }
        }
    }
}

/**
 * 全局悬浮窗里的内容。
 *
 * 与槽位内容的区别：悬浮窗是独立窗口，**没有**宿主的主题上下文，所以要自己
 * 处理尺寸（不能 fillMaxSize —— 那会撑满整个屏幕并挡住所有点击）。
 */
@Composable
private fun GlobalPetContent(host: DshExtensionHost, modifier: Modifier) {
    val config = remember { PetConfigHolder.read() }
    var bubble by remember { mutableStateOf<String?>(null) }
    val lexicon = remember { loadLexicon(host) }

    LaunchedEffect(bubble) {
        if (bubble != null) {
            delay(4000)
            bubble = null
        }
    }

    // 悬浮窗只包住桌宠本身：Box 不 fillMaxSize，让 WRAP_CONTENT 生效。
    Box(modifier) {
        DesktopPet(
            config = config,
            bubble = bubble,
            onInteract = { scene ->
                bubble = lexicon.pick(scene) ?: scene.label
            },
            modifier = Modifier,
        )
    }
}

/**
 * 槽位内容（WEBUI / HOST 两档共用）。
 *
 * 为什么共用：两档的区别只在宿主把它放在哪个槽位，宠物本身的画法一样。
 * 做成两个组件会让"改一处忘另一处"变成必然。
 */
@Composable
fun PetSlotContent(host: DshExtensionHost, modifier: Modifier) {
    val config = remember { PetConfigHolder.read() }

    // 只有当前档位与所在槽位匹配时才画。
    // 不判断的话，两档都注册了槽位，用户会同时看到两只桌宠。
    val inWebUi = config.display == PetDisplay.WEBUI
    val inHost = config.display == PetDisplay.HOST
    if (!inWebUi && !inHost) return

    var bubble by remember { mutableStateOf<String?>(null) }
    val lexicon = remember { loadLexicon(host) }
    val runtime = host.seam.runtime

    // 状态联动：容器启动/就绪时冒一句。
    // 用 RuntimeSeam.observe 而不是 EventSeam —— 后者在 3.0.10 之前从未 emit 过，
    // 而 observe 是纯本地的 StateFlow 订阅，不依赖宿主是否记得发事件。
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

    Box(modifier.fillMaxSize()) {
        DesktopPet(
            config = config,
            bubble = bubble,
            onInteract = { scene ->
                bubble = lexicon.pick(scene) ?: scene.label
            },
            modifier = Modifier,
        )
    }
}

/** 读词库：用户导入过就用合并后的，否则用内置。 */
private fun loadLexicon(host: DshExtensionHost): PetLexicon {
    val store = PetConfigHolder.store() ?: return PetLexicon.DEFAULT
    val file = store.lexiconFile()
    if (!file.isFile) return PetLexicon.DEFAULT
    val text = runCatching { file.readText() }.getOrNull() ?: return PetLexicon.DEFAULT
    return PetLexicon.merge(PetLexicon.fromJson(text))
}
