# Response Shape Models

<cite>
**Referenced Files in This Document**
- [ResponseShape.kt](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt)
- [ChatModels.kt](file://app/src/main/java/com/jev/probe/core/ChatModels.kt)
- [JudgeClient.kt](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt)
- [ReplyClient.kt](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt)
- [Prefs.kt](file://app/src/main/java/com/jev/probe/core/Prefs.kt)
- [HttpJson.kt](file://app/src/main/java/com/jev/probe/jev/HttpJson.kt)
- [ResponseShapeTest.kt](file://app/src/test/java/com/jev/probe/jev/ResponseShapeTest.kt)
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
This document explains the standardized response shape and data models used to normalize outputs from multiple AI providers, including OpenRouter, DeepSeek, TypeSafe, Vercel, and OpenCode Zen. It focuses on:
- The unified response contract that prevents silent failures when providers return HTTP 200 with error bodies or malformed JSON.
- The Analysis model representing judgment results, error states, and ranked replies.
- The RankedReply structure with scoring and metadata.
- Protocol versioning, field validation rules, and backward compatibility considerations.
- How provider-specific payloads are normalized into the standard format.

The goal is to make the protocol clear for both developers integrating new providers and users configuring existing ones.

## Project Structure
The relevant implementation lives under two packages:
- com.jev.probe.core: Core domain models such as Analysis, Choice, Score, and RankedReply.
- com.jev.probe.jev: Client code and normalization logic for judge and reply routes, plus response shape validation.

```mermaid
graph TB
subgraph "Core Domain"
CM["ChatModels.kt<br/>Analysis / Choice / Score / RankedReply"]
end
subgraph "Jev Clients"
RC["ReplyClient.kt<br/>OpenAI-compatible chat completions"]
JC["JudgeClient.kt<br/>Decisions / ranking"]
RS["ResponseShape.kt<br/>Validation & normalization"]
PR["Prefs.kt<br/>Provider routing & defaults"]
HJ["HttpJson.kt<br/>HTTP transport"]
end
RC --> RS
JC --> RS
RC --> PR
JC --> PR
RC --> HJ
JC --> HJ
```

**Diagram sources**
- [ChatModels.kt:40-58](file://app/src/main/java/com/jev/probe/core/ChatModels.kt#L40-L58)
- [ReplyClient.kt:10-93](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt#L10-L93)
- [JudgeClient.kt:14-143](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L14-L143)
- [ResponseShape.kt:25-140](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L25-L140)
- [Prefs.kt:281-303](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L281-L303)
- [HttpJson.kt:14-20](file://app/src/main/java/com/jev/probe/jev/HttpJson.kt#L14-L20)

**Section sources**
- [ChatModels.kt:40-58](file://app/src/main/java/com/jev/probe/core/ChatModels.kt#L40-L58)
- [ReplyClient.kt:10-93](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt#L10-L93)
- [JudgeClient.kt:14-143](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L14-L143)
- [ResponseShape.kt:25-140](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L25-L140)
- [Prefs.kt:281-303](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L281-L303)

## Core Components
- ResponseShape: Validates and normalizes provider responses before they reach UI or business logic. It rejects 2xx bodies that report errors internally, enforces OpenAI-compatible chat completion shapes, validates decisions endpoints, and extracts candidate replies.
- Analysis: A structured result containing seven judgment fields (choices, scores, and numeric signals), a list of ranked replies, latency, error state, and paywall flag.
- RankedReply: Represents a candidate reply with its probability score.
- JudgeClient: Implements the decisions protocol, parses answers into Analysis, and ranks candidates.
- ReplyClient: Calls any OpenAI-compatible chat completions endpoint to draft three candidate replies.
- Prefs: Encodes provider routing, base URLs, keys, models, and hosted gateway behavior.

**Section sources**
- [ResponseShape.kt:25-140](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L25-L140)
- [ChatModels.kt:40-58](file://app/src/main/java/com/jev/probe/core/ChatModels.kt#L40-L58)
- [JudgeClient.kt:31-68](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L31-L68)
- [ReplyClient.kt:28-93](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt#L28-L93)
- [Prefs.kt:281-303](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L281-L303)

## Architecture Overview
The system uses two distinct protocols:
- Decisions protocol (judge route): Returns structured answers for intent, danger level, needs, actionability, tension resolution, and literal question interpretation.
- Chat completions protocol (reply route): OpenAI-compatible chat completions returning assistant text content.

```mermaid
sequenceDiagram
participant App as "App Layer"
participant Jev as "JevClient"
participant Judge as "JudgeClient"
participant Reply as "ReplyClient"
participant Net as "HttpJson"
participant Prov as "AI Provider"
participant Norm as "ResponseShape"
App->>Jev : analyze(snapshot, relationship, ctx)
Jev->>Judge : judge(...)
Judge->>Net : POST decisions
Net->>Prov : HTTP request
Prov-->>Net : JSON body
Net-->>Judge : JSONObject
Judge->>Norm : jevAnswers()
Norm-->>Judge : validated answers
Judge-->>Jev : Analysis (no ranked replies yet)
Jev->>Reply : draft(...)
Reply->>Net : POST chat completions
Net->>Prov : HTTP request
Prov-->>Net : JSON body
Net-->>Reply : JSONObject
Reply->>Norm : chatContent()
Norm-->>Reply : assistant text
Reply-->>Jev : List<String> candidates
Jev->>Judge : rank(candidates)
Judge->>Net : POST best_reply
Net->>Prov : HTTP request
Prov-->>Net : JSON body
Net-->>Judge : JSONObject
Judge-->>Jev : List<RankedReply>
Jev-->>App : Analysis + rankedReplies
```

**Diagram sources**
- [JudgeClient.kt:31-68](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L31-L68)
- [ReplyClient.kt:28-93](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt#L28-L93)
- [ResponseShape.kt:35-94](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L35-L94)
- [Prefs.kt:281-303](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L281-L303)

## Detailed Component Analysis

### ResponseShape Contract
ResponseShape defines the canonical validation layer:
- ok(route, status, text): Accepts only well-formed success bodies; throws ApiException for internal errors even when HTTP status is 2xx.
- chatContent(route, resp): Extracts trimmed assistant text from OpenAI-compatible choices[0].message.content; rejects Anthropic message shapes and empty content.
- jevAnswers(route, resp): Requires an answers object; otherwise throws ApiException indicating the address is not a decisions endpoint.
- threeCandidates(route, content): Parses either a JSON array or numbered/bulleted lines into up to three candidates; pads fewer than three with a filler string; throws if no usable candidates exist.

```mermaid
flowchart TD
Start(["ok(route,status,text)"]) --> Parse["Parse JSON body"]
Parse --> CheckError{"error present?"}
CheckError --> |Yes| ThrowErr["Throw ApiException<br/>non-retryable"]
CheckError --> |No| CheckSuccess{"success=false?"}
CheckSuccess --> |Yes| ThrowFail["Throw ApiException<br/>non-retryable"]
CheckSuccess --> |No| CheckCode{"code >= 400?"}
CheckCode --> |Yes| ThrowCode["Throw ApiException<br/>non-retryable"]
CheckCode --> |No| ReturnResp["Return JSONObject"]
```

**Diagram sources**
- [ResponseShape.kt:35-43](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L35-L43)
- [ResponseShape.kt:98-116](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L98-L116)

**Section sources**
- [ResponseShape.kt:35-94](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L35-L94)
- [ResponseShape.kt:98-140](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L98-L140)

### Analysis Model
Analysis aggregates judgment outcomes and ranked replies:
- trueIntent: Choice
- dangerLevel: Score
- sheNeeds: Choice
- shouldReplyNow: Double?
- bestAction: Choice
- tensionResolved: Double?
- literalQuestion: Double?
- rankedReplies: List<RankedReply>
- latencyMs: Long
- error: String?
- paywall: Boolean

Choice and Score provide confidence and probabilities or legend-derived max levels. RankedReply pairs candidate text with a probability score.

```mermaid
classDiagram
class Analysis {
+Choice trueIntent
+Score dangerLevel
+Choice sheNeeds
+Double shouldReplyNow
+Choice bestAction
+Double tensionResolved
+Double literalQuestion
+RankedReply[] rankedReplies
+Long latencyMs
+String error
+Boolean paywall
}
class Choice {
+String choice
+Double confidence
+Map~String,Double~ probabilities
}
class Score {
+Double score
+Double confidence
+Int maxLevel
}
class RankedReply {
+String text
+Double prob
}
Analysis --> Choice : "uses"
Analysis --> Score : "uses"
Analysis --> RankedReply : "contains"
```

**Diagram sources**
- [ChatModels.kt:40-58](file://app/src/main/java/com/jev/probe/core/ChatModels.kt#L40-L58)

**Section sources**
- [ChatModels.kt:40-58](file://app/src/main/java/com/jev/probe/core/ChatModels.kt#L40-L58)

### JudgeClient Decisions Flow
JudgeClient implements the decisions protocol:
- judge(): Sends state and questions, parses answers into Analysis, measures latency, and returns errors via Analysis.error rather than throwing.
- rank(): Sends best_reply question over candidates, parses probabilities, and returns sorted RankedReply list.
- postDecisions(): Adds optional background/history context; retries without enriched fields on 4xx unless it is a gateway gate (401/402/429).

```mermaid
sequenceDiagram
participant C as "Caller"
participant J as "JudgeClient"
participant N as "HttpJson"
participant P as "Provider"
participant R as "ResponseShape"
C->>J : judge(snapshot, relationship, ctx)
J->>N : POST decisions(state, questions)
N->>P : HTTP request
P-->>N : JSON body
N-->>J : JSONObject
J->>R : jevAnswers()
R-->>J : validated answers
J-->>C : Analysis
```

**Diagram sources**
- [JudgeClient.kt:31-68](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L31-L68)
- [JudgeClient.kt:79-112](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L79-L112)
- [ResponseShape.kt:71-75](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L71-L75)

**Section sources**
- [JudgeClient.kt:31-68](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L31-L68)
- [JudgeClient.kt:79-112](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L79-L112)

### ReplyClient Drafting Flow
ReplyClient drafts three candidate replies using OpenAI-compatible chat completions:
- Builds system and user prompts, optionally injecting knowledge context.
- Posts messages and temperature to the configured endpoint.
- Normalizes assistant text through ResponseShape.chatContent.

```mermaid
sequenceDiagram
participant C as "Caller"
participant R as "ReplyClient"
participant N as "HttpJson"
participant P as "Provider"
participant S as "ResponseShape"
C->>R : draft(snapshot, relationship, ctx)
R->>R : build prompt (+knowledgeBlock)
R->>N : POST chat completions
N->>P : HTTP request
P-->>N : JSON body
N-->>R : JSONObject
R->>S : chatContent()
S-->>R : assistant text
R-->>C : List<String> candidates
```

**Diagram sources**
- [ReplyClient.kt:28-93](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt#L28-L93)
- [ResponseShape.kt:50-64](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L50-L64)

**Section sources**
- [ReplyClient.kt:28-93](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt#L28-L93)

### Candidate Extraction Logic
threeCandidates supports two formats:
- JSON array: First JSON array block in the text; trims each item; filters blanks; takes up to three.
- Line fallback: Numbered or bulleted lines; strips common prefixes; filters blanks.

Fewer than three real candidates are padded with a filler string; zero usable candidates throw an error.

```mermaid
flowchart TD
Start(["threeCandidates(content)"]) --> TryArray["Try parse first JSON array"]
TryArray --> FoundArray{"Array found?"}
FoundArray --> |Yes| TrimFilter["Trim items, filter blanks"]
TrimFilter --> Take3["Take up to 3"]
FoundArray --> |No| TryLines["Split by newline, strip prefixes"]
TryLines --> FilterLines["Filter blanks"]
FilterLines --> Take3
Take3 --> Enough{"At least one candidate?"}
Enough --> |No| ThrowErr["Throw ApiException"]
Enough --> |Yes| Pad["Pad to 3 with filler if needed"]
Pad --> Return["Return List<String>"]
```

**Diagram sources**
- [ResponseShape.kt:84-94](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L84-L94)
- [ResponseShape.kt:121-140](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L121-L140)

**Section sources**
- [ResponseShape.kt:84-94](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L84-L94)
- [ResponseShape.kt:121-140](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L121-L140)

### Ranking Logic
rank parses best_reply probabilities keyed by reply_a, reply_b, reply_c and maps them to the original candidate texts, then sorts descending by probability.

```mermaid
flowchart TD
Start(["parseRanked(probabilities, candidates)"]) --> Keys["Keys = ['reply_a','reply_b','reply_c']"]
Keys --> Map["For each candidate i:<br/>prob = probabilities[keys[i]] or 0"]
Map --> Build["Build RankedReply(text, prob)"]
Build --> Sort["Sort by prob descending"]
Sort --> Return["Return List<RankedReply>"]
```

**Diagram sources**
- [JudgeClient.kt:130-137](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L130-L137)

**Section sources**
- [JudgeClient.kt:130-137](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L130-L137)

### Provider Routing and Defaults
Prefs configures provider routing:
- Judge route presets: openrouter, typesafe, vercel, zen, bocha, custom.
- Default bases and models per provider.
- Hosted mode overrides endpoints and headers.
- Reply route uses OpenAI-compatible chat completions path.

```mermaid
graph LR
PR["Prefs.kt"] --> JR["judgeEndpoint()<br/>/alpha/decisions or /v1/systemone"]
PR --> RR["replyEndpoint()<br/>/chat/completions"]
PR --> VR["visionEndpoint()<br/>/chat/completions"]
```

**Diagram sources**
- [Prefs.kt:281-303](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L281-L303)
- [Prefs.kt:358-397](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L358-L397)

**Section sources**
- [Prefs.kt:281-303](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L281-L303)
- [Prefs.kt:358-397](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L358-L397)

## Dependency Analysis
Key dependencies:
- ReplyClient depends on HttpJson for transport and ResponseShape for normalization.
- JudgeClient depends on HttpJson and ResponseShape for decisions validation.
- Both clients depend on Prefs for routing and credentials.
- ResponseShape throws ApiException for non-retryable protocol mismatches and internal errors.

```mermaid
graph TB
RC["ReplyClient.kt"] --> RS["ResponseShape.kt"]
RC --> PR["Prefs.kt"]
RC --> HJ["HttpJson.kt"]
JC["JudgeClient.kt"] --> RS
JC --> PR
JC --> HJ
```

**Diagram sources**
- [ReplyClient.kt:10-93](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt#L10-L93)
- [JudgeClient.kt:14-143](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L14-L143)
- [ResponseShape.kt:25-140](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L25-L140)
- [Prefs.kt:281-303](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L281-L303)
- [HttpJson.kt:14-20](file://app/src/main/java/com/jev/probe/jev/HttpJson.kt#L14-L20)

**Section sources**
- [ReplyClient.kt:10-93](file://app/src/main/java/com/jev/probe/jev/ReplyClient.kt#L10-L93)
- [JudgeClient.kt:14-143](file://app/src/main/java/com/jev/probe/jev/JudgeClient.kt#L14-L143)
- [ResponseShape.kt:25-140](file://app/src/main/java/com/jev/probe/jev/ResponseShape.kt#L25-L140)
- [Prefs.kt:281-303](file://app/src/main/java/com/jev/probe/core/Prefs.kt#L281-L303)

## Performance Considerations
- Latency tracking: Analysis.latencyMs captures judge call duration.
- Minimal retries: Non-retryable protocol errors avoid repeated failures; transport-level truncation remains retryable.
- Context injection: Optional background/history can degrade gracefully on 4xx by retrying without enriched fields.
- Candidate padding: Ensures UI stability with fixed rows even when fewer candidates are returned.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common issues and their handling:
- Gateway soft 404 with HTTP 200: ok() detects code/msg/success patterns and throws ApiException with non-retryable flag.
- Error envelope as object or string: ok() extracts message fields and throws.
- Empty or blank assistant content: chatContent() throws instead of returning empty strings.
- Wrong protocol (Anthropic messages vs OpenAI chat completions): chatContent() hints at mismatch.
- Missing answers in decisions: jevAnswers() throws with protocol hint.
- No usable candidates: threeCandidates() throws when parsing yields nothing.

Tests validate these behaviors explicitly.

**Section sources**
- [ResponseShapeTest.kt:23-81](file://app/src/test/java/com/jev/probe/jev/ResponseShapeTest.kt#L23-L81)
- [ResponseShapeTest.kt:85-113](file://app/src/test/java/com/jev/probe/jev/ResponseShapeTest.kt#L85-L113)
- [ResponseShapeTest.kt:117-128](file://app/src/test/java/com/jev/probe/jev/ResponseShapeTest.kt#L117-L128)
- [ResponseShapeTest.kt:132-156](file://app/src/test/java/com/jev/probe/jev/ResponseShapeTest.kt#L132-L156)

## Conclusion
The ResponseShape contracts and core models ensure consistent, safe, and interoperable behavior across diverse AI providers. By enforcing strict validation, providing clear error signaling, and normalizing provider-specific payloads into Analysis and RankedReply structures, the system avoids silent failures and maintains predictable UI experiences. Provider routing via Prefs allows flexible configuration while preserving backward compatibility and graceful degradation.

[No sources needed since this section summarizes without analyzing specific files]