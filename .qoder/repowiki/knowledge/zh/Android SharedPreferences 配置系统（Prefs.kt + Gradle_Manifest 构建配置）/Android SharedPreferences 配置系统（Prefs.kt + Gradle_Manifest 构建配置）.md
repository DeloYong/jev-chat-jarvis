---
kind: configuration_system
name: Android SharedPreferences 配置系统（Prefs.kt + Gradle/Manifest 构建配置）
category: configuration_system
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/core/Prefs.kt
    - app/build.gradle.kts
    - gradle.properties
    - settings.gradle.kts
    - build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values/themes.xml
    - app/src/main/res/xml/config_disguised.xml
---

## 1. 使用的系统与框架

- **运行时用户配置**：Android `SharedPreferences`，通过自封装的 `com.jev.probe.core.Prefs` 类提供类型安全的 getter/setter。
- **构建期配置**：Gradle Kotlin DSL（`build.gradle.kts` / `settings.gradle.kts`），签名密钥从仓库外的 `.properties` 文件加载，路径由环境变量 `JEV_KEYSTORE_PROPS` 覆盖。
- **Android 清单与资源**：`AndroidManifest.xml`、`res/values/strings.xml`、`res/xml/config_disguised.xml` 等 Android 原生资源。
- **Python 工具侧**：无集中配置文件，参数通过命令行或硬编码常量传递（见 `tools/jev/` 下的脚本）。

## 2. 关键文件

- `app/src/main/java/com/jev/probe/core/Prefs.kt` — 唯一的应用级运行时配置入口。
- `app/build.gradle.kts` — 应用版本、SDK、ABI 过滤、依赖、签名属性加载。
- `gradle.properties` — 全局 Gradle 开关（JVM args、AndroidX、Kotlin 风格）。
- `settings.gradle.kts` — 仓库根项目名、模块包含、Maven 源。
- `app/src/main/AndroidManifest.xml` — 应用 ID、权限、Service/Activity 声明。
- `app/src/main/res/values/strings.xml`、`themes.xml` — 字符串与主题资源。
- `app/src/main/res/xml/config_disguised.xml` — 伪装成无障碍配置的 XML（用于悬浮窗/辅助功能）。 

## 3. 架构与约定

### 3.1 Prefs.kt：SharedPreferences 的单点封装

`Prefs` 是应用私有配置的唯一访问点，所有键集中在 companion object 中定义（`K_*` 常量），值以 `String`/`Boolean`/`Int`/`Set<String>` 形式读写。按功能域分组：

| 域 | 字段 | 默认值/行为 |
|---|---|---|
| judge（决策路由） | `judgeProvider`、`judgeBaseUrl`、`judgeKey`、`judgeModel` | provider 支持 `bocha`/`openrouter`/`typesafe`/`vercel`/`zen`/`custom`；`DEFAULT_JUDGE_BASE_OPENROUTER = "https://openrouter.ai/api"` |
| reply（回复生成） | `replyBaseUrl`、`replyKey`、`replyModel` | 默认 `openrouter.ai/api/v1` + `deepseek/deepseek-chat-v3.1` |
| vision（OCR 视觉） | `visionBaseUrl`、`visionKey`、`visionModel` | 默认 `openrouter.ai/api/v1` + `qwen/qwen2.5-vl-72b-instruct` |
| context（D 阶段上下文） | `contextEnabled`、`contextHistoryCount`、`autoSummary` | 默认关闭，不写盘除非用户主动开启 |
| OCR（B 阶段） | `ocrEngine`、`ocrForUnknownApps`、`ocrFallback`、`ocrAutoAnalyze` | engine 支持 `mlkit`/`vision` |
| 通用 | `relationship`、`enabled`、`whitelist`、`overlayOpacity`、`bubbleX/Y`、`autoAnalyze` | 关系描述默认中文；白名单为空表示允许全部对话 |

**端点构造约定**：
- `judgeEndpoint()`：根据 provider 拼接 `/v1/systemone` 或 `/alpha/decisions`。
- `replyEndpoint()`：`baseUrl/chat/completions`。
- `visionEndpoint()`：独立于 reply base，空时回退到 OpenRouter 默认。
- key 回退链：`effectiveReplyKey() = replyKey.ifBlank { judgeKey }`；`effectiveVisionKey() = visionKey.ifBlank { effectiveReplyKey() }`。

