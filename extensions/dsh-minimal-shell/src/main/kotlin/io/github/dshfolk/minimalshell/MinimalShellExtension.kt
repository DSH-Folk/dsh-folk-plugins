package io.github.dshfolk.minimalshell

import me.bmax.apatch.dsh.ext.DshExtension
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.ExtensionUiRegistry

/**
 * 极简启动壳。
 *
 * ## 它要解决的问题
 *
 * 宿主默认外壳是四个 Tab（首页 / 终端 / 插件 / 设置）。对"只想跟 dsh 对话"的人来说，
 * 那套结构每一步都是多余的：打开 App → 看首页卡片 → 点启动 → 等 → 再点进对话。
 *
 * 这个扩展把外壳换成一块极简启动屏：点开自动拉起容器，实时滚动显示启动日志，
 * 就绪后自动进入对话。设置 / 终端 / 插件仍然可达（右上角），只是不再占着主界面。
 *
 * ## 为什么日志要实时滚动
 *
 * 容器启动要十几秒到几十秒。这段时间如果只显示一个转圈，用户无法判断"是在正常推进
 * 还是卡住了"，于是会杀掉重来。滚动日志让人看见它确实在动 —— 这比任何进度条都有说服力。
 *
 * 日志来自 [me.bmax.apatch.dsh.ext.seam.RuntimeSeam.observeLogs]（宿主 3.0.9 起提供），
 * 进页面时先用 recentLogs 铺底，再订阅增量，于是中途切回来也不会从半截开始。
 */
class MinimalShellExtension : DshExtension {

    override fun onLoad(host: DshExtensionHost) {
        // 先绑定配置存储：外壳与配置面板都会读它，晚绑定会读到默认值。
        MinimalConfigHolder.attach(host)
        ExtensionUiRegistry.registerShell(host.id) { shellHost, hostContent ->
            MinimalShellScreen(shellHost, host, hostContent)
        }
        ExtensionUiRegistry.registerConfigPanel(host.id, "极简启动壳") {
            MinimalShellConfigPanel()
        }
        host.log("极简启动壳已加载")
    }

    /**
     * 卸载时什么都不用做。
     *
     * `registerShell` / `registerConfigPanel` 的注册项由宿主在卸载时按 extensionId
     * 统一收回（见 ExtensionUiRegistry.unregisterAll），这里再手动清一遍是多余的，
     * 而且容易漏掉某个注册点 —— 漏掉的那个会让扩展"卸载了但界面还占着"。
     */
    override fun onUnload() {
    }
}
