package me.bmax.apatch.ext.browserbridge

import me.bmax.apatch.dsh.ext.DshExtension
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.HostSwitch

/**
 * 浏览器桥：让容器里的 dsh 操作应用内的 WebView。
 *
 * ## 它其实做两件事，而且顺序重要
 *
 * 1. **确保浏览器模式开着** —— 不开的话 WebView 只加载回环的 dsh 页面，
 *    没有"别的网页"可操作，这个扩展的全部价值都不存在。
 * 2. **确保 WebView 调试通道开着** —— 容器里的 `dsh-browser` 靠它连进来。
 *
 * ## 为什么在 onLoad 里做，而不是让用户去设置里点
 *
 * 用户装这个扩展的意图就是"我要 AI 能操作浏览器"。让他装完再去别处开两个开关，
 * 中间任何一步漏了都会表现为"装了没反应"——而那是这类功能最难自查的失败。
 *
 * 宿主那边对这两个能力的判定也是"有已启用扩展声明了它"，所以：
 * - 启用本扩展 → 通道自动开（不需要额外动作）
 * - 禁用或卸载 → 通道自动关（不需要记得回来关开关）
 *
 * 这里调 set 是**冗余的保险**：宿主已经按声明推导了状态，但显式设一次能覆盖
 * "用户之前手动关过浏览器模式"这种残留。
 */
class BrowserBridgeExtension : DshExtension {

    override fun onLoad(host: DshExtensionHost) {
        // 浏览器模式先开：调试通道只在有页面可操作时才有意义。
        val browserOk = host.host.set(HostSwitch.BROWSER_MODE, true)
        if (!browserOk) {
            // 声明缺失才会走到这里 —— 那是 manifest 写错了，不是用户的问题。
            // 如实记日志，不假装成功。
            host.log("无法开启浏览器模式：manifest 未声明 host:browser-mode")
        }
        val debugOk = host.host.set(HostSwitch.WEBVIEW_DEBUG, true)
        if (!debugOk) {
            host.log("无法开启调试通道：manifest 未声明 host:webview-debug")
        }
        host.log(
            "浏览器桥已加载（浏览器模式=" + host.host.get(HostSwitch.BROWSER_MODE) +
                "，调试通道=" + host.host.get(HostSwitch.WEBVIEW_DEBUG) + "）",
        )
    }

    /**
     * 卸载时**不**关浏览器模式。
     *
     * 用户可能自己在用它浏览网页，替他关掉是另一种意外。调试通道不用管：
     * 宿主按"有没有已启用扩展声明它"推导，本扩展一旦不再是启用状态就自动失效。
     */
    override fun onUnload() {
    }
}
