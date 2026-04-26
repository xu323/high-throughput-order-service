---
name: mysql-transaction-design
description: MySQL schema 變更 / 交易設計 / 索引調整時的對齊指南。
---

# 何時使用
- 加新表 / 新欄位
- 改索引
- 改 `@Transactional` 邊界

# 工作步驟
1. 新增 `src/main/resources/db/migration/V{n}__xxx.sql`，**不要動歷史 migration**。
2. utf8mb4 / InnoDB 是預設；金額用 `DECIMAL(12,2)`；時間用 `DATETIME`。
3. 高頻 UPDATE 的欄位避免做 secondary index 的成員。
4. 排程器 / 定期掃描的 query 要有對應索引（如 `(status, expires_at)`）。
5. `@Transactional` 邊界放在 service public 方法；REQUIRES_NEW 必須跨 bean 否則被 self-invocation 失效。
6. 唯讀查詢標 `@Transactional(readOnly = true)`。
7. 更新 `docs/database-design.md`。

# 品質標準
- 寫 `@DataJpaTest` 驗證新 query 行為。
- 跑 `mvn test`、`mvn verify` 全綠。
- migration 在 H2 上至少能 parse（透過 application-test.yml 用 `MODE=MySQL`）。

# 禁止事項
- 不要用 `String.format` 拼 SQL（SQL injection）。
- 不要在 Java 端做能 push down 到 DB 的條件判斷。
- 不要在交易內呼叫 RPC / RabbitMQ publish 後依賴它的回應。

# 輸出格式
- 完整 SQL migration 檔
- 對應 entity / repository
- 對應 `@DataJpaTest`