**迁移与种子逻辑**：
- `migrateIfNeeded()`：v1.2 → v1.3 将旧 `openrouter_key` 迁移到 `judge_key`，仅执行一次（`prefs_migrated_v13` 标记）。
- `unseedBochaDefaultIfUnconfigured()`：v1.4.0 曾自动给新安装写入 Bocha Jev，v1.4.1 撤销该种子，仅当用户从未配置过任何 provider 时才生效。

**安全约定**（代码注释明确声明）：
- 使用 `Context.MODE_PRIVATE` 存储，非世界可读。
- 永不记录完整 key，只记录 key 长度（`legacy.length`、`current.length`）。
- 真实配置实例名为 `jev_assistant`，测试按钮和 KB self-check 使用临时实例名以避免污染。

### 3.2 Gradle 构建配置

- `app/build.gradle.kts`：
  - `applicationId = "com.jev.probe"`，`minSdk = 30`，`targetSdk = 35`，`versionCode = 5`，`versionName = "1.4"`。
  - NDK 仅保留 `arm64-v8a`（ML Kit 中文识别器需要 arm64 native lib）。
  - 签名属性从 `JEV_KEYSTORE_PROPS` 环境变量指向的文件加载，若不存在则 release 构建不签名。
  - `packaging.jniLibs.useLegacyPackaging = false`，为 Android 15+ 16KB page-size 设备启用 mmap。
  - Java/Kotlin 目标均为 17。
- `gradle.properties`：固定 `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`，开启并行与缓存，禁用 transitive R class。
- `settings.gradle.kts`：强制 `RepositoriesMode.FAIL_ON_PROJECT_REPOS`，仅允许 `google()` 与 `mavenCentral()` 两个源。

### 3.3 Android 资源

- `res/values/strings.xml`、`themes.xml` 存放 UI 文案与主题。
- `res/xml/config_disguised.xml` 作为无障碍服务的伪装配置（文件名刻意伪装）。

## 4. 观察到的约定与约束

- **运行时配置全部走 `Prefs`**：没有散落的 `getSharedPreferences(...)` 调用，所有键集中定义在 `Prefs.Companion` 的 `K_*` 常量中。
- **provider 枚举集中管理**：`PROVIDER_BOCHA`/`PROVIDER_OPENROUTER`/`PROVIDER_TYPESAFE`/`PROVIDER_VERCEL`/`PROVIDER_ZEN`/`PROVIDER_CUSTOM` 在 `Prefs` 内声明，新增 provider 需同时更新 `judgeEndpoint()` 的 when 分支。
- **key 回退策略统一**：reply key 回退到 judge key；vision key 回退到 reply key 再回退到 judge key，避免重复输入。
- **隐私优先默认值**：context/history 默认关闭（`contextEnabled = false`）、OCR auto-analyze 默认关闭，只有用户显式开启才产生额外开销或写盘。
- **迁移幂等**：`migrateIfNeeded()` 和 `unseedBochaDefaultIfUnconfigured()` 均通过布尔标记保证只运行一次。
- **构建密钥不出仓库**：签名 keystore 属性文件路径由 `JEV_KEYSTORE_PROPS` 环境变量指定，默认指向本地绝对路径 `H:/android/keys/jev-release.properties`，不在 git 中提交。
- **Gradle 仓库源锁定**：`FAIL_ON_PROJECT_REPOS` 禁止子模块自行添加 Maven 源，所有依赖必须经根 `settings.gradle.kts` 声明。
- **API 地址与模型 ID 集中化**：所有 provider 的 default base URL 与 model id 集中在 `Prefs` companion 中，新增 provider 需在此处追加常量并在 `judgeEndpoint()` 处理。
- **Python 工具无配置文件**：`tools/jev/` 下脚本（`calibrate.py`、`jev_client.py`、`questions.py` 等）未引入集中配置加载，参数通过函数参数或硬编码常量传递，不属于本仓库的“配置系统”范畴。