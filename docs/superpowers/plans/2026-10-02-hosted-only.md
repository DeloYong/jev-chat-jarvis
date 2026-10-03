# 订阅版配置收口与输入入口关闭 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 网关用单个 `.env` 管理全部上游 URL/密钥/模型；订阅版（带 https 网关地址的构建）彻底移除自带密钥输入入口。

**Architecture:** 网关新增 `config.ts` 集中解析校验上游配置，`scripts/sync-env.mjs` 把 `.env` 同步到 `.dev.vars` 与线上 secrets。客户端用 `BuildConfig.HOSTED_ONLY` 切换；访问判定抽成无 Android 依赖的 `HostedPolicy` 并单测；设置页「接口」区抽成 `buildApiSection` 后在订阅版不调用。

**Tech Stack:** TypeScript / Cloudflare Workers / wrangler 3.x（网关）；Kotlin / View+XML / Gradle 8.9 / JDK 17（Android）。

**设计文档：** `docs/superpowers/specs/2026-10-02-hosted-only-design.md`

**全程约束（`CLAUDE.md`）：** 禁止 `git commit` / `git push`，每个任务末尾只做 `git diff --stat` 供人工 review；任何文件不得出现 `sk-or-` 开头字符串；UTF-8；Gradle 环境：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export JEV_KEYSTORE_PROPS=/tmp/none.properties
```

仓库路径：

- 网关 `GW=/Users/luckincoffee/Documents/project/AI/jev-chat-gateway`
- Android `APP=/Users/luckincoffee/Documents/project/AI/jev-chat-jarvis`

---

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `$GW/src/config.ts` | 新建 | 解析/校验上游配置，输出 `Upstreams` 或缺失项 |
| `$GW/test/config.test.ts` | 新建 | `upstreamsOf` 的单测 |
| `$GW/src/types.ts` | 改 | `Env` 增加 `REPLY_EXTRA_JSON?` |
| `$GW/src/proxy.ts` | 改 | 改用 `upstreamsOf`，未配置返回 500 |
| `$GW/.env.example` | 新建 | 全部配置项占位符 |
| `$GW/scripts/sync-env.mjs` | 新建 | `.env` → `.dev.vars` / secrets |
| `$GW/.gitignore`、`wrangler.toml`、`package.json` | 改 | 忽略 `.env`；移除 `[vars]` 配置；加脚本 |
| `$APP/.gitignore`、`$APP/.env.example` | 改/新建 | 忽略 `.env`；`JEV_CLOUD_BASE` 样例 |
| `$APP/app/build.gradle.kts` | 改 | 读 `.env`；生成 `HOSTED_ONLY` |
| `$APP/app/src/main/java/com/jev/probe/core/HostedPolicy.kt` | 新建 | 访问判定纯函数 |
| `$APP/app/src/test/java/com/jev/probe/core/HostedPolicyTest.kt` | 新建 | 上者单测 |
| `…/core/Prefs.kt` | 改 | 用 `HostedPolicy`；一次性清旧密钥 |
| `…/SettingsActivity.kt` | 改 | 抽 `buildApiSection`；订阅版不调用 |
| `…/MainActivity.kt` | 改 | 去掉自带密钥入口 |
| `…/overlay/OverlayController.kt`、`billing/PlanActivity.kt`、`capture/ChatCaptureService.kt`、`billing/EntitlementRepo.kt` | 改 | 文案与同意门控 |
| `$APP/PRIVACY.md`、`site/privacy.html`、`README.md` | 改 | 订阅版声明 |

---

# Part G — 网关配置收口（M0）

### Task G1: `upstreamsOf` 配置解析

**Files:**
- Create: `$GW/src/config.ts`, `$GW/test/config.test.ts`
- Modify: `$GW/src/types.ts`

- [ ] **Step 1: 写失败测试** `$GW/test/config.test.ts`

```ts
import test from "node:test";
import assert from "node:assert/strict";
import { upstreamsOf } from "../src/config.ts";
import type { Env } from "../src/types.ts";

const full = {
  JUDGE_UPSTREAM_URL: "https://judge.example.com/alpha/decisions",
  JUDGE_UPSTREAM_KEY: "k1",
  JUDGE_MODEL: "m1",
  CHAT_UPSTREAM_BASE: "https://chat.example.com/v1",
  CHAT_UPSTREAM_KEY: "k2",
  CHAT_MODEL: "m2",
} as unknown as Env;

test("完整配置通过", () => {
  const r = upstreamsOf(full);
  assert.equal(r.ok, true);
  if (r.ok) {
    assert.equal(r.value.judge.model, "m1");
    assert.deepEqual(r.value.reply.extra, {});
  }
});

test("缺项只报变量名", () => {
  const r = upstreamsOf({ ...full, JUDGE_UPSTREAM_KEY: " ", CHAT_MODEL: "" } as unknown as Env);
  assert.equal(r.ok, false);
  if (!r.ok) assert.deepEqual(r.missing, ["JUDGE_UPSTREAM_KEY", "CHAT_MODEL"]);
});

test("非 https 视为无效", () => {
  const r = upstreamsOf({ ...full, CHAT_UPSTREAM_BASE: "http://chat.example.com/v1" } as unknown as Env);
  assert.equal(r.ok, false);
  if (!r.ok) assert.deepEqual(r.missing, ["CHAT_UPSTREAM_BASE"]);
});

