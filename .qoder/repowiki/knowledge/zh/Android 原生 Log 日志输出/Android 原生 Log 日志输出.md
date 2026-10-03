---
kind: logging_system
name: Android 原生 Log 日志输出
category: logging_system
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt
    - app/src/main/java/com/jev/probe/core/kb/KbStore.kt
    - app/src/main/java/com/jev/probe/core/kb/ContextBuilder.kt
    - app/src/main/java/com/jev/probe/core/Prefs.kt
    - app/src/main/java/com/jev/probe/SettingsActivity.kt
    - app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt
    - app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt
    - app/src/main/java/com/jev/probe/jev/JudgeClient.kt
    - app/src/main/java/com/jev/probe/overlay/OverlayController.kt
---

## 1. 使用的系统/方案

仓库中的 Android 应用（`app/src/main/java/com/jev/probe/...`）仅使用 Android SDK 自带的 `android.util.Log`，未引入任何第三方日志框架（如 SLF4J、Logback、Timber、Stetho 等）。Python 工具子项目（`tools/jev/`）也未见日志库导入。因此本仓库不存在统一的日志框架、结构化日志字段或集中式 sink 路由。

## 2. 关键文件

所有日志调用集中在以下 Kotlin 源文件中：
- `app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt` — 无障碍捕获与 OCR 流程的主要日志来源
- `app/src/main/java/com/jev/probe/core/kb/KbStore.kt` — 本地知识库持久化日志
- `app/src/main/java/com/jev/probe/core/kb/ContextBuilder.kt` — 上下文构建日志
- `app/src/main/java/com/jev/probe/core/Prefs.kt` — 偏好迁移与回退日志
- `app/src/main/java/com/jev/probe/SettingsActivity.kt` — 设置页打开日志
- `app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt` — OCR 失败日志
- `app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt` — 截图 API 不可用日志
- `app/src/main/java/com/jev/probe/jev/JudgeClient.kt` — 判断客户端日志
- `app/src/main/java/com/jev/probe/overlay/OverlayController.kt` — 悬浮窗异常日志

## 3. 架构与约定

- **统一 TAG**：每个含日志的类都声明一个私有常量 `private const val TAG = "JEVASSIST"`（或 companion object 中定义），所有 `Log.*` 调用均以该 TAG 作为第一个参数。目前所有类的 TAG 值均为字符串字面量 `"JEVASSIST"`，没有按模块分前缀。
- **日志级别分布**：主要使用 `Log.i`（信息）、`Log.w`（警告）和少量 `Log.d`（调试）；`Log.e` 仅在 `OverlayController` 中用于悬浮窗 addView 失败的异常路径，未见 `Log.v`。
- **消息格式**：采用 Kotlin 字符串模板拼接，常见模式为 `"key=$value"` 键值对形式（例如 `"capture service connected"`、`"prefs migrated judgeKey.len=${prefs.judgeKey.length}"`、`"appendLog contact=$contactId added=${tail.size} overlap=$k total=${list.size} ok=$ok"`），但并非 JSON 或其他结构化序列化格式。
- **异常处理**：捕获异常后通常记录 `e.javaClass.simpleName` 或 `e.message`，而不是堆栈轨迹，避免在正常错误路径上产生过大的日志。
- **无全局初始化**：未发现 Application 类或日志框架初始化代码；日志直接由各个组件自行调用。

## 4. 约定与约束

- **约定（描述性）**：所有需要输出的日志均通过 `android.util.Log`，并使用类内 `TAG` 常量作为标签。
- **约束（可验证）**：仓库中没有引入任何第三方日志依赖（build.gradle.kts 中无相关依赖），也没有自定义 Logger 抽象或拦截器；日志输出是散落在业务类中的裸调用，不存在集中式配置或过滤规则。