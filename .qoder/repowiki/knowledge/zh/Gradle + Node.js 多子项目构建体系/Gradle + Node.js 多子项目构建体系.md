---
kind: build_system
name: Gradle + Node.js 多子项目构建体系
category: build_system
scope:
    - '**'
source_files:
    - build.gradle.kts
    - settings.gradle.kts
    - gradle.properties
    - app/build.gradle.kts
    - site/tools/build-guides.mjs
    - site/tools/build-discovery.mjs
    - site/MAINTAINING.md
    - apk/jev-assistant-v1.4-release.apk
---

## 1. 使用的构建系统

仓库是三个独立子项目的聚合根，分别使用不同的构建工具：

- **Android 应用 (`app/`)**：基于 Gradle Kotlin DSL（`build.gradle.kts`），使用 Android Gradle Plugin `8.7.3` 与 Kotlin `1.9.24`。通过 `settings.gradle.kts` 以单模块聚合工程形式注册 `:app`。
- **官网站点 (`site/`)**：纯静态 HTML/CSS/JS 站点，无框架；发布前用 Node.js ESM 脚本生成多语言页面、sitemap、robots.txt、llms.txt 等发现文件。
- **Python 工具 (`tools/jev/`)**：无构建步骤，直接以脚本运行（`calibrate.py`、`jev_client.py` 等）。

仓库根没有 Makefile、Dockerfile、CI pipeline 或 release 脚本——所有构建都通过 Gradle Wrapper (`gradlew`) 和 Node.js 脚本手动触发。

## 2. 关键文件

- `build.gradle.kts`：顶层 Gradle 配置，声明 Android/Kotlin 插件版本并 `apply false`，由子模块自行 apply。
- `settings.gradle.kts`：集中管理 pluginManagement 与 dependencyResolutionManagement 的仓库源（`google()`、`mavenCentral()`、`gradlePluginPortal()`），并通过 `RepositoriesMode.FAIL_ON_PROJECT_REPOS` 禁止子模块再声明仓库。
- `gradle.properties`：全局 JVM 参数（`-Xmx2048m`）、并行构建、缓存开关、AndroidX 与非传递 RClass 开关。
- `app/build.gradle.kts`：Android 模块核心构建逻辑（见下节架构）。
- `site/tools/build-guides.mjs`：从 `content/guides.json` 生成每篇指南的中英双语 HTML。
- `site/tools/build-discovery.mjs`：从 `index.html` 与 `content/guides.json` 生成 `en.html`、`sitemap.xml`、`robots.txt`、`llms.txt`。
- `apk/jev-assistant-v1.4-release.apk`：预构建的已签名 APK 产物，随仓库一起发布。

## 3. 架构与设计决策

### Android 构建 (`app/build.gradle.kts`)

- **命名空间与应用 ID**：`com.jev.probe`，`namespace` 与 `applicationId` 一致。
- **SDK 目标**：`compileSdk=35`、`targetSdk=35`、`minSdk=30`（Android 11+）。
- **ABI 裁剪**：仅保留 `arm64-v8a`，因为 ML Kit 中文 OCR 原生库为每个 ABI 打包，而目标设备是 arm64，其余 ABI 被视为“dead weight”。
- **JDK 版本**：Java/Kotlin 编译目标均为 `17`（`sourceCompatibility`/`targetCompatibility`/`jvmTarget`）。
- **JNI 打包**：`packaging.jniLibs.useLegacyPackaging = false`，启用页面对齐的 `.so` 直映射，满足 Android 15+ 16KB page-size 设备要求。
- **混淆**：release 类型 `isMinifyEnabled = false`，未启用 R8/ProGuard。
- **签名**：release 签名密钥属性从外部 properties 文件读取，路径由环境变量 `JEV_KEYSTORE_PROPS` 指定；若未提供则不配置签名，release 构建为 unsigned。
- **依赖来源**：统一在 `settings.gradle.kts` 中声明 `google()` 与 `mavenCentral()`，子模块不得再声明仓库。

### 官网站点构建 (`site/tools/*.mjs`)

- `build-guides.mjs`：遍历 `content/guides.json` 中的文章，为每篇生成 `guides/<slug>.html` 与 `guides/<slug>.en.html`，注入 Schema.org JSON-LD、Open Graph、Twitter Card、canonical/hreflang 标签。
- `build-discovery.mjs`：扫描 `index.html` 中的 i18n 标记，根据 `content/guides.json` 与当前 `host`（`chatjevs.com` 或 `brewreel.com`）生成 `en.html`、`sitemap.xml`、`robots.txt`、`llms.txt`。
- 两个脚本均支持 `--check` 模式：比对生成的内容与磁盘上已有内容，不一致时输出错误并 `process.exit(1)`，用于 CI 校验。
- 站点部署到 GitHub Pages，`site/MAINTAINING.md` 明确说明：`main` 分支直出，无需构建；维护时需先运行上述脚本生成英文页与发现文件，再同步到 `jev-chat-jarvis` 仓库的 `site/` 目录与 `gh-pages` 分支。

### Python 工具

`tools/jev/` 下的 `.py` 文件（`calibrate.py`、`probe_background_field.py`、`questions.py`、`jev_client.py`、`demo_meme.py`）无构建步骤，作为独立脚本运行。

## 4. 约定与约束

- **Gradle 仓库源集中化**：`settings.gradle.kts` 通过 `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 强制禁止子模块再声明仓库，所有依赖必须走 `google()` / `mavenCentral()`。（来源：`settings.gradle.kts`）
- **Android 仅支持 ARM64**：`ndk { abiFilters += listOf("arm64-v8a") }` 显式剔除 x86/x64/armabi-v7a，减小 APK 体积。（来源：`app/build.gradle.kts`）
- **Release 签名密钥不出仓**：签名属性文件路径默认指向本地绝对路径 `H:/android/keys/jev-release.properties`，并通过 `JEV_KEYSTORE_PROPS` 环境变量覆盖；未提供时 release 构建不签名。（来源：`app/build.gradle.kts`）
- **JDK 17 是硬性要求**：`gradle.properties` 注释明确要求通过 `JAVA_HOME` 指向 JDK 17，且 `org.gradle.java.home` 不得提交（机器相关）。（来源：`gradle.properties`）
- **官网发现文件必须可再生成**：`build-guides.mjs` 与 `build-discovery.mjs` 的 `--check` 模式会拒绝任何与源码不同步的生成产物，确保磁盘上的 HTML/sitemap/robots/llms.txt 始终可由脚本重建。（来源：`site/tools/build-guides.mjs`、`site/tools/build-discovery.mjs`）
- **APK 随仓库发布**：`apk/jev-assistant-v1.4-release.apk` 是已签名的 release 产物，README 引导用户从此处下载，而非从 CI 拉取。（来源：`README.md`、`apk/`）
- **无 CI/CD 流水线**：仓库未发现 `.github/workflows`、`.gitlab-ci.yml`、`Jenkinsfile`、`Dockerfile` 等自动化构建配置；构建与发布目前为手工流程。
- **版本编号策略**：Android 应用的 `versionCode=5`、`versionName="1.4"` 硬编码在 `app/build.gradle.kts` 中，与 APK 文件名 `jev-assistant-v1.4-release.apk` 保持一致；版本号未在共享配置文件中统一管理。