test("REPLY_EXTRA_JSON 合法时保留并丢弃保留字段", () => {
  const r = upstreamsOf({
    ...full,
    REPLY_EXTRA_JSON: '{"reasoning_effort":"low","max_tokens":99999,"model":"x","stream":true}',
  } as unknown as Env);
  assert.equal(r.ok, true);
  if (r.ok) assert.deepEqual(r.value.reply.extra, { reasoning_effort: "low" });
});

test("REPLY_EXTRA_JSON 不是对象视为无效", () => {
  for (const bad of ["[1]", "not json", "3", "null"]) {
    const r = upstreamsOf({ ...full, REPLY_EXTRA_JSON: bad } as unknown as Env);
    assert.equal(r.ok, false, bad);
    if (!r.ok) assert.deepEqual(r.missing, ["REPLY_EXTRA_JSON"]);
  }
});
```

- [ ] **Step 2: 确认失败**

Run: `cd $GW && node --experimental-transform-types --test test/config.test.ts`
Expected: FAIL，`Cannot find module '../src/config.ts'`。

- [ ] **Step 3: `Env` 增加字段**，在 `$GW/src/types.ts` 的 `CHAT_MODEL: string;` 后加：

```ts
  /** 可选: 附加到回复请求体的模型专属参数, 一行 JSON 对象, 如 {"reasoning_effort":"low"}。 */
  REPLY_EXTRA_JSON?: string;
```

- [ ] **Step 4: 实现** `$GW/src/config.ts`

```ts
import type { Env } from "./types.ts";

export interface JudgeUpstream { url: string; key: string; model: string }
/** extra: 附加到回复请求体的模型专属参数(已剔除网关强制字段)。 */
export interface ReplyUpstream { base: string; key: string; model: string; extra: Record<string, unknown> }
export interface Upstreams { judge: JudgeUpstream; reply: ReplyUpstream }

export type UpstreamsResult = { ok: true; value: Upstreams } | { ok: false; missing: string[] };

/** 这些字段由网关强制决定(模型、输出上限、非流式), 配置不得覆盖, 否则可绕过计量与成本控制。 */
const RESERVED_REPLY_KEYS = new Set(["model", "messages", "stream", "max_tokens", "temperature"]);

const isHttps = (v: string): boolean => {
  try {
    return new URL(v).protocol === "https:";
  } catch {
    return false;
  }
};

/**
 * 集中解析上游配置。返回缺失/非法的**变量名**(绝不含值), 调用方据此报 500 并记日志。
 */
export function upstreamsOf(env: Env): UpstreamsResult {
  const get = (k: keyof Env): string => String(env[k] ?? "").trim();
  const missing: string[] = [];

  for (const k of ["JUDGE_UPSTREAM_KEY", "JUDGE_MODEL", "CHAT_UPSTREAM_KEY", "CHAT_MODEL"] as const) {
    if (!get(k)) missing.push(k);
  }
  for (const k of ["JUDGE_UPSTREAM_URL", "CHAT_UPSTREAM_BASE"] as const) {
    if (!isHttps(get(k))) missing.push(k);
  }

  let extra: Record<string, unknown> = {};
  const raw = get("REPLY_EXTRA_JSON");
  if (raw) {
    try {
      const parsed: unknown = JSON.parse(raw);
      if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("not an object");
      extra = Object.fromEntries(Object.entries(parsed).filter(([k]) => !RESERVED_REPLY_KEYS.has(k)));
    } catch {
      missing.push("REPLY_EXTRA_JSON");
    }
  }

  // 保持 JUDGE_* 在前、CHAT_* 在后的稳定顺序, 日志与测试都更好读。
  const order = ["JUDGE_UPSTREAM_URL", "JUDGE_UPSTREAM_KEY", "JUDGE_MODEL", "CHAT_UPSTREAM_BASE", "CHAT_UPSTREAM_KEY", "CHAT_MODEL", "REPLY_EXTRA_JSON"];
  if (missing.length) return { ok: false, missing: missing.sort((a, b) => order.indexOf(a) - order.indexOf(b)) };

  return {
    ok: true,
    value: {
      judge: { url: get("JUDGE_UPSTREAM_URL"), key: get("JUDGE_UPSTREAM_KEY"), model: get("JUDGE_MODEL") },
      reply: { base: get("CHAT_UPSTREAM_BASE").replace(/\/+$/, ""), key: get("CHAT_UPSTREAM_KEY"), model: get("CHAT_MODEL"), extra },
    },
  };
}
```

- [ ] **Step 5: 确认通过**

Run: `cd $GW && node --experimental-transform-types --test test/config.test.ts`
Expected: 5 个用例全部 PASS（`# pass 5`）。

- [ ] **Step 6: 类型检查** `cd $GW && npx tsc --noEmit`，Expected: 无输出。

- [ ] **Step 7:** `git -C $GW status --short`（网关目录若无 git 仓库则跳过），不提交。

### Task G2: `proxy.ts` 改用 `upstreamsOf`

**Files:** Modify `$GW/src/proxy.ts`

- [ ] **Step 1: 引入并加守卫。** 在文件顶部 import 区追加：

```ts
import { upstreamsOf, type Upstreams } from "./config.ts";
```

在 `analysisIdOf` 函数之前加：

```ts
/** 上游未配置时直接 500; 只记变量名, 不记值。 */
function upstreamsOrFail(env: Env): Upstreams | Response {
  const r = upstreamsOf(env);
  if (r.ok) return r.value;
  console.error(`upstream_not_configured: ${r.missing.join(",")}`);
  return fail(500, "upstream_not_configured", "服务暂未配置上游");
}
```

