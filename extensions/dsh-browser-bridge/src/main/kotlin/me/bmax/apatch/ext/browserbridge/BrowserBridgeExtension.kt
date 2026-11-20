package me.bmax.apatch.ext.browserbridge

import me.bmax.apatch.dsh.ext.DshExtension
import me.bmax.apatch.dsh.ext.DshExtensionHost
import me.bmax.apatch.dsh.ext.HostSwitch

/**
 * 浏览器桥：让容器里的 dsh 操作应用内的 WebView。
 *
 * ## 这个扩展自己做了哪些事
 *
 * 1. **开浏览器模式** —— 不开的话 WebView 只加载回环的 dsh 页面，没有"别的网页"可操作。
 * 2. **开 WebView 调试通道** —— 容器里的 `dsh-browser` 靠它连进来。
 * 3. **把 `dsh-browser` 命令装进容器** —— 脚本在下面，通过 `host.container` 落盘。
 *
 * 第 3 条是这一版的重点：命令由扩展提供，宿主不再内置。卸载扩展时宿主按归属标记
 * 把命令一并摘掉，不会留下"我卸载了、命令还在"。
 *
 * ## 为什么装命令要等 onContainerReady
 *
 * onLoad 在 App 启动时跑，那时运行时可能还没装（首次启动、刚清数据）。往不存在的
 * rootfs 里写命令只会失败。宿主在"运行时可用"时回调 onContainerReady，那里才是
 * 正确的安装时机。
 */
class BrowserBridgeExtension : DshExtension {

    override fun onLoad(host: DshExtensionHost) {
        // 浏览器模式先开：调试通道只在有页面可操作时才有意义。
        val browserOk = host.host.set(HostSwitch.BROWSER_MODE, true)
        if (!browserOk) {
            // 只有 manifest 没声明才会走到这里 —— 那是打包错误，不是用户的问题。
            host.log("无法开启浏览器模式：manifest 未声明 host:browser-mode")
        }
        host.log(
            "浏览器桥已加载（浏览器模式=" + host.host.get(HostSwitch.BROWSER_MODE) +
                "，调试通道=" + host.host.get(HostSwitch.WEBVIEW_DEBUG) + "）",
        )
    }

    /**
     * 容器就绪：装命令。
     *
     * **幂等**：宿主可能在重装运行时、切换通道后再调一次。
     * `installCommand` 本身是"写文件 + 写归属标记"，重复调用结果相同，
     * 所以这里直接装，不做额外的存在性判断。
     */
    override fun onContainerReady(host: DshExtensionHost) {
        val ok = host.container.installCommand("dsh-browser", BROWSER_CLI)
        if (ok) {
            host.log("已把 dsh-browser 装进容器")
        } else {
            // 装失败不是致命错误：容器可能还没就绪、或 rootfs 只读。
            // 下次 onContainerReady 会再试。
            host.log("安装 dsh-browser 失败，等下次容器就绪时重试")
        }
    }

    /**
     * 卸载时**不**关浏览器模式。
     *
     * 用户可能自己在用它浏览网页，替他关掉是另一种意外。命令由宿主按归属标记收回，
     * 这里不用管；调试通道也由宿主按"有没有已启用扩展声明它"自动失效。
     */
    override fun onUnload() {
    }

