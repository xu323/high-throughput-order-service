---
name: high-concurrency-order-design
description: 設計或修改下單 / 庫存路徑時，避免超賣、死鎖、長交易；強制套用樂觀鎖或 Redis 鎖策略。
---

# 何時使用
- 改動 OrderService.create / cancel / expire
- 改動 InventoryService 任何方法
- 新增一個會扣資源（庫存、額度、座位）的 API

# 工作步驟
1. 區分 read-only vs read-modify-write。
2. read-modify-write 一律用「**條件式 UPDATE**」(`WHERE x >= q`) 配 `@Version` 樂觀鎖。
3. 衝突率高、跨服務 → 改 Redis 鎖（`RedisLockService.runWithLock`）。
4. 鎖區塊 ≤ 100ms；不要在鎖內做 RPC、寄信、發送 RabbitMQ。
5. 任何扣減失敗 → 丟 `InsufficientStockException` 或 `LockAcquisitionFailedException`。
6. 在 `stock_deduction_logs` 紀錄成功/失敗，用於對帳。
7. 新增併發測試（參考 `InventoryConcurrencyIntegrationTest`）。
8. 更新 `docs/concurrency-design.md`。

# 品質標準
- 任何變更後，跑一輪 `POST /api/demo/concurrency-test`，`overSold = false`。
- 樂觀鎖 retry 上限要可調；預設 5。
- Redis 鎖一定要用 token + Lua 腳本解鎖。

# 禁止事項
- 不要 `findById` → 改值 → `save`，沒有 `@Version` 防護。
- 不要把鎖區塊與外部 IO 混在一起。
- 不要直接 catch + swallow `OptimisticLockingFailureException`。

# 輸出格式
- 變更的 Java 檔完整內容
- 對應更新 docs
- 新增/修改一個併發測試
