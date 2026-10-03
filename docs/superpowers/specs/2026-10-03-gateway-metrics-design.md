# 网关用量/错误率统计（M2）— 设计

状态：方案 A 已与项目主人确认（2026-10-03）。范围仅统计；上游备用切换、告警阈值、per-user 明细**不做**，看到数据再立项。
仓库：`/Users/luckincoffee/Documents/project/AI/jev-chat-gateway`。

## 1. 方案取舍

| 方案 | 结论 |
|---|---|
| A. D1 聚合表 + `npm run stats`（wrangler 查远端） | **采用**：不新增公网入口与密钥 |
| B. `GET /admin/stats` + `ADMIN_TOKEN` | 否：多一个公网入口和一个密钥 |
| C. Workers Analytics Engine / Logpush | 否：额外绑定，是否收费【未验证，需查 Cloudflare 官方文档】 |

## 2. 数据

表 `metrics`，主键 `(day, route, model, outcome)`，只存聚合，**不存用户 ID、不存任何正文**。

| 列 | 含义 |
|---|---|
| `day` | UTC+8 日期（沿用 `dayOf`） |
| `route` | `judge` / `chat` |
| `model` | 当次配置的模型名，换模型后可对比 |
| `outcome` | `ok` / `up_timeout`（超时或不可达）/ `up_4xx` / `up_5xx` / `blocked_402` / `blocked_429` / `bad_request` |
| `calls` | 次数 |
| `latency_ms_sum` | 上游耗时累计；未发出上游请求的结局为 0 |
| `tokens_sum` | 尽力解析响应 `usage.total_tokens`，没有记 0。Jev decisions 响应是否带 `usage`【未验证】 |

## 3. 写入

- `metered()` 每个出口各记一次，`Store.recordMetric()` 为单语句 upsert。
- 写入失败只吞掉：统计绝不能影响用户请求、计量、退款。
- 平均耗时 = `latency_ms_sum / 发出上游请求的次数`，其中"发出上游请求"= `ok + up_*`。

## 4. 报表 `scripts/stats.mjs`

`npm run stats [-- --days 7] [-- --local]`，默认查远端 D1。输出：
1. 每天每 route：总调用、错误率（`up_*` ÷ `ok+up_*`）、平均耗时、平均 token。
2. 拦截：402 / 429 条数，用于判断试用额度与每日上限。
3. 漏斗（直接从 `users` / `orders` 算）：每日新增用户、已支付订单数与金额、当前有效订阅数。

## 5. 验收

- `npm test` 含 `metrics.test.ts`：每种结局各写一行、同键累计、`recordMetric` 抛错不影响响应、`tokens` 解析。
- `npx tsc --noEmit` 无输出；`/tmp/gwtest/run.ts`、`pay.ts` 不回归。
- `stats.mjs` 用 `wrangler d1 execute --local` 灌入 schema 后实跑一次。
- **自验缺口**：远端 D1 上的实跑；Jev 真实响应是否带 `usage`。
