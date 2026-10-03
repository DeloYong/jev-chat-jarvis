---
kind: dependency_management
name: Gradle-based Android dependency management with centralized repository policy
category: dependency_management
scope:
    - '**'
source_files:
    - settings.gradle.kts
    - build.gradle.kts
    - app/build.gradle.kts
    - gradle.properties
    - gradle/wrapper/gradle-wrapper.properties
---

## Approach

The monorepo uses **Gradle (Kotlin DSL)** as the sole build and dependency-management system. The Android app is the only Gradle module; Python tooling under `tools/jev/` has no lockfile or package manifest, so its dependencies are not pinned.

## Key files

- `build.gradle.kts` — top-level file declaring plugin versions (`com.android.application` 8.7.3, `org.jetbrains.kotlin.android` 1.9.24) via the `plugins {}` block with `apply false`.
- `settings.gradle.kts` — declares the single included module `:app`, configures `pluginManagement` and `dependencyResolutionManagement` repositories, and enforces a centralized repo policy.
- `gradle.properties` — project-wide Gradle flags (`org.gradle.parallel=true`, `org.gradle.caching=true`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`).
- `app/build.gradle.kts` — the only place where third-party libraries are declared as `implementation` / `testImplementation` dependencies.
- `gradle/wrapper/gradle-wrapper.properties` + `gradlew` — pins the Gradle distribution used to run the build.

## Repository policy and resolution

`settings.gradle.kts` centralizes all Maven sources:

```kotlin
pluginManagement {
    repositories { google(), mavenCentral(), gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(), mavenCentral() }
}
```

`RepositoriesMode.FAIL_ON_PROJECT_REPOS` means any subproject that tries to declare its own `repositories { ... }` block will fail at configuration time — there is no per-module override of the global repo list. Only Google's Maven repo and Maven Central are allowed; no private registry, no local `mavenLocal()`, and no JitPack.

## Dependency declarations in the app module

All runtime and test dependencies live in `app/build.gradle.kts`:

| Category | Dependencies |
|---|---|
| Android SDK / platform | `androidx.core:core-ktx:1.13.1`, `androidx.appcompat:appcompat:1.7.0`, `com.google.android.material:material:1.12.0`, `androidx.constraintlayout:constraintlayout:2.1.4` |
| On-device OCR | `com.google.mlkit:text-recognition-chinese:16.0.1` (bundled Chinese model, chosen specifically to avoid Play Services and remote model downloads) |
| Tests | `junit:junit:4.13.2`, `org.json:json:20240303` (used because the mockable `android.jar` lacks real JSON parsing) |

Versions are specified inline as string literals next to each coordinate — there is no version catalog (`libs.versions.toml`) or BOM. Plugin versions are declared centrally in the root `build.gradle.kts`; library versions are scattered across the single app module.

## Versioning conventions observed

- Android Gradle Plugin and Kotlin compiler versions are pinned at the root level and shared by the app module through the inherited plugins.
- Library versions are pinned directly in `app/build.gradle.kts` (e.g. `1.13.1`, `1.7.0`, `1.12.0`, `16.0.1`, `4.13.2`, `20240303`).
- No lockfile is committed for Gradle dependencies (no `.gradle/caches` or equivalent checked in); the Gradle Wrapper (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`) pins the Gradle distribution itself.
- The Python tools under `tools/jev/` have no `requirements.txt`, `pyproject.toml`, or lockfile — their third-party imports are not version-pinned in this repository.

## Constraints enforced by the build

- `RepositoriesMode.FAIL_ON_PROJECT_REPOS` enforces that all dependency sources go through the two repos declared in `settings.gradle.kts`.
- Release signing keys are loaded from an external properties file pointed to by the `JEV_KEYSTORE_PROPS` environment variable; without it, release builds are unsigned (see comment in `app/build.gradle.kts`).
- NDK ABI filtering is restricted to `arm64-v8a` to drop dead-weight ABIs for the target device class.
- Java/Kotlin compilation targets JVM 17 (`compileOptions.sourceCompatibility = 17`, `kotlinOptions.jvmTarget = "17"`).