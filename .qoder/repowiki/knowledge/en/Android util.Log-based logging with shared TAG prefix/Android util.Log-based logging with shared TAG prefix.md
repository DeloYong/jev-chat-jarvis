---
kind: logging_system
name: Android util.Log-based logging with shared TAG prefix
category: logging_system
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/core/Prefs.kt
    - app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt
    - app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt
    - app/src/main/java/com/jev/probe/jev/JudgeClient.kt
    - app/src/main/java/com/jev/probe/billing/EntitlementRepo.kt
    - app/src/main/java/com/jev/probe/core/kb/ContextBuilder.kt
    - app/src/main/java/com/jev/probe/core/kb/KbStore.kt
    - app/src/main/java/com/jev/probe/SettingsActivity.kt
---

## What system/approach is used

The Android app (`app/`) uses **`android.util.Log`** directly — there is no third-party logging framework (no Timber, SLF4J, Log4j, or `java.util.logging`). The Python tooling under `tools/jev/` does not appear to use any structured logging library either; it is a small set of scripts.

No logging framework dependency is declared in `build.gradle.kts`, `app/build.gradle.kts`, or `gradle.properties`; imports are exclusively `android.util.Log`.

## Key files and packages

- `app/src/main/java/com/jev/probe/core/Prefs.kt` — preference migration and default seeding logs
- `app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt` — OCR failure paths
- `app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt` — screenshot capture failures
- `app/src/main/java/com/jev/probe/jev/JudgeClient.kt` — HTTP/judge API error logging
- `app/src/main/java/com/jev/probe/billing/EntitlementRepo.kt` — cloud plan refresh logs
- `app/src/main/java/com/jev/probe/core/kb/ContextBuilder.kt` — context assembly diagnostics
- `app/src/main/java/com/jev/probe/core/kb/KbStore.kt` — knowledge base append logs
- `app/src/main/java/com/jev/probe/SettingsActivity.kt` — settings UI logs

## Architecture and conventions

1. **Per-class `TAG` constant.** Every file that logs defines a private tag:
   ```kotlin
   private const val TAG = "JEVASSIST"
   ```
   All observed tags across the codebase are the literal string `"JEVASSIST"` (9 occurrences). There is no class-name-derived tag helper.

2. **Log levels used:**
   - `Log.i(TAG, ...)` — informational / state transitions (prefs migration, plan registration, settings opened).
   - `Log.w(TAG, ...)` — recoverable errors and warnings (OCR failures, judge HTTP errors, background retry fallback).
   - `Log.d(TAG, ...)` — debug-level diagnostics (context builder fields, KB append skips).
   - No `Log.e(...)` calls were found in the scanned Kotlin sources.

3. **Message format.** Messages are plain concatenated strings built with `+` and `${...}` interpolation, e.g.:
   - `"cloud registered plan=${ent.plan} trial=${ent.trialRemaining}"`
   - `"prefs migrated judgeKey.len=${legacy.length}"`
   - `"judge failed: ${e.message}"`
   - `"ocr crop failed: ${e.javaClass.simpleName}"`

4. **Error logging pattern.** Exceptions are logged by extracting their simple class name rather than printing full stack traces:
   ```kotlin
   Log.w(TAG, "ocr crop failed: ${e.javaClass.simpleName}")
   Log.w(TAG, "cloud refresh failed: ${e.javaClass.simpleName}")
   ```
   This keeps log output concise on device.

5. **No centralized logger abstraction.** Each module imports `android.util.Log` directly; there is no wrapper class, no singleton logger, and no sink/router layer.

6. **No log level configuration.** Because `android.util.Log` is used directly, there is no runtime log-level switcher, filter, or formatter in the app code.

7. **Python tooling.** Files like `tools/jev/calibrate.py`, `tools/jev/questions.py`, and `tools/jev/jev_client.py` do not import any logging module; they rely on standard script behavior (print/stderr) for output.

## Conventions and constraints

- **Convention:** Every Kotlin source that emits logs declares `private const val TAG = "JEVASSIST"` at the top of the file and passes that same tag to every `Log.*` call. Verified across all 9 matching files.
- **Convention:** Error conditions are logged at `Log.w` and include the exception's simple class name via `e.javaClass.simpleName` rather than a full stack trace.
- **Constraint (observed):** No third-party logging library is imported anywhere in the Kotlin source tree — only `android.util.Log` is used.
- **Constraint (observed):** All tags are the fixed string `"JEVASSIST"`; there is no per-module or per-class tag differentiation.