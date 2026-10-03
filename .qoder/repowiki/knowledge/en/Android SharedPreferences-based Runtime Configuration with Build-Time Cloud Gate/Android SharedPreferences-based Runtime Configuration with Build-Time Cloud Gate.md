---
kind: configuration_system
name: Android SharedPreferences-based Runtime Configuration with Build-Time Cloud Gateway Injection
category: configuration_system
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/core/Prefs.kt
    - app/src/main/java/com/jev/probe/SettingsActivity.kt
    - app/build.gradle.kts
    - gradle.properties
---

## Overview

The Jev Chat Jarvis Android app has no external configuration files, `.env` files, YAML/JSON config loaders, or feature-flag framework. All runtime configuration is centralized in a single Kotlin class backed by Android `SharedPreferences`, with a small amount of build-time configuration injected via Gradle `BuildConfig`.

## Key Files

- `app/src/main/java/com/jev/probe/core/Prefs.kt` — the sole configuration store and endpoint resolver.
- `app/src/main/java/com/jev/probe/SettingsActivity.kt` — the only UI for reading/writing user configuration; also hosts per-test scratch instances.
- `app/build.gradle.kts` — injects the hosted gateway base URL into `BuildConfig.CLOUD_BASE_URL` via `-PjevCloudBase=`.
- `gradle.properties` — project-wide Gradle settings (JDK 17, parallel builds); not application configuration.

## Architecture

### Persistent runtime config: `Prefs`

`Prefs(context, prefsName = PREFS_MAIN)` wraps one `SharedPreferences` instance named `"jev_assistant"` (`MODE_PRIVATE`). Every field is a Kotlin property whose getter reads from SP with a default value and whose setter writes back via `sp.edit().apply()`. There are ~40 keys, all declared as `private const val K_*` constants in the companion object alongside their string defaults and provider enum values.

Configuration groups:

| Group | Keys | Purpose |
|---|---|---|
| Judge route | `judge_provider`, `judge_base_url`, `judge_key`, `judge_model` | Decision API (OpenRouter / Bocha / TypeSafe / Vercel / Zen / Custom) |
| Reply route | `reply_base_url`, `reply_key`, `reply_model` | OpenAI-compatible chat completions |
| Vision route | `vision_base_url`, `vision_key`, `vision_model` | OCR image-to-text |
| Context (D stage) | `context_enabled`, `context_history_count`, `auto_summary` | Optional on-device chat history storage |
| OCR (B stage) | `ocr_engine`, `ocr_unknown_apps`, `ocr_fallback`, `ocr_auto_analyze` | ML Kit vs system vision OCR toggles |
| App behavior | `relationship`, `enabled`, `whitelist`, `overlay_opacity`, `bubble_x/y`, `auto_analyze` | Core assistant behavior |
| Hosted mode | `cloud_enabled`, `cloud_token`, `cloud_consent`, `cloud_entitlement_json`, `cloud_pending_order`, `cloud_device_fallback` | Official gateway subscription state |

### Endpoint resolution

`Prefs` owns the full URL construction logic:

- `judgeEndpoint()` — returns `/v1/judge` when hosted mode is active; otherwise routes to `/alpha/decisions` (OpenRouter) or `/v1/systemone` (Bocha/TypeSafe/Vercel/Zen), or uses the user-supplied custom URL verbatim.
- `replyEndpoint()` — always `/chat/completions` against `replyBaseUrl` (or hosted gateway).
- `visionEndpoint()` — always `/chat/completions` against `visionBaseUrl` (defaults to OpenRouter).

Key fallbacks are explicit: `effectiveReplyKey()` falls back to `judgeKey`; `effectiveVisionKey()` falls back to reply then judge. The `openRouterKey` alias is retained for backward compatibility.

### Migration and seeding

Two one-shot migrations run in the constructor (only when `prefsName == PREFS_MAIN`):

1. `migrateIfNeeded()` — v1.2 → v1.3 migration that copies the legacy `openrouter_key` into `judge_key` once, guarded by `prefs_migrated_v13`.
2. `unseedBochaDefaultIfUnconfigured()` — reverts an earlier v1.4.0 auto-seed of the Bocha provider on fresh installs where no key was ever entered, guarded by `unseeded_bocha_v141`.

### Build-time configuration: cloud gateway

`app/build.gradle.kts` reads `-PjevCloudBase=` (from command line or `~/.gradle/gradle.properties`) and emits it as `BuildConfig.CLOUD_BASE_URL`. If blank, every hosted/paywall surface stays hidden — the plain open-source build behaves identically to before.

At runtime, `cloudBase()` returns this value, `cloudAvailable()` checks it starts with `https://`, and `cloudActive()` requires `cloudEnabled && cloudAvailable() && cloudToken.isNotBlank()`. When active, `judgeRouteKey()` and `replyRouteKey()` return the gateway token instead of the user's BYOK key, and endpoints switch to `${cloudBase()}/v1/judge` and `${cloudBase()}/v1/chat`.

### Secrets handling

API keys (`judge_key`, `reply_key`, `vision_key`, `cloud_token`) are stored in `SharedPreferences` under `MODE_PRIVATE`. They are never logged; only key lengths are logged (e.g. `Log.i(TAG, "settings opened judgeKey.len=... replyKey.len=... visionKey.len=...")`). The release keystore properties file is loaded from a path outside the repo via the `JEV_KEYSTORE_PROPS` environment variable, with a hardcoded local fallback used only during development.

### Settings UI and scratch instances

`SettingsActivity` is the only place users edit configuration. It groups fields into sections (接口/Judge, 回复/Reply, 视觉/Vision, 分析/Analysis, 外观/Appearance, 关于与隐私/About & Privacy). Test buttons use `draftPrefs(scratchName, ...)` which creates throwaway `SharedPreferences` instances named `jev_probe_scratch_judge/reply/vision` so tests never touch the real config.

## Conventions and Constraints

- **Single source of truth**: All persistent app configuration goes through `Prefs.kt`; there is no other config loader in the codebase.
- **Keys are constants**: Every `SharedPreferences` key is a `private const val K_*` in `Prefs.Companion`; call sites reference them via the class, never raw strings.
- **Defaults are co-located**: String defaults, provider enums, and model presets live next to the keys in the same companion object block.
- **User-facing secrets are masked**: Password inputs use `InputType.TYPE_TEXT_VARIATION_PASSWORD` rather than `VISIBLE_PASSWORD` (see `edit(...)` helper in `SettingsActivity`).
- **Migration flags are boolean guards**: Each migration runs at most once, guarded by its own `K_MIGRATED_*` flag written to SP.
- **Hosted mode is opt-in and lossless**: Enabling hosted mode does not overwrite the user's BYOK fields; switching back restores them exactly.
- **Build-time vs runtime separation**: Provider URLs and models are runtime-configurable; the hosted gateway root is compile-time-only via `BuildConfig.CLOUD_BASE_URL`.
- **No env vars at runtime**: Application code does not read `System.getenv()`; only the Gradle build script reads `JEV_KEYSTORE_PROPS` for signing.
- **No external config files**: No `*.yaml`, `*.toml`, `*.json` config, no `.env`, no `application.properties` — the repository contains none of these patterns.