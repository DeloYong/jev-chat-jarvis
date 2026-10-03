---
kind: error_handling
name: 跨模块错误处理：Kotlin ApiException + Python JevError 双端统一异常与重试策略
category: error_handling
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/jev/HttpJson.kt
    - app/src/main/java/com/jev/probe/jev/ResponseShape.kt
    - tools/jev/jev_client.py
    - tools/jev/calibrate.py
    - site/main.js
---

## 1. 采用的体系

仓库包含三个子项目（Android Kotlin、Python 判断工具、前端静态站点），每个语言层各自定义了一个顶层异常类型，并在网络 I/O 处集中捕获并转换为该异常；上层调用方只感知业务语义错误，不直接处理底层 `IOException` / `URLError`。

- Android (Kotlin)：`com.jev.probe.jev.ApiException : RuntimeException`，携带 `route`（判断/回复/视觉接口）、HTTP status、响应体前 120 字符以及 `retryable` 标志。
- Python 工具：`tools/jev/jev_client.py` 中的 `JevError(Exception)`，同样附带 `status: int | None`。
- 前端站点：无专用错误类，仅在 `site/main.js` 中用裸 `try/catch` 包裹埋点与 `localStorage` 访问，失败即静默忽略。

## 2. 关键文件

| 层级 | 文件 | 职责 |
|---|---|---|
| Android | `app/src/main/java/com/jev/probe/jev/HttpJson.kt` | HTTP 请求封装、指数退避重试、所有失败归一化为 `ApiException` |
| Android | `app/src/main/java/com/jev/probe/jev/ResponseShape.kt` | 校验 2xx 响应体结构，把“成功状态码但业务失败”的响应转为 `ApiException` |
| Android | `app/src/main/java/com/jev/probe/jev/HttpJson.kt` 中的 `Route` object | 定义 `JUDGE` / `REPLY` / `VISION` 三个路由常量，用于错误文案区分 |
| Python | `tools/jev/jev_client.py` | `JevError` 定义、`ask()` 重试逻辑、API key 脱敏 |
| Python | `tools/jev/calibrate.py` | 消费 `JevError`，将错误写入校准报告 JSON/Markdown |
| 前端 | `site/main.js` | 仅对埋点和 localStorage 做 try/catch 兜底 |

## 3. 架构与约定

### 3.1 Android 层

- **统一异常**：`ApiException` 继承 `RuntimeException`，由 `HttpJson.post` 和 `ResponseShape.*` 抛出。构造消息格式为 `"$route HTTP $status：${snippet.take(120)}"` 或 `"$route 请求失败：${snippet.take(120)}"`。
- **重试策略**：`MAX_ATTEMPTS = 3`，对 HTTP 429/529 以及未知异常执行指数退避（`Thread.sleep(500L * (1L shl attempt))`）。`InterruptedException` 被显式重新中断后向上抛出，不被视为可重试。
- **不可重试标记**：`retryable = false` 用于配置错误或协议不匹配（如 Anthropic messages 格式、非 OpenAI 兼容地址），避免重复浪费三次往返。
- **响应体校验**：`ResponseShape.ok` 在 2xx 状态下检查 `error` 对象、`success=false`、`code >= 400` 等字段，全部转为 `ApiException(retryable=false)`；`chatContent`、`jevAnswers`、`threeCandidates` 也按相同模式抛错。
- **用户可见文案**：`describe(e)` 把底层异常翻译为中文提示（超时、域名解析失败、无法连接、SSL 证书校验失败），且 `Authorization` 头从不进入日志或错误文本。
- **调用方捕获**：`SettingsActivity`、`ChatCaptureService`、`MlKitOcr`、`ScreenCapture` 使用 `try { ... } catch (e: Exception)` 捕获异常，将其转为本地化字符串显示到 UI，而不是让崩溃传播到系统。

### 3.2 Python 层

- **统一异常**：`JevError(Exception)` 带可选 `status` 字段，由 `_api_key()`、`ask()` 以及 `calibrate.py` 内部校验函数（`answers_of`、`score_value` 等）抛出。
- **重试策略**：`MAX_RETRIES = 3`，对 HTTP 429/529、`TimeoutError`/`socket.timeout`、`URLError` 执行指数退避（`time.sleep(2**attempt)`），最终耗尽则抛出 `JevError("exhausted retries")`。
- **密钥脱敏**：`redact_secrets()` 从任意字符串中替换环境变量中的 `OPENROUTER_API_KEY` 为 `[REDACTED]`，所有错误信息、JSON 报告、Markdown 报告都经此函数过滤后再落盘。
- **调用方捕获**：`calibrate.py::main` 对每个 case 单独 `try/except JevError`，将错误记录为 `{ok: False, http_status, error}` 行，不影响其他 case 继续运行。

### 3.3 前端层

`site/main.js` 中只有三处裸 `try/catch`：
- 埋点调用 `window.umami.track` 失败时忽略；
- `localStorage.getItem/setItem` 失败返回 `null`/空；
- GitHub token 缓存解析失败回退到未登录态。
这些是“功能降级”而非“错误上报”，没有统一的错误类型。

## 4. 观察到的约定与约束

- **网络层集中转换**：Android 的 `HttpJson.post` 与 Python 的 `ask()` 是各自模块唯一的网络入口，所有底层异常在此处被包装成 `ApiException` / `JevError`，上层代码不再直接 `catch IOException` 或 `urllib.error.HTTPError`。
- **429/529 指数退避**：两端实现一致——最多 3 次尝试，间隔随尝试次数翻倍（Kotlin 侧 `500ms * 2^attempt`，Python 侧 `2^attempt` 秒）。
- **4xx 客户端错误不重试**：`HttpJson.post` 中 `e.status in 400..499` 直接抛出，不进入重试循环；Python 侧对 401/422/429/529 走固定映射文案，其余 4xx 也直接抛 `JevError`。
- **成功状态码 ≠ 成功**：`ResponseShape.ok` 明确拒绝 2xx 中携带 `error` 对象、`success=false` 或 `code >= 400` 的响应，防止网关把 404 包装成 200 后穿透到 UI。
- **错误文案面向用户**：Android 侧 `describe()` 把技术异常翻译成中文；Python 侧 `redact_secrets()` 确保任何输出都不泄露 API Key。
- **UI 层吞掉异常并展示**：`SettingsActivity`、`ChatCaptureService` 等把异常转成用户可读字符串（如 `"保存失败：${e.javaClass.simpleName}"`、`"自检异常：..."`），不会让应用崩溃。
- **前端错误静默降级**：`site/main.js` 的 `try/catch` 块内为空或返回默认值，属于“容错”而非“错误处理”，不产生日志或告警。
- **测试用例覆盖部分错误路径**：`app/src/test/java/com/jev/probe/jev/ResponseShapeTest.kt` 验证 `ResponseShape` 的错误分支；Python 侧未见针对 `JevError` 的单元测试。