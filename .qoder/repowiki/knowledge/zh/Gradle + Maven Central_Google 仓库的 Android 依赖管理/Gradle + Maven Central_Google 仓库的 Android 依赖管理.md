---
kind: dependency_management
name: Gradle + Maven Central/Google 仓库的 Android 依赖管理
category: dependency_management
scope:
    - '**'
source_files:
    - settings.gradle.kts
    - build.gradle.kts
    - app/build.gradle.kts
    - gradle.properties
---

## 1. 使用的系统

仓库使用 **Gradle (Kotlin DSL)** 作为唯一的构建与依赖解析工具，Android 应用通过 `com.android.application` 插件管理 APK 产物。Python 侧 (`tools/jev/`) 和站点侧 (`site/`) 没有引入任何包管理器（无 `requirements.txt`、`pyproject.toml`、`package.json`），仅通过标准库或相对路径脚本运行。

## 2. 关键文件

- `settings.gradle.kts`：集中声明插件与依赖仓库源，启用 `dependencyResolutionManagement` 并设置 `RepositoriesMode.FAIL_ON_PROJECT_REPOS`，禁止子模块自行添加仓库。
- `build.gradle.kts`（根）：以 `apply false` 方式在聚合层声明插件版本——AGP `8.7.3`、Kotlin `1.9.24`。
- `app/build.gradle.kts`：唯一包含业务依赖声明的模块，定义 `implementation` / `testImplementation` 依赖及签名、ABI 过滤等打包配置。
- `gradle.properties`：全局 Gradle JVM 参数、AndroidX 开关、并行与缓存开关。
- `gradle/wrapper/gradle-wrapper.properties`：由 Gradle Wrapper 管理的 Gradle 发行版版本。

## 3. 架构与约定

### 仓库源
`settings.gradle.kts` 中统一声明三个上游仓库：`google()`、`mavenCentral()`、`gradlePluginPortal()`（插件用）。所有依赖解析均走这三个源，子模块不得新增仓库。

### 依赖分类
`app/build.gradle.kts` 将依赖分为两类：
- `implementation`：运行时可见的三方库（`androidx.core:core-ktx`、`androidx.appcompat`、`material`、`constraintlayout`、`com.google.mlkit:text-recognition-chinese`）。
- `testImplementation`：仅单元测试使用（`junit:junit:4.13.2`、`org.json:json:20240303`），注释说明使用真实 `org.json` 是因为 mockable android.jar 中的 `org.json` 只有抛异常的桩实现。

### 版本管理
当前采用**内联版本号**策略：每个依赖直接写死版本号字符串（如 `1.13.1`、`1.7.0`、`16.0.1`），未使用 `libs.versions.toml`（Version Catalog）或 BOM。插件版本集中在根 `build.gradle.kts` 的 `plugins {}` 块中。

### ABI 与原生库
针对 ML Kit 中文 OCR 模型，显式通过 `ndk { abiFilters += listOf("arm64-v8a") }` 只保留 arm64 原生库，其余 ABI 被裁剪；同时 `packaging.jniLibs.useLegacyPackaging = false` 以适配 Android 15+ 的 16KB 页对齐要求。

### 签名密钥
Release 签名属性从仓库外读取，优先使用环境变量 `JEV_KEYSTORE_PROPS` 指向的文件，否则回退到硬编码路径 `H:/android/keys/jev-release.properties`；若文件不存在则 release 构建保持未签名。

### Python 与站点侧
`tools/jev/*.py` 通过 `import` 标准库调用 OpenRouter API，未声明任何第三方依赖；`site/tools/*.mjs` 为 Node.js 脚本但仓库未提供 `package.json`，依赖来源未在仓库内声明。

## 4. 约定与约束

- **仓库源集中化**：`settings.gradle.kts` 中 `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 强制禁止子模块再声明自己的仓库，所有依赖必须来自 `google()` / `mavenCentral()` / `gradlePluginPortal()`。（来源：`settings.gradle.kts`）
- **插件版本集中声明**：根 `build.gradle.kts` 以 `id(...).version(...).apply(false)` 形式声明 AGP 与 Kotlin 插件版本，子模块仅通过 `id(...)` 引用而不重复指定版本。（来源：`build.gradle.kts`）
- **AndroidX 强制开启**：`gradle.properties` 中 `android.useAndroidX=true` 且 `android.nonTransitiveRClass=true`，项目全面迁移至 AndroidX 并使用非传递 R class 优化编译。（来源：`gradle.properties`）
- **JDK 版本固定**：`compileOptions.sourceCompatibility` 与 `kotlinOptions.jvmTarget` 均设为 `JavaVersion.VERSION_17` / `"17"`，构建需 JDK 17。（来源：`app/build.gradle.kts`）
- **minSdk/targetSdk/compileSdk 锁定**：`minSdk=30`、`targetSdk=35`、`compileSdk=35`，APK 仅支持 Android 11+。（来源：`app/build.gradle.kts`）
- **ML Kit 中文 OCR 使用 bundled 模型**：依赖 `com.google.mlkit:text-recognition-chinese:16.0.1` 而非 play-services 变体，以保证无 Google Play services 的设备可用且无需额外下载模型。（来源：`app/build.gradle.kts` 注释）
- **测试 JSON 解析使用真实 org.json**：因 android.jar 中 `org.json` 仅为桩实现，单元测试依赖 `org.json:json:20240303` 才能实际解析响应体。（来源：`app/build.gradle.kts` 注释）
- **签名密钥不入库**：release 签名属性文件位于仓库之外，通过 `JEV_KEYSTORE_PROPS` 环境变量注入；缺失时 release 构建不签名。（来源：`app/build.gradle.kts` 注释与代码）