    private companion object {
        /**
         * `dsh-browser` 的脚本正文。
         *
         * ## 为什么内嵌在代码里而不是放 assets
         *
         * 扩展的 assets 要在运行期从扩展目录读，而 .dshx 里只有 manifest + classes.dex
         * （见扩展仓库的打包步骤）。内嵌进 dex 是唯一不需要改打包格式的做法。
         *
         * ## 它是自包含的
         *
         * 自带最小 CDP 客户端（手写 WebSocket 握手），不依赖容器里有没有 ws 包 ——
         * 依赖运行时的内部 node_modules 会在 dsh 升级时静默坏掉。
         */
        val BROWSER_CLI = """
        #!/usr/bin/env node
        const fs = require('fs');
        const http = require('http');
        const CFG = '/root/.dsh/fs-bridge.json';
        
        function usage() {
          console.error([
            'usage: dsh-browser <command> [options]',
            '',
            '  targets                          列出可调试的页面',
            '  version                          打印 WebView 的 DevTools 版本',
            '  eval <js>                        在页面里执行 JS，打印结果',
            '  shot <file>                      截屏保存为 PNG',
            '',
            'options:',
            '  --endpoint <url>   DevTools 地址（默认由 adb forward 提供）',
            '',
            '需要先在应用里打开「WebView 调试通道」，并做一次端口转发：',
            '  adb forward tcp:9222 localabstract:<webviewSocket>',
            'socket 名可以在应用设置里看到。',
          ].join('\n'));
        }
        
        function fail(msg) { console.error('dsh-browser: ' + msg); process.exit(1); }
        
        const argv = process.argv.slice(2);
        if (argv.length === 0 || argv[0] === '-h' || argv[0] === '--help') { usage(); process.exit(argv.length ? 0 : 1); }
        
        let endpoint = '';
        const rest = [];
        for (let i = 0; i < argv.length; i++) {
          if (argv[i] === '--endpoint') { endpoint = argv[++i] || ''; }
          else rest.push(argv[i]);
        }
        
        if (!endpoint) {
          let socket = '';
          try {
            if (fs.existsSync(CFG)) socket = JSON.parse(fs.readFileSync(CFG, 'utf8')).webviewSocket || '';
          } catch (e) { /* 配置坏了就当作没开 */ }
          if (!socket) {
            fail('WebView 调试通道没有开启。请在应用设置里打开它，或用 --endpoint 指定 adb forward 的地址。');
          }
          // 抽象 socket 不能直接 HTTP 访问，必须经 adb forward。
          // 这里只提示，不猜一个端口 —— 猜错了报的错更难懂。
          fail('检测到 socket ' + socket + '，但它需要先转发：\n' +
               '  adb forward tcp:9222 localabstract:' + socket + '\n' +
               '然后用 --endpoint http://127.0.0.1:9222 重试。');
        }
        
        /**
         * 最小 CDP 客户端：手写 WebSocket 握手 + 单帧收发。
         *
         * ## 为什么不引 ws 依赖
         *
         * 容器里 node_modules 确实有 ws（dsh 的依赖），但那是**运行时的内部细节**：
         * dsh 换版本、换打包方式都可能让它消失，而那时这个 CLI 会静默坏掉。
         * 手写一份只够发一条命令的实现（约 40 行），比绑一个不受控的依赖稳。
         *
         * 只支持：客户端发起、文本帧、无分片、payload < 64KB。CDP 的单条响应
         * 基本都在这个范围内；截屏 base64 可能超，所以 shot 走的是分片读取。
         */
        function cdp(wsUrl, method, params) {
          return new Promise(function (resolve, reject) {
            const u = new URL(wsUrl);
            const key = require('crypto').randomBytes(16).toString('base64');
            const req = http.request({
              host: u.hostname, port: u.port || 80, path: u.pathname + (u.search || ''),
              headers: {
                'Connection': 'Upgrade', 'Upgrade': 'websocket',
                'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Key': key,
              },
            });
            req.on('upgrade', function (res, socket) {
              let buf = Buffer.alloc(0);
              let done = false;
              const send = function (obj) {
                const payload = Buffer.from(JSON.stringify(obj), 'utf8');
                const mask = require('crypto').randomBytes(4);
                const len = payload.length;
                let header;
                if (len < 126) {
                  header = Buffer.from([0x81, 0x80 | len]);
                } else if (len < 65536) {
                  header = Buffer.alloc(4); header[0] = 0x81; header[1] = 0x80 | 126; header.writeUInt16BE(len, 2);
                } else {
                  header = Buffer.alloc(10); header[0] = 0x81; header[1] = 0x80 | 127;
                  header.writeUInt32BE(0, 2); header.writeUInt32BE(len, 6);
                }
                const masked = Buffer.alloc(len);
                for (let i = 0; i < len; i++) masked[i] = payload[i] ^ mask[i % 4];
                socket.write(Buffer.concat([header, mask, masked]));
              };
              socket.on('data', function (chunk) {
                buf = Buffer.concat([buf, chunk]);
                while (buf.length >= 2 && !done) {
                  const opcode = buf[0] & 0x0f;
                  let off = 2;
                  let len = buf[1] & 0x7f;
                  if (len === 126) { if (buf.length < 4) return; len = buf.readUInt16BE(2); off = 4; }
                  else if (len === 127) { if (buf.length < 10) return; len = Number(buf.readBigUInt64BE(2)); off = 10; }
                  if (buf.length < off + len) return;
                  const data = buf.slice(off, off + len);
                  buf = buf.slice(off + len);
                  if (opcode === 0x8) { if (!done) { done = true; reject(new Error('WebSocket 被关闭')); } return; }
                  if (opcode !== 0x1) continue;
                  let msg;
                  try { msg = JSON.parse(data.toString('utf8')); } catch (e) { continue; }
                  if (msg.id === 1) { done = true; socket.end(); resolve(msg.result || {}); }
                }
              });
              socket.on('error', reject);
              send({ id: 1, method: method, params: params });
            });
            req.on('error', reject);
            req.end();
          });
        }
        
        function get(path) {
          return new Promise(function (resolve, reject) {
            const u = new URL(path, endpoint);
            const r = http.get(u, function (res) {
              const chunks = [];
              res.on('data', function (c) { chunks.push(c); });
              res.on('end', function () { resolve(Buffer.concat(chunks).toString('utf8')); });
            });
            r.on('error', reject);
          });
        }
        
        (async function () {
          const cmd = rest[0];
          try {
            if (cmd === 'version') {
              console.log(await get('/json/version'));
            } else if (cmd === 'targets') {
              console.log(await get('/json/list'));
            } else if (cmd === 'eval' || cmd === 'shot') {
              // 走 CDP：先问 /json/list 拿到页面 WebSocket 地址，再发一条命令。
              const list = JSON.parse(await get('/json/list'));
              const page = list.find(function (t) { return t.type === 'page' && t.webSocketDebuggerUrl; });
              if (!page) fail('没有可调试的页面（WebView 可能还没加载内容）');
              const method = cmd === 'eval' ? 'Runtime.evaluate' : 'Page.captureScreenshot';
              const params = cmd === 'eval'
                ? { expression: rest[1] || '', returnByValue: true, awaitPromise: true }
                : { format: 'png' };
              if (cmd === 'eval' && !rest[1]) fail('eval 需要一个 JS 表达式');
              const result = await cdp(page.webSocketDebuggerUrl, method, params);
              if (cmd === 'eval') {
                const r = result.result || {};
                if (r.exceptionDetails) fail('页面里抛了异常：' + JSON.stringify(r.exceptionDetails));
                const v = r.result && r.result.value;
                console.log(typeof v === 'string' ? v : JSON.stringify(v));
              } else {
                const data = (result.data || '');
                if (!data) fail('截屏返回空');
                const out = rest[1] || '/root/shot.png';
                fs.writeFileSync(out, Buffer.from(data, 'base64'));
                console.log(out);
              }
            } else {
              usage();
              process.exitCode = 1;
            }
          } catch (e) {
            fail('请求失败：' + (e && e.message ? e.message : String(e)));
          }
        })();
        """.trimIndent()
    }
}
