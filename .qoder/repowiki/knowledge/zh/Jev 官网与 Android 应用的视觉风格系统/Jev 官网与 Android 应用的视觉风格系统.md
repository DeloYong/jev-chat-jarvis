---
kind: frontend_style
name: Jev 官网与 Android 应用的视觉风格系统
category: frontend_style
scope:
    - '**'
source_files:
    - site/style.css
    - site/guide.css
    - site/DESIGN.md
    - app/src/main/res/values/themes.xml
---

## 1. 体系总览

仓库包含两个前端子项目，各自维护独立的样式系统：

- **site/**（产品官网）：纯静态 HTML + 手写 CSS + 少量 JS，无构建工具、无 CSS 预处理器、无组件库。
- **app/**（Android APK）：基于 Material Components `Theme.MaterialComponents.DayNight.NoActionBar`，通过 Android `values/themes.xml` 声明主题，其余 UI 由 Kotlin Activity/Service 和 XML layout 驱动。

官网的视觉语言在 `site/style.css` 中集中定义，设计意图与约束在 `site/DESIGN.md` 中书面约定；指南页复用同一套变量，额外样式在 `site/guide.css`。Android 应用侧没有自定义 CSS，仅继承 Material DayNight 主题。

## 2. 关键文件

- `site/style.css` — 官网主样式表（约 718 行），含全部设计令牌、响应式断点、组件与动画。
- `site/guide.css` — 生成式指南页（`guides/*.html`）的轻量排版样式，复用 `style.css` 中的 CSS 变量。
- `site/DESIGN.md` — 官方设计说明文档，规定信息架构、视觉概念「批注」、色板 WCAG 对比度、字体策略、栅格、首屏演示行为与无障碍要求。
- `app/src/main/res/values/themes.xml` — Android 主题入口，继承 `Theme.MaterialComponents.DayNight.NoActionBar`。
- `site/i18n.js` / `site/main.js` — 控制语言切换、深色模式、Star 计数与首屏演示动画（与样式强耦合）。

## 3. 架构与约定

### 3.1 设计令牌（Design Tokens）
所有颜色、圆角、阴影、字体族都集中在 `:root` 下的 CSS 自定义属性，例如：

- 背景/文字：`--bg`, `--bg-tint`, `--card`, `--ink`, `--ink-2`, `--ink-3`
- 品牌蓝：`--blue`, `--blue-ink`, `--blue-soft`, `--blue-line`, `--btn-bg`, `--btn-ink`, `--btn-hover`, `--btn-shadow`
- 语义色：`--safe`, `--warn`, `--risk` 及其背景变体（对应危险等级 0–9）
- 圆角：`--r-sm`(10px), `--r`(16px), `--r-lg`(22px)
- 字体：`--font`（Figtree + 中文系统字体栈）、`--ui`、`--mono`
- 阴影：`--shadow-sm`, `--shadow`, `--shadow-lg`

深色主题通过 `@media (prefers-color-scheme: dark)` 与 `:root[data-theme="dark"]` 两套规则覆盖同一组变量，实现「浅色 = 白天聊天」/「深色 = 夜里回消息」的双主题，而非简单反色。手机样机内的聊天界面跟随主题，但 Jev 悬浮窗面板始终使用浅色（与 App 源码一致）。

### 3.2 响应式策略
- 采用移动优先的 `min-width` 媒体查询，断点集中在 520/599/640/720/800/860/900/960/980/1000/1024/1100/1120/1270px。
- 内容宽度用 `.wrap` 限制为 `max-width: 1180px`（宽屏 1244px），内边距按断点递增（16→24→32px）。
- 字号大量使用 `clamp()`，避免固定像素导致小屏过小或大屏过大。
- 布局以 CSS Grid + Flexbox 为主，无第三方栅格框架。

### 3.3 组件命名
类名采用短小、可读的 BEM 风格前缀，按页面区块组织：`.hero`, `.demo`, `.jp`（Jev Panel）、`.mm`（同一个嗯对照卡）、`.jq`（七道题）、`.flow`, `.bound`, `.apps-grid`, `.steps`, `.faq`, `.contact-grid`, `.end`, `.foot` 等。按钮统一为 `.btn`，辅以 `.btn-primary` / `.btn-ghost` / `.btn-lg` / `.btn-sm`。

### 3.4 视觉概念「批注」
`DESIGN.md` 明确定义视觉语言围绕「批注」展开：气泡尾巴标签、虚线引线、0–9 危险等级刻度条均从 App 悬浮窗长出来，不在站点另造图形。首屏演示的手机样机和悬浮窗完全用 HTML/CSS 重绘，结构、文案、颜色、字号按 `overlay/OverlayController.kt` 源码还原。

### 3.5 无障碍与动效
- 焦点环：`:focus-visible { outline: 2.5px solid var(--blue); outline-offset: 3px; }`
- 减少动效：`@media (prefers-reduced-motion: reduce)` 关闭所有 transition/animation。
- 打印：隐藏导航、演示控件、收尾色带与跳过链接。
- 演示动画默认渐进增强：无 JS 时停在「收尾状态」（悬浮窗全开、第 1 候选选中、输入框已填、发送键被圈出）。

### 3.6 Android 端样式
Android 模块仅声明 `Theme.JevProbe` 继承 `Theme.MaterialComponents.DayNight.NoActionBar`，未提供自定义 colors/dimens/styles 资源。UI 主要由 Kotlin 代码（`MainActivity.kt`, `SettingsActivity.kt`, `KnowledgeActivity.kt`, `capture/ChatCaptureService.kt`, `overlay/OverlayController.kt`）动态绘制，因此不属于本仓库的 CSS 风格范畴。

## 4. 约定与约束（来自代码与设计文档）

- **令牌来源**：`style.css` 顶部注释写明「令牌在开头；浅色、深色两套分开设计」。所有颜色必须通过 `var(--*)` 引用，禁止在正文样式中硬编码色值。
- **深色不是反色**：`DESIGN.md` 第 3 节「色板」明确「深色是『夜里回消息』，不是反色」，并给出各令牌的浅色/深色值及 WCAG 对比度。
- **字体策略**：中文只用系统字体（苹方/鸿蒙/MiSans/微软雅黑/思源黑体），不下载中文字体；英文标题用 Figtree（Google Fonts，可变字重 400–800，只下拉丁子集）。
- **首屏演示**：「手机样机和悬浮窗都是 HTML/CSS 画的，不是截图」，且「悬浮窗的结构、文案格式、颜色、字号按 App 源码 `overlay/OverlayController.kt` 还原」。
- **WCAG 2.2.2**：演示提供「暂停演示」按钮以满足可停止要求。
- **无障碍语义**：`header/nav/main/section[aria-labelledby]/footer`；步骤用 `ol`；问答用原生 `details`；对比表用真 `table`；演示区 `aria-hidden`。
- **多语言**：文案单一来源在 `i18n.js`，`index.html` 里的中文由 `tools/check-i18n.mjs --fix` 写入；`privacy.html` 保持中文，英文模式下隐私链接指向其页底 `#en-summary`。
- **获取链接**：所有获取/安装教程/交流入口静态写死到 GitHub 仓库根 URL，不带章节锚点，保证无 JS 可用。
- **指南页**：`guide.css` 仅补充排版，强制复用 `style.css` 的 CSS 变量（如 `--line`, `--card`, `--ink-2`），不得自创主题变量。

## 5. 结论

该仓库的 frontend_style 由两部分组成：官网使用自维护的 CSS 变量 + 手写组件 + 详尽的设计文档；Android 应用则依赖 Material Components DayNight 主题，自身不提供 CSS 样式层。两者通过共享的「批注」视觉概念与蓝色系语义色保持跨端一致性。