- [ ] **Step 2: 改 `handleJudge` / `handleChat`。** 把两个函数整体替换为：

```ts
export function handleJudge(req: Request, env: Env, store: Store, user: User, cfg: MeteringConfig): Promise<Response> {
  const up = upstreamsOrFail(env);
  if (up instanceof Response) return Promise.resolve(up);
  return metered(req, store, user, cfg, {
    prepare: (body) => (body.state && typeof body.state === "object" && body.questions && typeof body.questions === "object" ? body : null),
    send: (body) =>
      fetch(up.judge.url, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${up.judge.key}` },
        // 模型由网关决定, 客户端传的 model 一律忽略。
        body: JSON.stringify({ ...body, model: up.judge.model }),
        signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
      }),
  });
}

export function handleChat(req: Request, env: Env, store: Store, user: User, cfg: MeteringConfig): Promise<Response> {
  const up = upstreamsOrFail(env);
  if (up instanceof Response) return Promise.resolve(up);
  return metered(req, store, user, cfg, {
    prepare: (body) => {
      const messages = body.messages;
      if (!Array.isArray(messages) || messages.length === 0 || messages.length > CHAT_MAX_MESSAGES) return null;
      let chars = 0;
      const clean: { role: string; content: string }[] = [];
      for (const m of messages) {
        const role = (m as { role?: unknown })?.role;
        const content = (m as { content?: unknown })?.content;
        if ((role !== "system" && role !== "user") || typeof content !== "string") return null;
        chars += content.length;
        clean.push({ role, content });
      }
      if (chars > CHAT_MAX_CHARS) return null;
      const t = typeof body.temperature === "number" ? Math.min(Math.max(body.temperature, 0), 1.2) : 0.7;
      return { messages: clean, temperature: t };
    },
    send: (p) =>
      fetch(`${up.reply.base}/chat/completions`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${up.reply.key}` },
        // extra 在前, 网关强制字段在后, 配置无法覆盖 max_tokens / stream。
        body: JSON.stringify({ model: up.reply.model, ...up.reply.extra, ...p, max_tokens: CHAT_MAX_TOKENS, stream: false }),
        signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
      }),
  });
}
```

- [ ] **Step 3: 类型检查** `cd $GW && npx tsc --noEmit`，Expected: 无输出。

- [ ] **Step 4: 回归旧脚本。** `/tmp/gwtest/run.ts`、`pay.ts` 是之前的临时自测（不在仓库内，若已不存在则跳过）：

Run: `cd $GW && node --experimental-transform-types /tmp/gwtest/run.ts && node --experimental-transform-types /tmp/gwtest/pay.ts`
Expected: 末行分别为 `ALL GATEWAY CHECKS PASSED`、`ALL PAY CHECKS PASSED`。若失败原因是脚本 env 里上游 URL 非 https（新校验），把脚本里的假上游改成 `https://…` 再跑，不改 `config.ts` 的 https 校验。

### Task G3: `.env.example`、同步脚本、清理 `wrangler.toml`

**Files:**
- Create: `$GW/.env.example`, `$GW/scripts/sync-env.mjs`
- Modify: `$GW/.gitignore`, `$GW/wrangler.toml`, `$GW/package.json`

- [ ] **Step 1: 确认 wrangler 子命令。** Run: `cd $GW && npx wrangler secret --help`。Expected: 输出里能看到 `bulk` 子命令；若叫法不同（如 `secret:bulk`），以实际输出为准，修改下一步脚本里 `args`，并把设计文档第 3 节的【未验证】标记改为已验证。

- [ ] **Step 2: `.env.example`**（只放占位符）

```dotenv
# 复制为 .env 后填写。.env 与 .dev.vars 已在 .gitignore, 绝不提交。
# PEM 值写成单行, 换行用字面量 \n, 同步脚本会还原。

# ---- 判断上游 (Jev decisions 协议) ----
JUDGE_UPSTREAM_URL=https://openrouter.ai/api/alpha/decisions
JUDGE_UPSTREAM_KEY=<上游判断接口密钥>
JUDGE_MODEL=typesafe/jev-1.13

# ---- 回复上游 (OpenAI 兼容 chat/completions, base 填到 /v1) ----
CHAT_UPSTREAM_BASE=https://openrouter.ai/api/v1
CHAT_UPSTREAM_KEY=<上游回复接口密钥>
CHAT_MODEL=deepseek/deepseek-chat-v3.1
# 可选: 模型专属参数, 一行 JSON 对象, 例 {"reasoning_effort":"low"}; model/messages/stream/max_tokens/temperature 会被忽略
REPLY_EXTRA_JSON=

# ---- 计量 ----
TRIAL_CREDITS=30
DAILY_CAP=300
REGISTER_PER_IP_PER_DAY=5
# 随机长字符串, 上线后不可更换(设备哈希依赖它)
TOKEN_PEPPER=<随机长字符串>

# ---- 支付 ----
PAY_PUBLIC_BASE=https://<网关域名>
ALIPAY_APP_ID=
ALIPAY_PRIVATE_KEY=
ALIPAY_PUBLIC_KEY=
WECHAT_APP_ID=
WECHAT_MCH_ID=
WECHAT_SERIAL_NO=
WECHAT_PRIVATE_KEY=
WECHAT_API_V3_KEY=
WECHAT_PLATFORM_PUBLIC_KEY=
# 仅本地联调置 1; 线上必须为空
ALLOW_MOCK_PAY=
```

- [ ] **Step 3: `scripts/sync-env.mjs`**

```js
// 用法:
//   node scripts/sync-env.mjs           .env -> .dev.vars (本地 wrangler dev 读取)
//   node scripts/sync-env.mjs --push    另把全部非空键作为 secrets 推到线上
// .env 是唯一配置源; 线上不再使用 wrangler.toml [vars], 避免与 secret 同名冲突。
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const REQUIRED = ["JUDGE_UPSTREAM_URL", "JUDGE_UPSTREAM_KEY", "JUDGE_MODEL", "CHAT_UPSTREAM_BASE", "CHAT_UPSTREAM_KEY", "CHAT_MODEL", "TOKEN_PEPPER"];

function parseEnv(text) {
  const out = {};
  for (const line of text.split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const i = t.indexOf("=");
    if (i < 1) continue;
    const key = t.slice(0, i).trim();
    let val = t.slice(i + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) val = val.slice(1, -1);
    out[key] = val.replace(/\\n/g, "\n");
  }
  return out;
}

const env = parseEnv(readFileSync(".env", { encoding: "utf8" }));
const missing = REQUIRED.filter((k) => !env[k] || env[k].startsWith("<"));
if (missing.length) {
  console.error(`.env 缺少或仍是占位符: ${missing.join(", ")}`);
  process.exit(1);
}

// .dev.vars 用 JSON 字符串转义多行值, 这样 PEM 的换行不会破坏格式。
const devVars = Object.entries(env).filter(([, v]) => v !== "").map(([k, v]) => `${k}=${JSON.stringify(v)}`).join("\n") + "\n";
writeFileSync(".dev.vars", devVars, { encoding: "utf8", mode: 0o600 });
console.log(`已写入 .dev.vars (${Object.keys(env).length} 项)`);

if (process.argv.includes("--push")) {
  const dir = mkdtempSync(join(tmpdir(), "jev-gw-"));
  const file = join(dir, "secrets.json");
  try {
    const payload = Object.fromEntries(Object.entries(env).filter(([, v]) => v !== ""));
    writeFileSync(file, JSON.stringify(payload), { encoding: "utf8", mode: 0o600 });
    const r = spawnSync("npx", ["wrangler", "secret", "bulk", file], { stdio: "inherit" });
    if (r.status !== 0) process.exit(r.status ?? 1);
  } finally {
    rmSync(dir, { recursive: true, force: true });  // 临时文件含明文密钥, 无论成败都删
  }
}
```

- [ ] **Step 4: `.gitignore`** 追加两行（若已有则跳过）：`.env` 和 `.dev.vars`。

- [ ] **Step 5: `wrangler.toml`**：删除整个 `[vars]` 段及其上方的 secrets 说明注释（配置已全部迁入 `.env`），保留 `name / main / compatibility_date / [[d1_databases]]`，并在文件末尾加：

```toml
# 所有运行时配置(上游 URL/密钥/模型、额度、支付参数)统一写在 .env,
# 由 `npm run env:sync`(本地) / `npm run env:push`(线上 secrets) 同步, 见 .env.example。
```

- [ ] **Step 6: `package.json` scripts** 增加：

```json
    "test": "node --experimental-transform-types --test test/config.test.ts",
    "env:sync": "node scripts/sync-env.mjs",
    "env:push": "node scripts/sync-env.mjs --push"
```

- [ ] **Step 7: 验证。**

```bash
cd $GW && cp .env.example .env && node scripts/sync-env.mjs; echo "exit=$?"
```
Expected: 打印 `.env 缺少或仍是占位符: JUDGE_UPSTREAM_KEY, CHAT_UPSTREAM_KEY, TOKEN_PEPPER`，`exit=1`（占位符被拒绝）。然后 `rm .env`。再跑 `npm test` 与 `npx tsc --noEmit`，Expected: 全部通过。
最后 `grep -rn "sk-or-" $GW --include='*' -l --exclude-dir=node_modules`，Expected: 无输出。

---

# Part A — Android 订阅版（M1）

### Task A1: 构建开关 `HOSTED_ONLY` 与根 `.env`

**Files:** Modify `$APP/app/build.gradle.kts`, `$APP/.gitignore`；Create `$APP/.env.example`

- [ ] **Step 1: `.gitignore`** 在 "Never commit secrets" 段追加：

```
.env
!.env.example
```

- [ ] **Step 2: `$APP/.env.example`**

```dotenv
# 复制为 .env(已 gitignore)。只放非敏感的网关地址, 绝不要在这里放任何上游密钥。
# 配置后构建为"订阅版": 彻底关闭自带密钥入口。留空 = 开源版, 行为不变。
JEV_CLOUD_BASE=https://gw.example.com
```

- [ ] **Step 3: `app/build.gradle.kts`。** 在 `releaseProps` 块之后加：

```kotlin
// 仓库根 .env 只放非敏感的 JEV_CLOUD_BASE。优先级: -PjevCloudBase > .env > 空。
val dotEnv = Properties().apply {
    val f = rootProject.file(".env")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}
```

把 `defaultConfig` 里原来的两行：

```kotlin
        val cloudBase = (project.findProperty("jevCloudBase") as String?) ?: ""
        buildConfigField("String", "CLOUD_BASE_URL", "\"${cloudBase.replace("\"", "")}\"")
```

替换为：

```kotlin
        val cloudBase = ((project.findProperty("jevCloudBase") as String?)
            ?: dotEnv.getProperty("JEV_CLOUD_BASE") ?: "").trim().replace("\"", "")
        buildConfigField("String", "CLOUD_BASE_URL", "\"$cloudBase\"")
        // 订阅版: 带 https 网关地址即关闭自带密钥入口; 非 https 视为未配置, 防止明文传令牌。
        buildConfigField("boolean", "HOSTED_ONLY", cloudBase.startsWith("https://").toString())
```

- [ ] **Step 4: 验证两种构建的常量**

```bash
cd $APP && ./gradlew :app:compileDebugKotlin -q && grep -n "HOSTED_ONLY" app/build/generated/source/buildConfig/debug/com/jev/probe/BuildConfig.java
./gradlew :app:compileDebugKotlin -q -PjevCloudBase=https://gw.example.com && grep -n "HOSTED_ONLY" app/build/generated/source/buildConfig/debug/com/jev/probe/BuildConfig.java
```
Expected: 第一次 `HOSTED_ONLY = false;`，第二次 `HOSTED_ONLY = true;`。

### Task A2: `HostedPolicy` + `Prefs`

**Files:**
- Create: `…/core/HostedPolicy.kt`, `app/src/test/java/com/jev/probe/core/HostedPolicyTest.kt`
- Modify: `…/core/Prefs.kt`

- [ ] **Step 1: 写失败测试** `HostedPolicyTest.kt`

```kotlin
package com.jev.probe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostedPolicyTest {
    @Test fun openSourceNeedsSwitchAndToken() {
        assertFalse(HostedPolicy.cloudActive(false, enabled = false, available = true, token = "t"))
        assertFalse(HostedPolicy.cloudActive(false, enabled = true, available = true, token = ""))
        assertTrue(HostedPolicy.cloudActive(false, enabled = true, available = true, token = "t"))
    }

    @Test fun hostedOnlyIgnoresSwitchButStillNeedsToken() {
        assertTrue(HostedPolicy.cloudActive(true, enabled = false, available = true, token = "t"))
        assertFalse(HostedPolicy.cloudActive(true, enabled = false, available = true, token = " "))
        assertFalse(HostedPolicy.cloudActive(true, enabled = true, available = false, token = "t"))
    }

    @Test fun gatewayRouteIsAlwaysOnWhenHostedOnly() {
        assertTrue(HostedPolicy.gatewayRoute(hostedOnly = true, cloudActive = false))
        assertFalse(HostedPolicy.gatewayRoute(hostedOnly = false, cloudActive = false))
        assertTrue(HostedPolicy.gatewayRoute(hostedOnly = false, cloudActive = true))
    }

    @Test fun ownKeyNeverGrantsAccessInHostedOnly() {
        assertFalse(HostedPolicy.hasAccess(true, cloudActive = false, ownJudgeKey = "k"))
        assertTrue(HostedPolicy.hasAccess(false, cloudActive = false, ownJudgeKey = "k"))
        assertTrue(HostedPolicy.hasAccess(true, cloudActive = true, ownJudgeKey = ""))
        assertEquals(false, HostedPolicy.hasAccess(false, cloudActive = false, ownJudgeKey = ""))
    }
}
```

- [ ] **Step 2: 确认失败。** Run: `cd $APP && ./gradlew :app:testDebugUnitTest --tests 'com.jev.probe.core.HostedPolicyTest' -q`。Expected: 编译失败 `Unresolved reference: HostedPolicy`。

- [ ] **Step 3: 实现** `app/src/main/java/com/jev/probe/core/HostedPolicy.kt`

```kotlin
package com.jev.probe.core

/**
 * 订阅版(HOSTED_ONLY)下的访问判定。抽成纯函数, 不依赖 Android, 便于单测。
 * 核心不变量: 订阅版任何情况下都不能把聊天文字发往第三方, 自带密钥也不能授予访问权。
 */
object HostedPolicy {
    /** 托管会话是否生效。订阅版不再有"启用托管"开关, 只看是否已注册拿到令牌。 */
    fun cloudActive(hostedOnly: Boolean, enabled: Boolean, available: Boolean, token: String): Boolean =
        available && token.isNotBlank() && (hostedOnly || enabled)

    /** 端点与凭证是否一律走网关。订阅版即使令牌暂缺也走网关(得到 401 自愈), 不回落到第三方。 */
    fun gatewayRoute(hostedOnly: Boolean, cloudActive: Boolean): Boolean = hostedOnly || cloudActive

    /** 是否允许发起分析。订阅版只认托管会话。 */
    fun hasAccess(hostedOnly: Boolean, cloudActive: Boolean, ownJudgeKey: String): Boolean =
        cloudActive || (!hostedOnly && ownJudgeKey.isNotBlank())
}
```

- [ ] **Step 4: 改 `Prefs.kt`。**

(a) `init` 行改为：

```kotlin
    init { if (prefsName == PREFS_MAIN) { migrateIfNeeded(); unseedBochaDefaultIfUnconfigured(); wipeByokIfHostedOnly() } }
```

(b) 在 `unseedBochaDefaultIfUnconfigured` 函数之后加：

```kotlin
    /**
     * 订阅版首次启动: 清掉历史自带密钥与旧 openrouter_key。它们在订阅版里再也用不上,
     * 留着只是多一份泄露面。只清密钥, 不动接口地址/模型名。一次性, 靠标记位防重复。
     * 必须排在 migrateIfNeeded 之后: 先迁移再清, 不会让旧密钥被重新拷回。
     */
    private fun wipeByokIfHostedOnly() {
        if (!hostedOnly || sp.getBoolean(K_BYOK_WIPED, false)) return
        sp.edit().remove(K_JUDGE_KEY).remove(K_REPLY_KEY).remove(K_VISION_KEY).remove(K_LEGACY_KEY)
            .putBoolean(K_BYOK_WIPED, true).apply()
        Log.i(TAG, "prefs: hosted-only build, cleared legacy BYOK keys")
    }

    /** 订阅版开关, 来自构建期的网关地址。 */
    private val hostedOnly: Boolean get() = com.jev.probe.BuildConfig.HOSTED_ONLY
```

(c) 把 `cloudActive()` 到 `cloudHeaders` 这一段（从 `fun cloudActive()` 起，到 `cloudHeaders` 结束）替换为：

```kotlin
    fun cloudActive(): Boolean = HostedPolicy.cloudActive(hostedOnly, cloudEnabled, cloudAvailable(), cloudToken)

    /** 为真时端点与凭证一律指向网关; 订阅版恒为真, 保证不会回落到第三方。 */
    private fun gatewayRoute(): Boolean = HostedPolicy.gatewayRoute(hostedOnly, cloudActive())

    /** Judge route credential: the gateway token on the gateway route, else the user's own key. */
    fun judgeRouteKey(): String = if (gatewayRoute()) cloudToken else judgeKey

    /** Reply route credential, same rule as [judgeRouteKey]. */
    fun replyRouteKey(): String = if (gatewayRoute()) cloudToken else effectiveReplyKey()

    /**
     * Metering header for the gateway: calls sharing one id are billed once.
     * Empty for every third-party provider, which must never see it.
     */
    fun cloudHeaders(analysisId: String): Map<String, String> =
        if (gatewayRoute()) mapOf("X-Analysis-Id" to analysisId) else emptyMap()
```

(d) `judgeEndpoint()` 第一行 `if (cloudActive()) return …` 改为 `if (gatewayRoute()) return "${cloudBase()}/v1/judge"`；`replyEndpoint()` 里的 `if (cloudActive())` 改为 `if (gatewayRoute())`。

(e) `hasKey()` 与 `hasAccess()` 改为：

```kotlin
    /** Readiness gate for own-key mode. Always false in the subscription build. */
    fun hasKey(): Boolean = !hostedOnly && judgeKey.isNotBlank()

    /** Analysis can run: an active hosted session, or (open-source build only) the user's own judge key. */
    fun hasAccess(): Boolean = HostedPolicy.hasAccess(hostedOnly, cloudActive(), judgeKey)
```

(f) 常量区 `K_CLOUD_ORDER` 之后加：

```kotlin
        private const val K_BYOK_WIPED = "byok_wiped_hosted_v1"
```

- [ ] **Step 5: 确认通过。** Run: `./gradlew :app:testDebugUnitTest -q`。Expected: 无失败（含新 4 个用例与原有测试）。

### Task A3: 设置页抽出并按版本隐藏「接口」区

**Files:** Modify `…/SettingsActivity.kt`

- [ ] **Step 1: 机械抽取。** 区间用锚点而非行号。在仓库根执行：

```bash
cd $APP && python3 - <<'PY'
import pathlib
p = pathlib.Path("app/src/main/java/com/jev/probe/SettingsActivity.kt")
s = p.read_text(encoding="utf-8")

a = s.index("        // =================== 接口 ===================")
tail = "        root.addView(visionCard)\n"
b = s.index(tail, a) + len(tail)
block = s[a:b]

c = s.index("            // Address wins over the pill")
d = s.index("            prefs.relationship = relEdit")
save = s[c:d]

# 先改靠后的位置, 前面的下标不失效
s = s[:c] + "            saveApi()\n\n" + s[d:]
s = s[:a] + "        // 订阅版没有自带接口, 整区不渲染; 开源版返回保存闭包, 由底部保存按钮触发。\n" \
            "        val saveApi: () -> Unit = if (BuildConfig.HOSTED_ONLY) ({}) else buildApiSection(root)\n\n" + s[b:]

fn = ("    /** 接口配置三张卡(判断/回复/视觉)。订阅版不调用; 返回\"保存\"闭包。 */\n"
      "    private fun buildApiSection(root: LinearLayout): () -> Unit {\n"
      + block + "\n        return {\n" + save + "        }\n    }\n\n")
anchor = "    // Held as fields because several test buttons read each other's key box."
assert anchor in s
s = s.replace(anchor, fn + anchor, 1)
p.write_text(s, encoding="utf-8")
PY
```

- [ ] **Step 2: 关于文案按版本切换。** 把 `aboutCard.addView(text(` 后的那句原文：

```kotlin
            "这个 App 会读取你当前聊天窗口的文字，发给你自己配置的模型接口做判断和起草回复。作者不运营服务器，收不到你的数据。",
```

替换为：

```kotlin
            if (BuildConfig.HOSTED_ONLY) "这个 App 会读取你当前聊天窗口的文字，发往官方托管服务做判断和起草回复。服务端不保存聊天正文，详见隐私政策。"
            else "这个 App 会读取你当前聊天窗口的文字，发给你自己配置的模型接口做判断和起草回复。作者不运营服务器，收不到你的数据。",
```

- [ ] **Step 3: 编译。** Run: `./gradlew :app:compileDebugKotlin -q`。Expected: 无错误。若报 `Unresolved reference: BuildConfig`，在文件 import 区加 `import com.jev.probe.BuildConfig`（同包通常无需 import）。若报某个变量未定义，说明它在被抽出区间之外被引用：把该引用改为 `buildApiSection` 内本地可见的值，不要扩大抽取范围。

- [ ] **Step 4: 确认保存闭包完整。** Run: `grep -n "saveApi\|buildApiSection" app/src/main/java/com/jev/probe/SettingsActivity.kt`。Expected: 3 处——声明处 1、定义 1、保存按钮内调用 1。

### Task A4: 首页、悬浮窗、付费页、服务文案与同意门控

**Files:** Modify `MainActivity.kt`、`overlay/OverlayController.kt`、`billing/PlanActivity.kt`、`capture/ChatCaptureService.kt`、`billing/EntitlementRepo.kt`

- [ ] **Step 1: `MainActivity.kt`。**

(a) 设置入口副标题：

```kotlin
        container.addView(actionRow("设置",
            if (BuildConfig.HOSTED_ONLY) "模型 · 关系 · 透明度 · 会话白名单" else "密钥 · 模型 · 关系 · 透明度 · 会话白名单") {
```

（`"设置"` 之后原来的字符串参数换成上面的条件表达式，lambda 保持不变。）

(b) `statusCard` 里：

```kotlin
        val hosted = prefs.cloudActive() || BuildConfig.HOSTED_ONLY
        c.addView(checkLine(if (hosted) "官方托管" else "密钥", key,
            okWord = if (hosted) "已启用" else "已设", noWord = if (hosted) "未启用" else "未设"))
```

(c) `cloudCard()` 活动分支里，把整个 `row.addView(text("改用自己的密钥", …)…)` 用 `if (!BuildConfig.HOSTED_ONLY) { … }` 包起来；未激活分支里 `if (newcomer) row.addView(text("我有自己的密钥", …)…)` 条件改为 `if (newcomer && !BuildConfig.HOSTED_ONLY)`。

(d) 同意框 `setMessage(...)` 的最后一段 `"不想这样，可以随时改回自己的密钥，数据就只发往你自己配置的接口。"` 改为：

```kotlin
                (if (BuildConfig.HOSTED_ONLY) "不同意则无法使用分析功能，可以先不开通。"
                else "不想这样，可以随时改回自己的密钥，数据就只发往你自己配置的接口。"))
```

注意括号与上一行的 `+` 拼接保持语法正确。

(e) `privacyHint()` 条件改为 `if (prefs.cloudActive() || BuildConfig.HOSTED_ONLY)`。

- [ ] **Step 2: `OverlayController.kt`。** 文件 import 区加 `import com.jev.probe.BuildConfig`。`showPaywall` 整体替换为：

```kotlin
    fun showPaywall(msg: String) {
        ensureRoot(); bubble?.alpha = 1f
        val items = mutableListOf<View>(
            line("试用已结束", "#3A7AFE", 14f, true),
            hint(msg),
            bigButton("开通订阅") { openScreen("com.jev.probe.billing.PlanActivity") })
        // 订阅版没有自带密钥这条路, 不展示提示。
        if (!BuildConfig.HOSTED_ONLY) {
            items += hint("也可以填自己的接口密钥，继续免费使用").apply {
                setPadding(0, dp(8), 0, 0)
                setOnClickListener { openScreen("com.jev.probe.SettingsActivity") }
            }
        }
        setContent(items)
        if (!expanded) toggle()
    }
```

同时把其上方 KDoc 里 `with "use my own key" as the free alternative` 改为 `with "use my own key" as the free alternative (open-source build only)`。

- [ ] **Step 3: `PlanActivity.kt`。** 文件末尾那条提示：

```kotlin
        if (!BuildConfig.HOSTED_ONLY) {
            container.addView(label("不想订阅？在设置里填自己的接口密钥，同样免费使用。", 12f, sub)
                .apply { setPadding(dp(2), dp(14), 0, 0) })
        }
```

（原来的 `container.addView(label("不想订阅？…` 两行整体放进 if。该文件已用到 `BuildConfig.DEBUG`，无需新增 import。）

- [ ] **Step 4: `ChatCaptureService.kt`** 第 399 行附近：

```kotlin
        if (!prefs.hasAccess()) {
            overlay?.showError(if (com.jev.probe.BuildConfig.HOSTED_ONLY) "请先打开 App 首页，点「开始试用」"
                else "未设置判断接口密钥，去设置里填")
            return
        }
```

- [ ] **Step 5: `EntitlementRepo.kt` 同意门控。**

`register` 里 `require(prefs.cloudAvailable()) …` 之后加：

```kotlin
        require(prefs.cloudConsent) { "hosted mode needs the user's consent first" }
```

`refreshQuietly` 首行改为：

```kotlin
        // 未同意前绝不发请求; 订阅版没有"启用托管"开关, 只看是否可用。
        if (!prefs.cloudAvailable() || !prefs.cloudConsent) return
        if (!prefs.cloudEnabled && !com.jev.probe.BuildConfig.HOSTED_ONLY) return
```

- [ ] **Step 6: 编译与单测。** Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug -q`，再 `./gradlew :app:assembleDebug -q -PjevCloudBase=https://gw.example.com`。Expected: 两次都 `BUILD SUCCESSFUL`，无新 warning 指向被改文件。

- [ ] **Step 7: 残留检查。** Run:

```bash
cd $APP && grep -rn "自己的密钥\|自己的接口密钥" app/src/main/java
```
Expected: 只剩 `MainActivity.kt`、`OverlayController.kt`、`PlanActivity.kt` 里被 `!BuildConfig.HOSTED_ONLY` 保护的行和同意框的开源版分支；每处肉眼确认都在条件内。

### Task A5: 隐私文档与 README

**Files:** Modify `$APP/PRIVACY.md`、`$APP/site/privacy.html`、`$APP/README.md`

- [ ] **Step 1: `PRIVACY.md`。** 版本改 `v1.2`（日期 `2026-10-02` 保持）。逐条替换（先用 `grep -n` 定位，行号以实际为准）：

| 位置 | 原文关键句 | 改为 |
|---|---|---|
| 两种模式列表之后（约第 6 行后） | 无 | 新增一行：`> **订阅版（内置托管网关地址的官方构建）只有托管模式，没有自带密钥入口，设置页没有接口/密钥输入框。** 下文"自带密钥模式"只适用于不带网关地址、自行从源码编译的构建。` |
| 2A 节首句（约第 47 行） | `随时可在首页切回自己的密钥，切回后内容不再发往运营方。` | `订阅版不提供切回自带密钥的入口；不同意则无法使用分析功能，已发出的请求按下文留存规则处理。` |
| 本机数据表令牌行（约第 78 行） | `切回自带密钥后令牌仍保留在本机，卸载即清除` | `令牌保留在本机，卸载即清除；订阅版首次启动会清除升级前保存的自带密钥` |
| 约第 104 行末句 | `不放心请用自带密钥模式` | `不放心请不要使用订阅版，或自行编译开源版并使用自己的密钥` |
| 约第 111 行 | `更换或自建接口：` 开头那条 | 句首加 `（仅自行编译的开源构建）` |
| 约第 115 行 | `随时退出托管：首页「改用自己的密钥」即可切回，之后聊天内容不再发往运营方。` | `退出托管：卸载应用即可停止发送；订阅版不提供切回自带密钥。` |
| 英文摘要（约第 141 行） | `You can switch back to your own key at any time.` | `The subscription build has no bring-your-own-key option; build the open-source version from source to use your own key.` |

- [ ] **Step 2: `site/privacy.html`** 做同样 7 处替换（位置以 `grep -n "切回\|自带密钥" site/privacy.html` 为准），并在 `<li><strong>自带密钥模式（默认）</strong>…` 前加上同样的订阅版声明 `<p>`；`<meta description>` 里"默认自带密钥模式下作者不运营服务器"改为"订阅版只有官方托管模式；自行编译的开源版为自带密钥模式"。

- [ ] **Step 3: `README.md`**（第 69、158、179 行附近）：第 69 行 `接口自己配。` 开头加 `（开源构建）`；第 179 行 `自带密钥的用法始终免费` 改为 `自带密钥的用法仅在自行编译的开源构建中提供，始终免费`。

- [ ] **Step 4: 一致性检查。** Run:

```bash
cd $APP && grep -n "随时.*切回\|改用自己的密钥" PRIVACY.md site/privacy.html README.md
```
Expected: 无输出。

### Task A6: 总验收

- [ ] **Step 1: 全量构建与测试**

```bash
cd $APP && ./gradlew :app:testDebugUnitTest :app:assembleDebug -q && ./gradlew :app:assembleDebug -q -PjevCloudBase=https://gw.example.com
cd $GW && npm test && npx tsc --noEmit
```
Expected: 全部成功。

- [ ] **Step 2: 密钥与跟踪检查**

```bash
cd $APP && git check-ignore -v .env; git ls-files | grep -E '(^|/)\.env$' ; grep -rn "sk-or-" . --include='*.kt' --include='*.kts' --include='*.md' --include='*.html' --include='*.example' -l --exclude-dir=build --exclude-dir=.git --exclude-dir=.qoder
```
Expected: 第一条显示 `.env` 被 `.gitignore` 命中（若本地没有 `.env` 文件，先 `touch .env` 再测，测完删除）；第二条无输出；第三条无输出。

- [ ] **Step 3: 变更清单供 review（不提交）。** Run: `git -C $APP diff --stat` 与 `git -C $APP status --short`。

- [ ] **Step 4: 写报告** `_reports/hosted-only_report.md`（`CLAUDE.md` 报告格式）：做法 → 改动文件 → 上述命令的真实输出 → **自验缺口**：订阅版设置页真机确无接口区；旧版升级后旧密钥已清；同意前抓包无任何对网关的请求；`wrangler secret bulk` 实际推送；`.env` 经 `env:push` 上线后网关返回正确。

---

## Self-Review（对照设计文档）

| 设计要求 | 对应任务 |
|---|---|
| 网关单一 `.env`、`upstreamsOf` 校验、保留字段剔除、500 `upstream_not_configured` | G1、G2、G3 |
| `sync-env` 本地/线上、PEM 单行、临时文件清理 | G3 |
| 客户端 `.env`、`HOSTED_ONLY`、https 才算 | A1 |
| `HostedPolicy` 三函数 + 单测 | A2 |
| `Prefs` 端点恒指网关、`hasKey` 恒 false、一次性清旧密钥 | A2 |
| 设置页接口区不渲染、关于文案 | A3 |
| 首页/悬浮窗/付费页/服务文案 | A4 |
| `register` 与 `refreshQuietly` 同意门控 | A4 Step 5 |
| 隐私文档与 README | A5 |
| 构建/测试/密钥检查/自验缺口 | A6 |

类型一致性：`upstreamsOf` / `Upstreams.judge.{url,key,model}` / `Upstreams.reply.{base,key,model,extra}` 在 G1、G2 一致；`HostedPolicy.cloudActive/gatewayRoute/hasAccess` 的参数名在 A2 测试与实现一致；`saveApi` / `buildApiSection` 在 A3 内一致。
