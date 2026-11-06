package io.github.ipf_sinon.dshgoshell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.bmax.apatch.dsh.ext.DshExtension
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.ExtLog
import me.bmax.apatch.dsh.ext.ExtensionShellTabs
import me.bmax.apatch.dsh.ext.ExtensionUiRegistry
import me.bmax.apatch.dsh.ext.RuntimeSummary
import me.bmax.apatch.dsh.ext.ShellHost

/**
 * DSH-Go 风格外壳扩展的入口。
 *
 * 为什么这里只做「注册」一件事：外壳接管是宿主给的**运行时**能力，扩展的作用就是在
 * onLoad 里把一段 @Composable 交给 [ExtensionUiRegistry]。真正的界面代码在 [DshGoShell]，
 * 这样即便注册之后宿主还没渲染（比如用户没启用外壳），也不会白跑任何初始化。
 */
class DshGoShellExtension : DshExtension {

    /** 记下自己的 id：onUnload 拿不到 host 句柄，但撤注册需要 id。 */
    private var loadedId: String? = null

    override fun onLoad(host: DshExtensionHost) {
        loadedId = host.id
        host.log("DSH-Go 外壳 v1.0.0 已注册")

        // 交给宿主的是一段「怎么画外壳」的说明，不是立刻画：
        // 宿主在渲染导航时才会调用它，参数里的 content 才是宿主原有的页面树。
        ExtensionUiRegistry.registerShell(host.id) { shellHost, content ->
            DshGoShell(host = shellHost, content = content)
        }
    }

    override fun onUnload() {
        // 宿主停用扩展时自己也会撤注册；这里再撤一次是幂等的兜底 ——
        // 外壳是整屏接管，漏撤一次用户就会卡在一个没有入口的空壳里。
        loadedId?.let { id ->
            runCatching { ExtensionUiRegistry.unregisterAll(id) }
        }
        loadedId = null
    }
}

/**
 * 本外壳的色板。
 *
 * 为什么不拦截宿主主题（[ExtensionUiRegistry.interceptPalette]）而是自己包一层
 * MaterialTheme：色板拦截是**全局**的，会影响宿主所有页面（含设置、崩溃对话框）；
 * 而本扩展的诉求只是「外壳是深色的」。范围越小越不容易惹麻烦。
 */
private object GoPalette {
    val Bg = Color(0xFF141416)
    val Surface = Color(0xFF1C1C20)
    val Divider = Color(0xFF2A2A30)
    val Primary = Color(0xFF4B67B4)
    val TextPrimary = Color(0xFFEDEDF0)
    val TextMuted = Color(0xFF9A9AA4)
    val Running = Color(0xFF5DD08A)
}

private val DshGoColorScheme = darkColorScheme(
    primary = GoPalette.Primary,
    onPrimary = Color.White,
    background = GoPalette.Bg,
    onBackground = GoPalette.TextPrimary,
    surface = GoPalette.Surface,
    onSurface = GoPalette.TextPrimary,
    surfaceVariant = GoPalette.Surface,
    onSurfaceVariant = GoPalette.TextMuted,
)

/** 一个导航项：宿主主页面 id + 显示名。id 用宿主的常量，避免字符串写错。 */
private data class NavItem(val tabId: String, val label: String)

private val NavItems = listOf(
    NavItem(ExtensionShellTabs.HOME, "主页"),
    NavItem(ExtensionShellTabs.TERMINAL, "终端"),
    NavItem(ExtensionShellTabs.PLUGINS, "插件"),
    NavItem(ExtensionShellTabs.SETTINGS, "设置"),
)

/**
 * 外壳里的日志出口。
 *
 * 为什么不用 host.log(...)：[ShellHost] 是**导航**句柄，只给 navigate/currentTabId/openWebUi
 * 与运行时状态，没有日志方法（那在 [DshExtensionHost] 上，只在 onLoad 阶段拿得到）。
 * 外壳运行期要记点什么，走 :ext-api 的 [ExtLog] —— 它的 sink 由宿主指向自己的日志存储。
 * 前缀写死 ext 名：日志落到 dsh.log 里时能一眼看出是谁干的。
 */
private fun goLog(message: String) {
    ExtLog.log("[ext:dsh-go-shell] " + message)
}

/** 宽屏阈值：>= 720dp 时抽屉换成常驻侧栏（平板横屏、折叠屏展开都落在这条线上）。 */
private val WideScreenThreshold = 720.dp

private fun labelOf(tabId: String): String =
    NavItems.firstOrNull { it.tabId == tabId }?.label ?: tabId

/**
 * DSH-Go 风格外壳本体。
 *
 * 与宿主原来的样子只差一件事：**去掉底部四页栏**，把导航收进顶栏汉堡 / 宽屏侧栏。
 * 页面本体仍然是 [content] —— 那是宿主原来的整棵页面树（主页、终端、插件、设置…），
 * 必须照常渲染，否则「换了个外壳」等于把插件页和设置页弄丢了。
 *
 * 所有导航动作都走 [ShellHost]（navigate / currentTabId / openWebUi），
 * **不要**去摸宿主的 navController：那是 :app 内部实现，扩展拿不到也不该依赖。
 */
