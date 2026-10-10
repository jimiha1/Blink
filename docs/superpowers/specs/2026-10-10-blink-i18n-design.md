# Blink 中英文语言选项与中文界面 — 设计

日期：2026-10-10
状态：待用户审阅
路径分类：architectural（引入新的本地化子系统）

## 目标

给 Blink 增加 App 内语言开关（跟随系统 / 简体中文 / English），并把现有全部英文界面译成中文，使中文用户打开即见中文。

## 现状（勘察结论）

- 项目**没有任何 i18n 基础设施**：`res/values/strings.xml` 只有一条 `app_name`；没有 `values-zh`；没有 locale 配置。
- 约 135 条用户可见文案**硬编码在 Kotlin 字面量**里，分布在 5 个文件：
  `SettingsScreen.kt`(50)、`MainActivity.kt`(21)、`RestOverlayService.kt`(17)、
  `ShizukuUtils.kt`(3 条用户可见)、`EyeCareService.kt`(2 条通知文案)。
- `minSdk = 34`、`compileSdk = 35`、AGP 8.7.3、Kotlin 2.1。
- 无 appcompat 依赖；`AndroidManifest` 的 `android:label` 走 `@string/app_name`。

## 决策（已与用户确认）

1. **切换方式**：设置页内三选一（System / 简体中文 / English）。
2. **翻译范围**：全部界面，**含** `Device-Specific` 厂商电池指引。
3. **应用名**：`app_name` 保持 `Blink`，不因语言变化。

## 技术选型：原生 LocaleManager（方案 A）

用 Android 13+（API 33）的 `LocaleManager.setApplicationLocales()`，配合 AGP 的
`generateLocaleConfig` 自动生成 `locales_config.xml`。

选它的理由：`minSdk 34` 已远超 API 33，该 API 原生可用，**零新增依赖**；系统设置里会
自动出现「Blink 的应用语言」入口。放弃 `AppCompatDelegate.setApplicationLocales()`
（需新增 appcompat 依赖，而本项目根本用不上它的向下兼容）；放弃自研语言包
（重复造轮子，拿不到系统每应用语言集成）。

## 设计

### 1. 语言切换机制

- `app/build.gradle.kts`：加 `androidResources { generateLocaleConfig = true }`。
  由 AGP **自动生成** `locales_config.xml`，内容取自资源目录，因此**无需手写**该文件；
  建了 `values-zh-rCN` 后它即声明 `en` + `zh-rCN`。不设置
  `resourceConfigurations`（保持默认，允许系统语言回退到默认英文）。
- `AndroidManifest.xml`：`<application android:localeConfig="@xml/locales_config">`。
- `PrefsManager`：新增键 `app_language`（`system` | `zh` | `en`，默认 `system`），
  配 `getAppLanguage()` / `setAppLanguage()`。
- `EyeCareApp.onCreate()`：读偏好并调用一次 `LocaleManager.setApplicationLocales()`，
  做防御性对齐（LocaleManager 自身已持久化，重启后仍生效）。
- `SettingsScreen`：新增 **Language** 三选一（`SingleChoiceSegmentedButtonRow`，
  与 Alert Mode / Overlay Style / Overlay Theme 同款）。选中即调
  `LocaleManager.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`，
  系统自动重建界面，即时生效。
  - `system` → `LocaleListCompat.getEmptyLocaleList()`（清空，跟随系统）
  - `zh` → `forLanguageTags("zh-CN")`（与 `values-zh-rCN` 目录对应）
  - `en` → `forLanguageTags("en")`
- 需要的 import：`android.app.LocaleManager`、`android.os.LocaleList`、
  `androidx.core.os.LocaleListCompat`（core-ktx 已含）。

### 2. 文案抽取

- 新建 `res/values/strings.xml`（英文，默认）与 `res/values-zh-rCN/strings.xml`（中文）。
- 抽取规则：
  - **只抽用户可见文案**：Compose `Text(...)`、`contentDescription`、通知标题/正文、
    设置项标题与说明、按钮、厂商指引步骤。
  - **不抽开发者日志**：`Log.d(TAG, "...")` 一律保留英文，不翻译、不抽取。
  - 带变量的用占位符：如 `String.format("%02d:%02d remaining", m, s)` →
    `<string name="notification_remaining">%1$02d:%2$02d remaining</string>`；
    单位与百分比（`min` / `sec` / `%`）同样抽成带占位符的字符串。
  - 命名：`snake_case`，按界面前缀分组（如 `settings_*`、`home_*`、`overlay_*`、
    `notif_*`、`oem_*`）。

### 3. 厂商电池指引

8 个厂商（Xiaomi/Samsung/Huawei-Honor/OPPO-Vivo/Motorola/ASUS/Nokia）的标题、说明与
步骤全部译成中文。**译法约定**：设置菜单路径保留英文原词并用「>」分隔，外层用中文
说明，例如「设置 > 应用管理 > 应用列表 > Blink > 自启动 > 允许」。理由：各 ROM 的
中文菜单名不统一，硬译反而对不上实际界面。

### 4. 应用名

`app_name` 保持 `Blink`，`values-zh-rCN/strings.xml` 不覆盖它。

## 改动文件

- 新增：`res/values-zh-rCN/strings.xml`、`res/xml/locales_config.xml`（或由 AGP 生成）
- 修改：`res/values/strings.xml`、`app/build.gradle.kts`、`AndroidManifest.xml`、
  `PrefsManager.kt`、`EyeCareApp.kt`、`SettingsScreen.kt`、`MainActivity.kt`、
  `RestOverlayService.kt`、`EyeCareService.kt`、`ShizukuUtils.kt`

## 验证方案

模拟器在本容器不可用（无 `/dev/kvm`，见 memory `blink-android-build-env`），验证走
真机 Honor BKQ-AN10 + 无线 ADB（见 memory `blink-verify-on-device`）。

客观判据（不靠肉眼）：

1. 编译 `:app:assembleDebug` 通过。
2. 安装后分别设置三档语言，用 `uiautomator dump` 抓 `text=` 节点，断言：
   - `en` → 出现 `Settings` / `Timers` / `Work Duration`
   - `zh` → 出现 `设置` / `计时` / `工作时长`
   - `system` → 随系统语言（`adb shell cmd uimode`/locale 切换后断言跟随）
3. 用 `adb shell dumpsys activity` 或 `dumpsys package com.cflat.blink | grep locale`
   核对 per-app locale 已生效。
4. 休息悬浮层文案同理：触发后 dump，断言出现对应语言的 `Rest Your Eyes` / `休息一下`。
5. 抽检通知文案与厂商指引段落的语言。

## 非目标（YAGNI）

- 不引入 appcompat 或任何第三方 i18n 库。
- 不做简繁区分（仅 `zh-rCN`）。
- 不翻译日志与代码注释。
- 不做应用名中文化。
- 不新增除语言以外的设置项。
