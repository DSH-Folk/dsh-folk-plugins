# dsh-folk-plugins

DSH-Folk 的**扩展市场**仓库：一个目录 + 一堆 `.dshx` 安装包。

- `index.json` —— 市场清单，App 的「扩展市场」读它来决定展示什么、下载哪个文件、校验什么哈希；
- `extensions/<id>/` —— 每个扩展的源码与 `manifest.json`；
- `.github/workflows/build.yml` —— 把源码编成 `.dshx` 并挂到 Release 上的流水线。

> 这里说的是 **扩展（extension）**，不是插件（plugin）。插件是跑在容器里的 Node.js；
> 扩展是加载进 App 进程的 DEX，跟宿主同权。两者的安全边界完全不同，别混着理解。

## 现在有什么

| id | 名称 | 版本 | 能力 | 说明 |
| --- | --- | --- | --- | --- |
| `dsh-go-shell` | DSH-Go 外壳 | 1.1.0 | `ui.shell` | 把底部四页栏换成顶栏汉堡 + 抽屉（宽屏换成常驻侧栏），深色近黑外观；支持「切回默认外壳」 |

## 怎么装

1. 打开 DSH-Folk；
2. 进「插件」页 → 切到「扩展」分段 → 打开「扩展市场」；
3. 选一个扩展安装，宿主会把 `.dshx` 解到 `files/extensions/<id>/`（`manifest.json` + `classes.dex`）；
4. 首次启用会弹**信任确认**，把 `declaredCaps` 一条条列给你看 —— 扩展与宿主同权，这一步别一路点过去。

也可以手动装：把 `.dshx` 当 zip 解开，两个文件丢进 `/data/data/me.bmax.apatch/files/extensions/<id>/`。
（`.dshx` 就是普通 zip，根目录下只有 `manifest.json` 和 `classes.dex`，没有别的格式魔法。）

卸载/停用：同一个页面里关掉开关即可。停用后 `ExtensionUiRegistry` 里的注册会被整个撤掉。

## 为什么扩展权限这么大，以及安全铁律

扩展由宿主用 `DexClassLoader` 加载，父加载器就是宿主的 classLoader —— 它**跑在 App 进程里**，
能拿到宿主能拿到的一切：root 通道、无障碍、全部文件。这是设计使然（要换外壳、要改界面、
要碰容器文件系统），所以安全全靠下面几条兜底：

1. **三条逃生通道（文件系统级，不依赖 App 界面）**
   在任意一条路径放一个名叫 `.disable_extensions` 的文件，冷启动时宿主会在加载任何扩展之前
   检测到并**禁用全部三方扩展**：
   - `<私有目录>/files/extensions/.disable_extensions`（用本 App 的文件提供器或 MT 管理器建）
   - `Android/data/me.bmax.apatch/files/disable_extensions`（插电脑用 MTP 建，不需要权限）
   - `/sdcard/Download/DSH-Folk/.disable_extensions`（手机自带文件管理器直接建）

   三种写法都认：`.disable_extensions`、`disable_extensions`、`disable_extensions.txt`。

2. **崩溃自动熔断**
   连续崩溃 **3 次**（`ExtensionSafetyGuard.CRASH_THRESHOLD`）时，宿主自动落上面的标志文件并熔断：
   本次进程不再加载任何三方扩展，下次冷启动同样安全。偶发一次崩溃不会把扩展全杀掉。

3. **熔断后界面回到原生样子**
   熔断位一旦置上，`ExtensionUiRegistry` 的所有读取接口返回空 —— 外壳、槽位、整屏接管全部失效，
   保证用户至少还能看到宿主原生界面去关掉扩展。

4. **扩展碰不到熔断文件**
   `ExtensionFs` 的所有写/删操作都会先过安全守卫：标志文件路径一律拒绝。
   否则一个坏扩展可以把「禁用全部扩展」的标志删掉，而那时用户正需要它。

## 给开发者：怎么加一个新扩展

### 目录结构

```
extensions/<id>/
  build.gradle.kts            # com.android.library + compose 编译器
  manifest.json               # 见下表
  src/main/kotlin/<包名>/Xxx.kt   # 实现 DshExtension
```

然后在 `settings.gradle.kts` 里 `include(":extensions:<id>")`。

### manifest.json 字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 目录名。只允许字母数字与 `- _ .`，长度 ≤ 64，不能是 `.` / `..` |
| `name` | 是 | 显示名 |
| `version` | 是 | 版本号，同时决定 Release tag 与产物文件名 |
| `entryClass` | 否 | 实现 `DshExtension` 的全限定类名。留空 = 纯声明式扩展，不加载字节码 |
| `description` | 否 | 一句话说明 |
| `declaredCaps` | 否 | 声明用到的宿主能力，信任确认框逐条展示 |
| `minAppVersion` | 否 | 最低宿主版本，低于它就不让装 |

### 契约从哪来

扩展不依赖发布的 artifact，而是 **composite build** 直接引用宿主源码：

```kotlin
// settings.gradle.kts
includeBuild("../DSH-Folk") {
    dependencySubstitution {
        substitute(module("me.bmax.apatch:ext-api")).using(project(":ext-api"))
    }
}
```