@Composable
fun DshGoShell(
    host: ShellHost,
    content: @Composable (Modifier) -> Unit,
) {
    // 初值从宿主读一次；之后以本地状态为准（宿主没有「当前页变化」的回调）。
    // TODO: 等宿主提供可观察的当前页状态后，改成收集它，避免用户用系统返回键切页时高亮不同步。
    var currentTab by remember {
        mutableStateOf(
            runCatching { host.currentTabId() }.getOrDefault(ExtensionShellTabs.HOME)
        )
    }
    // 运行时状态：先拿一次快照兜底，再异步收集状态流（宿主没实现流时静默降级成静态）。
    var summary by remember { mutableStateOf<RuntimeSummary?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(host) {
        summary = runCatching { host.runtimeSummary() }.getOrNull()
        val states = runCatching { host.runtimeStates() }.getOrNull() ?: return@LaunchedEffect
        runCatching { states.collect { summary = it } }
    }

    // 宿主调用一律 runCatching：外壳跑在宿主进程里，抛出去就是整个 App 崩。
    val select: (String) -> Unit = { tabId ->
        runCatching { host.navigate(tabId) }
            .onFailure { goLog("切换到 " + tabId + " 失败：" + it.message) }
        currentTab = tabId
    }
    val openWebUi: () -> Unit = {
        runCatching { host.openWebUi() }
            .onFailure { goLog("打开 WebUI 失败：" + it.message) }
    }
    val backToDefaultShell: () -> Unit = {
        // TODO: 宿主目前**没有**「切回默认外壳」的 API（ShellHost 里没有对应方法）。
        // 与其猜一个不存在的接口，这里先留空：用户仍可以在「插件 → 扩展」里停用本扩展，
        // 停用后 ExtensionUiRegistry 里没有外壳注册，宿主自然回到原生外壳。
        goLog("切回默认外壳：宿主尚未提供此接口（TODO）")
    }

    MaterialTheme(colorScheme = DshGoColorScheme) {
        // 宽窄判断用 BoxWithConstraints 而不是 LocalConfiguration.screenWidthDp：
        // 后者是平台量（含系统栏、不考虑本 Composable 实际拿到多少空间），在分屏/折叠屏上
        // 会给出错误结论；BoxWithConstraints 量的是自己这一层的可用宽度，且不引入
        // 任何可能被改废的 platform API。
        BoxWithConstraints(Modifier.fillMaxSize().background(GoPalette.Bg)) {
            val wide = maxWidth >= WideScreenThreshold

            if (wide) {
                // 宽屏：抽屉点起来太远，直接常驻侧栏。
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .background(GoPalette.Surface)
                    ) {
                        SideContent(
                            currentTab = currentTab,
                            summary = summary,
                            onSelect = select,
                            onWebUi = openWebUi,
                            onBackToDefaultShell = backToDefaultShell,
                        )
                    }
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(GoPalette.Divider)
                    )
                    Column(Modifier.fillMaxSize()) {
                        TopBar(
                            title = labelOf(currentTab),
                            summary = summary,
                            onMenu = null,
                        )
                        content(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            } else {
                // 窄屏：抽屉。注意这里**不画底部四页栏** —— 那正是要被换掉的东西。
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(drawerContainerColor = GoPalette.Surface) {
                            SideContent(
                                currentTab = currentTab,
                                summary = summary,
                                onSelect = { tabId ->
                                    select(tabId)
                                    scope.launch { drawerState.close() }
                                },
                                onWebUi = {
                                    openWebUi()
                                    scope.launch { drawerState.close() }
                                },
                                onBackToDefaultShell = {
                                    backToDefaultShell()
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }
                    },
                ) {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(
                            title = labelOf(currentTab),
                            summary = summary,
                            onMenu = { scope.launch { drawerState.open() } },
                        )
                        content(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}

/** 顶栏：汉堡（仅窄屏）+ 当前页名 + 右侧状态胶囊。 */
@Composable
private fun TopBar(
    title: String,
    summary: RuntimeSummary?,
    onMenu: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GoPalette.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onMenu != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) {
                // 用字符而不是 Icons：少一个 material-icons 依赖，就少一处版本兼容风险。
                Text("☰", color = GoPalette.TextPrimary, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            color = GoPalette.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (summary != null) {
            Spacer(Modifier.width(8.dp))
            StatusPill(summary)
        }
    }
}

/** 状态胶囊：版本 · 运行时方式，前面一个点表示服务是否在跑。 */
@Composable
private fun StatusPill(summary: RuntimeSummary) {
    Row(
        modifier = Modifier
            .widthIn(max = 180.dp)
            .clip(RoundedCornerShape(50))
            .background(GoPalette.Primary.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (summary.running) GoPalette.Running else GoPalette.TextMuted)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = summary.version + " · " + summary.mode,
            color = GoPalette.TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 侧栏/抽屉共用的内容（宽度由调用方决定）。 */
@Composable
private fun SideContent(
    currentTab: String,
    summary: RuntimeSummary?,
    onSelect: (String) -> Unit,
    onWebUi: () -> Unit,
    onBackToDefaultShell: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
        Text("DSH-Go", color = GoPalette.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("扩展外壳", color = GoPalette.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))

        for (item in NavItems) {
            NavRow(
                label = item.label,
                selected = item.tabId == currentTab,
                onClick = { onSelect(item.tabId) },
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(GoPalette.Divider))
        Spacer(Modifier.height(10.dp))

        NavRow(label = "打开 WebUI", selected = false, onClick = onWebUi)
        NavRow(label = "切回默认外壳", selected = false, onClick = onBackToDefaultShell)

        Spacer(Modifier.weight(1f))
        Text(
            text = summary?.let { it.version + " · " + it.mode } ?: "运行时状态不可用",
            color = GoPalette.TextMuted,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一个侧栏条目：选中时整行微亮 + 左侧小圆点。 */
@Composable
private fun NavRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) GoPalette.Primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (selected) GoPalette.Primary else Color.Transparent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected) GoPalette.TextPrimary else GoPalette.TextMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
