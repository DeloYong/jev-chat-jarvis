# Performance Optimization Strategies

<cite>
**Referenced Files in This Document**
- [ChatCaptureService.kt](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt)
- [ConversationSession.kt](file://app/src/main/java/com/jev/probe/capture/ConversationSession.kt)
- [ScreenCapture.kt](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt)
- [MlKitOcr.kt](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt)
- [CaptureRules.kt](file://app/src/main/java/com/jev/probe/capture/CaptureRules.kt)
</cite>

## Table of Contents
1. [Introduction](#introduction)
2. [Project Structure](#project-structure)
3. [Core Components](#core-components)
4. [Architecture Overview](#architecture-overview)
5. [Detailed Component Analysis](#detailed-component-analysis)
6. [Dependency Analysis](#dependency-analysis)
7. [Performance Considerations](#performance-considerations)
8. [Troubleshooting Guide](#troubleshooting-guide)
9. [Conclusion](#conclusion)

## Introduction
This document explains the performance optimization strategies used by the message capture system. It focuses on how bursts of accessibility events are debounced, how redundant analysis is avoided through signature-based deduplication, how background work is executed safely with a fixed-size thread pool, and how memory, battery, session state, OCR rate limiting, and failure backoff are handled. The goal is to make these optimizations understandable for both developers and product-oriented readers while preserving precise code-level references.

## Project Structure
The relevant performance-critical code lives under the capture package:
- ChatCaptureService orchestrates event handling, conversation sessions, overlay interaction, and background analysis.
- ConversationSession tracks active conversations and prevents stale callbacks.
- ScreenCapture manages screenshot acquisition, throttling, and backoff.
- MlKitOcr performs on-device text recognition efficiently.
- CaptureRules centralizes decision logic for manual operations and foreground exclusions.

```mermaid
graph TB
CCS["ChatCaptureService<br/>Event loop, debounce, session, worker pool"] --> CS["ConversationSession<br/>Target + revision token"]
CCS --> SC["ScreenCapture<br/>Screenshot throttle + backoff"]
CCS --> OCR["MlKitOcr<br/>On-device OCR"]
CCS --> CR["CaptureRules<br/>Manual block + exclusion rules"]
SC --> |Result.Ok / Result.Failed| CCS
OCR --> |Lines + bounds| CCS
```

**Diagram sources**
- [ChatCaptureService.kt:43-170](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L43-L170)
- [ConversationSession.kt:4-35](file://app/src/main/java/com/jev/probe/capture/ConversationSession.kt#L4-L35)
- [ScreenCapture.kt:35-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L35-L93)
- [MlKitOcr.kt:26-95](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L26-L95)
- [CaptureRules.kt:12-56](file://app/src/main/java/com/jev/probe/capture/CaptureRules.kt#L12-L56)

**Section sources**
- [ChatCaptureService.kt:43-170](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L43-L170)
- [ConversationSession.kt:4-35](file://app/src/main/java/com/jev/probe/capture/ConversationSession.kt#L4-L35)
- [ScreenCapture.kt:35-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L35-L93)
- [MlKitOcr.kt:26-95](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L26-L95)
- [CaptureRules.kt:12-56](file://app/src/main/java/com/jev/probe/capture/CaptureRules.kt#L12-L56)

## Core Components
- Debouncing: A main-thread handler schedules analysis after a delay to collapse rapid content-changed events into one analysis pass.
- Signature-based deduplication: Both tree snapshots and OCR results compute a stable signature; identical signatures skip redundant analysis or UI updates.
- Worker thread pool: A fixed-size executor runs analysis tasks off the main thread, with graceful rejection handling during teardown.
- Memory management: Bitmaps from screenshots and cropped regions are explicitly recycled; hardware buffers are closed promptly.
- Battery optimization: Screenshot frequency is limited, OCR is gated, and expensive work is deferred or warmed up ahead of time.
- Session state: A lightweight target + revision token ensures only live requests proceed and stale work is abandoned.
- OCR rate limiting and backoff: Global throttling enforces a minimum interval and exponential backoff on repeated failures.

**Section sources**
- [ChatCaptureService.kt:45-59](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L45-L59)
- [ChatCaptureService.kt:178-180](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L178-L180)
- [ChatCaptureService.kt:331-359](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L331-L359)
- [ChatCaptureService.kt:548-622](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L548-L622)
- [ScreenCapture.kt:188-206](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L188-L206)
- [MlKitOcr.kt:100-111](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L100-L111)

## Architecture Overview
The capture pipeline connects accessibility events to either direct chat-app adapters or an OCR fallback path. All user-facing UI updates happen on the main thread, while heavy work runs on background executors.

```mermaid
sequenceDiagram
participant OS as "Android System"
participant CCS as "ChatCaptureService"
participant SC as "ScreenCapture"
participant OCR as "MlKitOcr"
participant W as "Worker Pool"
OS->>CCS : Accessibility event
CCS->>CCS : maybeCapture()
alt Tree has messages
CCS->>CCS : Compute snapshot signature
CCS->>CCS : Debounce via Handler.postDelayed
CCS->>W : submitAnalysis(runAnalysis)
else No messages (OCR fallback)
CCS->>SC : capture(shouldCapture, callback)
SC-->>CCS : Result.Ok(bitmap) or Result.Failed(code)
alt Ok
CCS->>OCR : recognize(bitmap, region)
OCR-->>CCS : Lines
CCS->>CCS : Group lines → ChatSnapshot
CCS->>W : submitAnalysis(runAnalysis)
end
end
```

**Diagram sources**
- [ChatCaptureService.kt:239-359](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L239-L359)
- [ChatCaptureService.kt:389-455](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L389-L455)
- [ChatCaptureService.kt:548-622](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L548-L622)
- [ScreenCapture.kt:66-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L66-L93)
- [MlKitOcr.kt:39-95](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L39-L95)

## Detailed Component Analysis

### Debouncing Mechanism Using Handler.postDelayed
Rapid accessibility events can cause many captures. The service defers actual analysis by posting a Runnable to the main thread’s handler after a short delay. Before scheduling, it removes any pending debounce task so only the latest event triggers analysis.

Key behaviors:
- Collapses bursts of TYPE_WINDOW_CONTENT_CHANGED and related events.
- Ensures only one analysis per meaningful change window.
- Cancels previous pending work when the conversation changes.

```mermaid
flowchart TD
Start(["Accessibility event"]) --> Maybe["maybeCapture()"]
Maybe --> HasSig{"Content changed?"}
HasSig --> |No| Idle["Show idle bubble if needed"]
HasSig --> |Yes| Remove["Remove pending debounce"]
Remove --> Post["postDelayed(debounce, 800ms)"]
Post --> Run["runAnalysis()"]
Run --> End(["Done"])
```

**Diagram sources**
- [ChatCaptureService.kt:357-359](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L357-L359)
- [ChatCaptureService.kt:389-455](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L389-L455)

**Section sources**
- [ChatCaptureService.kt:178-180](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L178-L180)
- [ChatCaptureService.kt:357-359](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L357-L359)
- [ChatCaptureService.kt:389-455](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L389-L455)

### Signature-Based Deduplication
To avoid redundant analysis when conversation content has not changed, the system computes signatures at two levels:
- Tree snapshot signature: Based on the current app package, title, and message list characteristics.
- OCR signature: Based on package, title, and bubble rectangle positions/sides; falls back to package+title when no rectangles are available.

Deduplication rules:
- If the same signature is detected and the overlay is already showing, skip re-analysis.
- If the same signature is detected but the overlay is gone, restore the idle bubble without re-analyzing.
- When switching apps, reset the signature to prevent cross-app false positives.

```mermaid
flowchart TD
Snap["Compute snapshot signature"] --> SameSig{"Same as lastSignature?"}
SameSig --> |Yes & Overlay shown| Skip["Skip analysis"]
SameSig --> |Yes & Overlay hidden| Restore["Show idle bubble"]
SameSig --> |No| Cancel["Cancel previous analysis"]
Cancel --> Update["Update lastSignature"]
Update --> Analyze["Proceed to analysis"]
```

**Diagram sources**
- [ChatCaptureService.kt:331-349](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L331-L349)
- [ChatCaptureService.kt:680-691](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L680-L691)

**Section sources**
- [ChatCaptureService.kt:331-349](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L331-L349)
- [ChatCaptureService.kt:535-541](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L535-L541)
- [ChatCaptureService.kt:680-691](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L680-L691)

### Worker Thread Pool Configuration and Rejection Handling
Background analysis uses a fixed-size executor with two threads. Tasks are submitted via a helper that catches RejectedExecutionException, ensuring that teardown does not crash the process.

Important aspects:
- Fixed pool size limits concurrent analysis to two tasks.
- Stale overlay callbacks are ignored after service destruction.
- During onDestroy, the pool is shut down and all pending tasks are cancelled.

```mermaid
classDiagram
class ChatCaptureService {
-Handler main
-Executor worker
-submit(task)
-submitAnalysis(task)
-cancelAnalysis()
}
class Executor {
+execute(task)
+submit(task) Future
}
ChatCaptureService --> Executor : "fixed-size pool"
```

**Diagram sources**
- [ChatCaptureService.kt:45-59](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L45-L59)
- [ChatCaptureService.kt:169-171](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L169-L171)
- [ChatCaptureService.kt:79-87](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L79-L87)
- [ChatCaptureService.kt:800-814](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L800-L814)

**Section sources**
- [ChatCaptureService.kt:45-59](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L45-L59)
- [ChatCaptureService.kt:169-171](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L169-L171)
- [ChatCaptureService.kt:79-87](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L79-L87)
- [ChatCaptureService.kt:800-814](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L800-L814)

### Memory Management Strategies
- Bitmap recycling: After OCR completes, bitmaps are explicitly recycled to free native memory. Cropped bitmaps created for specific regions are also recycled promptly.
- Hardware buffer lifecycle: Screenshot results wrap a hardware buffer; the implementation copies it into a software bitmap and closes the buffer immediately to avoid compositor leaks.
- GC considerations: By minimizing long-lived large objects and closing resources in finally blocks, the system reduces pressure on the garbage collector and avoids out-of-memory conditions during repeated screenshots.

```mermaid
flowchart TD
Shot["Screenshot result"] --> Copy["Copy to ARGB_8888 bitmap"]
Copy --> Crop["Optional crop for bubble regions"]
Crop --> OCR["Run OCR"]
OCR --> Recycle["Recycle bitmap(s)"]
Shot --> CloseHW["Close hardware buffer"]
Recycle --> Done["Free memory"]
CloseHW --> Done
```

**Diagram sources**
- [ScreenCapture.kt:155-176](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L155-L176)
- [ChatCaptureService.kt:605-607](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L605-L607)
- [ChatCaptureService.kt:617-618](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L617-L618)
- [MlKitOcr.kt:49-55](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L49-L55)
- [MlKitOcr.kt:86-93](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L86-L93)

**Section sources**
- [ScreenCapture.kt:155-176](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L155-L176)
- [ChatCaptureService.kt:605-607](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L605-L607)
- [ChatCaptureService.kt:617-618](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L617-L618)
- [MlKitOcr.kt:49-55](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L49-L55)
- [MlKitOcr.kt:86-93](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L86-L93)

### Battery Optimization Techniques
- Reducing screen capture frequency:
  - Global screenshot throttle enforces a minimum interval between attempts.
  - Exponential backoff increases intervals after repeated failures, capped at a maximum.
  - OCR fallback is gated by a signature check so repeated content-changed events do not trigger constant screenshots.
- Minimizing accessibility tree traversals:
  - Early exits when there is no adapter or no readable title.
  - Bounded traversal loops with guards when scanning nodes for debugging or input fields.
- Efficient notification/UI updates:
  - Only update the overlay when the signature changes or the bubble is missing.
  - Avoid unnecessary re-analysis when the same content is detected.

```mermaid
flowchart TD
Event["Accessibility event"] --> CheckTree["Adapter extracts snapshot"]
CheckTree --> Empty{"Messages empty?"}
Empty --> |Yes| SigCheck["Compute OCR signature"]
SigCheck --> Changed{"Changed from lastOcrSignature?"}
Changed --> |No| Throttle["Respect ScreenCapture throttle/backoff"]
Changed --> |Yes| Capture["Take screenshot"]
Empty --> |No| Dedupe["Tree-path dedupe"]
Dedupe --> UpdateUI["Update overlay only if needed"]
Throttle --> UpdateUI
Capture --> OCR["OCR processing"]
OCR --> UpdateUI
```

**Diagram sources**
- [ChatCaptureService.kt:308-327](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L308-L327)
- [ChatCaptureService.kt:331-359](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L331-L359)
- [ScreenCapture.kt:66-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L66-L93)
- [ScreenCapture.kt:188-206](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L188-L206)

**Section sources**
- [ChatCaptureService.kt:308-327](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L308-L327)
- [ChatCaptureService.kt:331-359](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L331-L359)
- [ScreenCapture.kt:66-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L66-L93)
- [ScreenCapture.kt:188-206](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L188-L206)

### Conversation Session State Management
A lightweight session object tracks the active conversation target and revision number. Tokens carry the target plus a revision; checks ensure only live tokens proceed. When the target changes or the service is torn down, old tasks are invalidated and UI state is reset.

Benefits:
- Prevents stale analysis after app switching.
- Avoids resource leaks by cancelling pending work and clearing overlays.
- Supports both automatic and manual capture paths with appropriate liveness rules.

```mermaid
classDiagram
class ConversationSession {
+Target target
-long revision
+observe(next) boolean
+invalidate() void
+token() Token?
+begin() Token?
+accepts(token) boolean
}
class Target {
+string pkg
+int windowId
+string? title
+string? messagesSignature
}
class Token {
+Target target
+long revision
}
ConversationSession --> Target
ConversationSession --> Token
```

**Diagram sources**
- [ConversationSession.kt:4-35](file://app/src/main/java/com/jev/probe/capture/ConversationSession.kt#L4-L35)
- [ChatCaptureService.kt:63-68](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L63-L68)
- [ChatCaptureService.kt:89-104](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L89-L104)
- [ChatCaptureService.kt:389-455](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L389-L455)

**Section sources**
- [ConversationSession.kt:4-35](file://app/src/main/java/com/jev/probe/capture/ConversationSession.kt#L4-L35)
- [ChatCaptureService.kt:63-68](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L63-L68)
- [ChatCaptureService.kt:89-104](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L89-L104)
- [ChatCaptureService.kt:389-455](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L389-L455)

### Rate Limiting Mechanisms for OCR Operations
- Per-capture gating:
  - Manual OCR is blocked while another OCR is in flight.
  - Automatic OCR is gated by a signature comparison to avoid repeated shots when the visible bubbles have not moved.
- Global throttling and backoff:
  - Minimum interval between screenshot attempts.
  - Exponential backoff on consecutive failures, capped at a maximum interval.
- Timeout protection:
  - A watchdog cancels stalled screenshot callbacks to avoid hanging states.

```mermaid
flowchart TD
Request["OCR request"] --> Busy{"ocrBusy?"}
Busy --> |Yes| Reject["Reject or wait"]
Busy --> |No| Throttle["Check global throttle"]
Throttle --> Allowed{"Within interval?"}
Allowed --> |No| Backoff["Apply backoff interval"]
Allowed --> |Yes| Shoot["Take screenshot"]
Shoot --> Success{"Success?"}
Success --> |No| Fail["Handle failure + backoff increment"]
Success --> |Yes| OCR["Process OCR"]
OCR --> Done["Reset busy flag"]
```

**Diagram sources**
- [ChatCaptureService.kt:465-499](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L465-L499)
- [ChatCaptureService.kt:548-587](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L548-L587)
- [ScreenCapture.kt:66-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L66-L93)
- [ScreenCapture.kt:188-206](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L188-L206)

**Section sources**
- [ChatCaptureService.kt:465-499](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L465-L499)
- [ChatCaptureService.kt:548-587](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L548-L587)
- [ScreenCapture.kt:66-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L66-L93)
- [ScreenCapture.kt:188-206](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L188-L206)

### Backoff Strategies for Failed Screenshot Attempts
When screenshots fail repeatedly, the system increases the required interval exponentially until it reaches a cap. Successful screenshots reset the streak. This prevents continuous retries on protected windows or unsupported configurations.

```mermaid
flowchart TD
Start(["Attempt screenshot"]) --> Interval["Compute requiredInterval()"]
Interval --> Wait["Wait if within cooldown"]
Wait --> Try["Call platform screenshot"]
Try --> Ok{"Success?"}
Ok --> |Yes| Reset["Reset failStreak"]
Ok --> |No| Inc["Increment failStreak (capped)"]
Reset --> End(["Return result"])
Inc --> End
```

**Diagram sources**
- [ScreenCapture.kt:66-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L66-L93)
- [ScreenCapture.kt:188-206](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L188-L206)

**Section sources**
- [ScreenCapture.kt:66-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L66-L93)
- [ScreenCapture.kt:188-206](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L188-L206)

## Dependency Analysis
The capture service depends on several components:
- ConversationSession for stateful tracking.
- ScreenCapture for safe, throttled screenshots.
- MlKitOcr for efficient on-device text recognition.
- CaptureRules for consistent decision-making around manual operations and foreground exclusions.

```mermaid
graph LR
CCS["ChatCaptureService"] --> CS["ConversationSession"]
CCS --> SC["ScreenCapture"]
CCS --> OCR["MlKitOcr"]
CCS --> CR["CaptureRules"]
```

**Diagram sources**
- [ChatCaptureService.kt:43-170](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L43-L170)
- [ConversationSession.kt:4-35](file://app/src/main/java/com/jev/probe/capture/ConversationSession.kt#L4-L35)
- [ScreenCapture.kt:35-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L35-L93)
- [MlKitOcr.kt:26-95](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L26-L95)
- [CaptureRules.kt:12-56](file://app/src/main/java/com/jev/probe/capture/CaptureRules.kt#L12-L56)

**Section sources**
- [ChatCaptureService.kt:43-170](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L43-L170)
- [ConversationSession.kt:4-35](file://app/src/main/java/com/jev/probe/capture/ConversationSession.kt#L4-L35)
- [ScreenCapture.kt:35-93](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L35-L93)
- [MlKitOcr.kt:26-95](file://app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt#L26-L95)
- [CaptureRules.kt:12-56](file://app/src/main/java/com/jev/probe/capture/CaptureRules.kt#L12-L56)

## Performance Considerations
- Prefer tree-based extraction over OCR whenever possible to avoid screenshots and OCR costs.
- Keep the overlay idle when content has not changed to reduce UI churn.
- Warm up the OCR recognizer early to avoid first-use latency on the main thread.
- Use bounded traversals and guards when walking the accessibility tree to prevent excessive CPU usage.
- Ensure all large bitmaps and hardware buffers are released promptly to minimize memory pressure.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common issues and their likely causes:
- Screenshot too frequent: The system throttles repeated attempts; wait for the backoff interval to expire.
- Protected window: Some screens cannot be captured; the error message indicates the condition.
- Service not allowed to screenshot: Requires enabling the accessibility service’s screenshot capability and toggling it off/on.
- Stale overlay callbacks: The service clears overlay callbacks during teardown to avoid crashes.

Relevant diagnostics:
- Manual analyze blocked reasons are centralized and surfaced to users.
- Foreground exclusions prevent capturing launcher/system UI.
- Debug logs include node id inventories (without sensitive text) to help diagnose adapter mismatches.

**Section sources**
- [ScreenCapture.kt:208-218](file://app/src/main/java/com/jev/probe/capture/ocr/ScreenCapture.kt#L208-L218)
- [CaptureRules.kt:19-25](file://app/src/main/java/com/jev/probe/capture/CaptureRules.kt#L19-L25)
- [CaptureRules.kt:32-38](file://app/src/main/java/com/jev/probe/capture/CaptureRules.kt#L32-L38)
- [CaptureRules.kt:48-56](file://app/src/main/java/com/jev/probe/capture/CaptureRules.kt#L48-L56)
- [ChatCaptureService.kt:800-814](file://app/src/main/java/com/jev/probe/capture/ChatCaptureService.kt#L800-L814)

## Conclusion
The message capture system employs a layered set of performance optimizations: debouncing bursts of events, deduplicating based on stable signatures, running analysis on a bounded worker pool, carefully managing memory, and applying aggressive throttling and backoff to screenshots. Session state ensures that only live requests proceed, preventing resource leaks during app switches. Together, these strategies keep the system responsive, battery-friendly, and robust across diverse Android environments.

[No sources needed since this section summarizes without analyzing specific files]