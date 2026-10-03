---
kind: error_handling
name: 'Cross-Layer Error Handling: ApiException, JevError, and Result Sealing'
category: error_handling
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/jev/HttpJson.kt
    - app/src/main/java/com/jev/probe/jev/ResponseShape.kt
    - app/src/main/java/com/jev/probe/billing/EntitlementRepo.kt
    - app/src/main/java/com/jev/probe/billing/PlanActivity.kt
    - app/src/main/java/com/jev/probe/MainActivity.kt
    - app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt
    - app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt
    - tools/jev/jev_client.py
    - tools/jev/calibrate.py
    - tools/jev/probe_background_field.py
---

## Overview

The monorepo has no shared error framework. Each language layer defines its own error type and propagates it upward to the UI or caller:

- **Android (Kotlin)** — `ApiException` (a `RuntimeException`) thrown from the HTTP/JSON layer; callers catch it at the Activity/Repo boundary.
- **Python tooling** — a single `JevError(Exception)` raised by `jev_client.py`; callers use `try/except`.
- **UI / capture layers** — fall back to broad `catch (e: Exception)` / `catch (e: Throwable)` blocks that swallow errors and log via `Log.w` or return sentinel values (`null`, `false`, empty lists).

There is no middleware, no centralized error handler, and no panic/recover equivalent in Kotlin.

## Android: `ApiException` + `HttpJson` retry policy

### Core types

- `Route` object (`app/src/main/java/com/jev/probe/jev/HttpJson.kt`) — constants `JUDGE` / `REPLY` / `VISION` / `CLOUD` used as the first field of every `ApiException`, so user-facing messages identify which API route failed.
- `ApiException(route, status, snippet, retryable = true, code = null) : RuntimeException` — carries:
  - `status: Int?` (null means transport failure)
  - `snippet` — first 120 chars of response body
  - `retryable` — false for permanent configuration errors (wrong address, incompatible protocol, 4xx client errors)
  - `code` — gateway business code like `trial_exhausted` / `plan_expired`
  - `isPaywall` derived property (`status == 402`) used by billing UI to show paywall instead of generic error.
- `ResponseShape.ok` / `chatContent` / `jevAnswers` / `threeCandidates` — parse 2xx bodies and throw `ApiException(..., retryable = false)` when the JSON shape does not match the expected contract. The class comment explains this was introduced because two distinct failures previously reached the UI as success (unknown path returning 200 with an error body, and malformed bodies parsing to empty results).

### Retry strategy (centralized in `HttpJson.post`)

- Up to `MAX_ATTEMPTS = 3` attempts with exponential backoff (`500ms * 2^attempt`).
- Retries only on:
  - HTTP 429 / 529 (throttling / overload), except when the gateway marks 429 as a hard daily cap (body contains `"gateway"`) — then fails fast.
  - Any non-`ApiException` caught by the outer `catch (e: Exception)` block.
- Does NOT retry on `ApiException` where `!retryable` or `status in 400..499`.
- Non-2xx responses are converted to `ApiException` via `httpError`, which unwraps the hosted gateway's `{"gateway":true,"error":{"code","message"}}` envelope into `code` + `snippet` while leaving third-party provider bodies raw.
- Transport-level failures are normalized through `describe(e)` into localized Chinese strings (timeout, DNS resolution, connection refused, SSL cert failure).

### Call-site handling

- `EntitlementRepo.refresh` catches `ApiException` with `status != 401` rethrown; 401 is treated as "no token yet" and silently ignored.
- `MainActivity.register` wraps the call in `try { ... } catch (e: Exception) { e.message ?: "请求失败" }` and displays the message string.
- `PlanActivity.createOrder` catches `Exception` and shows `Toast.makeText(this, result.message ?: "下单失败", ...)`. It also polls order status with `try { ... } catch (_: Exception) { false }`.
- `JevClient.analyze` catches exceptions from `draftAndRank` and returns an empty ranked list rather than failing the whole analysis.
- OCR/capture helpers (`MlKitOcr`, `ScreenCapture`) catch `Exception` / `Throwable` broadly and return fallback values or log via `Log.w`.

## Python: `JevError` + exponential backoff

`tools/jev/jev_client.py` defines:

```python
class JevError(Exception):
    def __init__(self, message: str, status: int | None = None) -> None:
        super().__init__(message)
        self.status = status
```

`ask(state, questions, timeout=20)` retries HTTP 429 and 529 up to `MAX_RETRIES = 3` times with exponential backoff (`2^attempt` seconds). On exhaustion it raises `JevError(readable_message, last_status)`. Timeouts and `URLError` are similarly retried before raising. A helper `redact_secrets` strips the live `OPENROUTER_API_KEY` from any printed/written text.

Callers (`calibrate.py`, `probe_background_field.py`) use `try/except urllib.error.HTTPError` and bare `except Exception` blocks; malformed input raises `KeyError` / `SystemExit` directly.

## Conventions observed

1. **Network-layer normalization**: Both `HttpJson.post` (Kotlin) and `ask` (Python) normalize all transport failures into a domain exception (`ApiException` / `JevError`) carrying a human-readable message and optional HTTP status.
2. **Retryable flag**: `ApiException.retryable = false` is used to mark permanent misconfiguration errors (wrong route, incompatible protocol, 4xx) so `HttpJson` does not waste round trips.
3. **Gateway envelope unwrapping**: `HttpJson.httpError` detects `{"gateway":true,...}` and extracts `code`/`message` into `ApiException.code`/`snippet`; other providers' bodies are passed through verbatim.
4. **Body validation over status-only checks**: `ResponseShape.ok` rejects 2xx responses that carry an `error` field, `success=false`, or `code >= 400`, preventing silent successes.
5. **Localized error messages**: All user-visible error text is in Chinese (e.g. `"网络超时，请检查连接"`, `"域名解析失败，地址填错或无网络"`).
6. **Secret redaction**: Python side uses `redact_secrets` around any output; Kotlin side never logs the Bearer key (it is passed as a parameter and never concatenated into log strings).
7. **Broad catch blocks at UI boundaries**: Activities and services wrap network calls in `try/catch (Exception)` and convert to Toasts, Log warnings, or sentinel values — there is no global uncaught-exception handler.