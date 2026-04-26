---
name: redis-cache-lock
description: 改動或新增 Redis cache / 分散式鎖時，套用 Cache-Aside 與短鎖區段原則。
---

# 何時使用
- 加快某個熱資料的讀取
- 需要跨服務串行化的臨界區
- 改動 ProductCacheService 或 RedisLockService

# 工作步驟
## Cache
1. Key 命名：`<domain>:<id>`，例如 `product:1`、`inventory:1`。
2. Value 是 DTO（record），不是 entity。
3. 設 TTL；變化頻繁 → 短 TTL（30s 以下），少變 → 5 分鐘以上。
4. 寫策略：**先寫 DB，後 evict cache**（不要更新 cache）。
5. miss 後回填要在 service 層；不要在 controller。

## Lock
1. Key 命名：`lock:<resource>:<id>`，例如 `lock:inventory:1`。
2. 用 `runWithLock(key, callable)`；token + Lua unlock。
3. 鎖內邏輯越短越好；不可呼叫 RPC / 發郵件 / 寫日誌大量 IO。
4. 搶不到鎖 → 丟 `LockAcquisitionFailedException`。

# 品質標準
- 寫測試覆蓋 cache hit/miss、lock 成功/搶不到。
- TTL 寫在 `application.yml` `cache.*-ttl-seconds`，不寫死在程式。
- 序列化失敗（JSON 型別不符）要當 cache miss 處理，不要拋例外回應給 client。

# 禁止事項
- 不要用 `KEYS *` 在 production 路徑掃描。
- 不要用同一 key 既當 cache 又當 lock。
- 不要在沒設 TTL 的情況下 `SET` value（避免無限累積）。

# 輸出格式
- 變更的 Java 檔
- 更新 `docs/cache-design.md`