所以 `implementation("me.bmax.apatch:ext-api:1.0")` 里的版本号只是占位，解析前就被换成宿主里的
`:ext-api` 项目 —— 宿主改了 API，这边下次编译立刻报错，不用等装上才发现。

注意：`:ext-api` 里 Compose 是 `implementation`，不传递，扩展模块要自己声明 compose 依赖
（`extensions/dsh-go-shell/build.gradle.kts` 里有现成的写法）。

> 本地开发：把 `DSH-Folk` 放在**兄弟目录**（与本仓库同级），不需要改任何路径。
> 只读宿主源码即可 —— 本仓库不需要、也不应该修改宿主仓库的任何文件。

### 写外壳（ui.shell）的两条规矩

1. **必须渲染宿主给的 `content(Modifier)`** —— 那是宿主原有的整棵页面树。不画它，插件页和设置页就没了；
2. **导航只走 `ShellHost`**（`navigate` / `currentTabId` / `openWebUi`），不要 import 宿主的
   `navController`：那是 `:app` 内部实现，扩展拿不到，也不该依赖。

## CI 做什么

push 到 `main` 或手动 `workflow_dispatch`：

1. 检出本仓库，并把宿主 `DSH-Folk` clone 到 `../DSH-Folk`（复合构建需要它）；
2. JDK 21 + Android SDK（`platforms;android-37`、`build-tools;36.1.0`）；
3. `./gradlew :extensions:dsh-go-shell:assembleRelease` 产出 AAR；
4. 从 AAR 里取出 `classes.jar`，用 SDK 自带的 `d8 --min-api 26` 转成 `classes.dex`；
5. 打包 `dsh-go-shell-<version>.dshx`（zip：`manifest.json` + `classes.dex`）；
6. 发 Release，tag 为 `ext-dsh-go-shell-v<version>`；
7. 用 `sha256sum` 回填 `index.json` 的 `sha256` / `sizeBytes` / `downloadUrl` 并提交回 `main`。

### 关于 index.json 里的 sha256

手写的初版 `index.json` 里 `"sha256": ""`、`"sizeBytes": 0` 是**占位**：哈希只有真正编出
`.dshx` 之后才知道。第一次成功的 CI 已经把它们回填并提交回 `main` 了
（`6453155c…cfad4e`，32123 字节）。市场端仍应把**空哈希当成「未校验」**而不是「校验通过」——
回填永远不会发生在第一次构建之前。

## 构建状态与已知不确定的地方（诚实清单）

**流水线已经跑通**：`Build extensions` 在 2026-11-06 完整跑过一次绿色（编译 → 打包 → 发 Release →
回填 `index.json`），产物 `dsh-go-shell-1.0.0.dshx` 校验过是合法 zip（`manifest.json` + `classes.dex`，
dex 魔数 `dex\n038`，里面确实是本扩展的类）。

把第一次跑挂掉的三处修掉之后才绿的，留个记录免得下次踩回去：

- ~~`android-actions/setup-android@v3`~~ 2026-09 起它必定失败（会去装 Google 已移除的 `tools` 包）；
  改成直接调 `sdkmanager`，只接受许可证 + 装 `platform-tools`；
- ~~`ShellHost.log(...)`~~ `ShellHost` 是导航句柄，没有 `log`（那在 `DshExtensionHost` 上，
  只有 `onLoad` 阶段拿得到）。运行期日志走 `:ext-api` 的 `ExtLog`；
- `compileSdk` 平台名可能是 `android-37` 也可能是 `android-37.1`，按两种都匹配，装不上就交给 AGP。

仍然**没有被验证**的部分（都是运行期，CI 看不出来）：

- 外壳在真机上的实际观感与手势：抽屉、`720dp` 宽屏切换点、深色下的对比度；
- 1.1.0 接上了宿主 `ShellHost` 的 `currentTabStates()` 与 `resetToDefaultShell()`：
  高亮跟着系统返回键等一切真实切页动作走，「切回默认外壳」也真的会切回去。
  这两个接口是 1.1.0 才用上的，**更旧的宿主装不了**；
另外几处是**保守写法**，不是不确定，但值得知道为什么这么写：

- `compileSdk = 37` 与宿主的 `androidCompileSdkVersion` 必须一致（复合构建不会把宿主的
  `extra` 传进来，只能手抄）；宿主升级时改 `extensions/dsh-go-shell/build.gradle.kts` 一处；
- AAR 输出路径 `build/outputs/aar/*.aar` 在 AGP 9 上没变，但脚本仍取「目录里第一个 aar」，
  布局变了也不会直接挂；
- `d8` 不写死路径，取 `$ANDROID_HOME/build-tools/*/d8` 里版本号最大的那个，`--lib` 用最近的
  `android.jar`；
- 扩展模块**刻意不设** `compileOptions` / `kotlin.jvmTarget`：交给 AGP 默认值统一决定，
  免得 Java 与 Kotlin 两边设成不同版本而报 `Inconsistent JVM-target`。产物会被 d8 转成 dex，
  class 文件版本不影响运行。
