# 併發 / 防超賣設計

## 0. 問題

> 同一個 SKU 在 100 個使用者同時下單時，怎麼確保「**庫存不會被扣到負數**」？

直覺寫法（**錯誤**）：
```java
ProductInventory inv = inventoryRepo.findByProductId(id);
if (inv.getAvailableStock() >= qty) {           // 1. 讀
    inv.setAvailableStock(inv.getAvailableStock() - qty); // 2. 算
    inventoryRepo.save(inv);                     // 3. 寫
}
```
這是經典的 **read-modify-write 競態**：兩個交易都讀到 100、各自扣 60、結果是 40，而不是錯誤訊息。

## 1. 本專案提供兩種策略

| 策略 | 機制 | 何時用 |
|---|---|---|
| **OPTIMISTIC** | 條件式 UPDATE + JPA `@Version` | 衝突率不高；單機 / 多機都可 |
| **REDIS_LOCK** | Redis 分散式鎖（`SET NX PX` + Lua unlock）| 跨服務串行化、商品超熱、需要更可控等待 |

實作位置：[InventoryService.java](../src/main/java/com/xu/orderservice/service/InventoryService.java)

## 2. OPTIMISTIC 詳解

核心 SQL：
```sql
UPDATE product_inventory
   SET available_stock = available_stock - :qty,
       version = version + 1,
       updated_at = NOW()
 WHERE product_id = :productId
   AND available_stock >= :qty
```

- **DB 原子性**：MySQL 對單列 UPDATE 是行鎖原子的；不會有交錯。
- **`available_stock >= :qty`**：是條件，不是 Java if；庫存不足直接 0 row 受影響。
- **`@Version`**：當其他 transaction 已經改過此列，Hibernate 會在 flush 階段檢查 version；若不符會丟 `OptimisticLockingFailureException`。
- **重試**：在 `InventoryService.deductOptimistic`，最多 5 次（指數退避）。
- **失敗回應**：
  - 0 rows affected → `InsufficientStockException`
  - 重試耗盡 → `LockAcquisitionFailedException`

## 3. REDIS_LOCK 詳解

實作：[RedisLockService.java](../src/main/java/com/xu/orderservice/lock/RedisLockService.java)

```java
String token = UUID.randomUUID().toString();
redis.opsForValue().setIfAbsent(key, token, leaseMillis);   // SET NX PX
// ... do work ...
redis.execute(LUA_UNLOCK, key, token);                      // 比對後刪除
```

關鍵：
- **NX**：只有 key 不存在時才設成功 → 互斥
- **PX leaseMillis**：自動過期，避免拿到鎖的 client 崩潰後永久卡住
- **Lua unlock**：必須「**比對 token 後再刪**」，否則可能誤刪別人剛拿到的鎖
- **wait + retry**：`tryLock` 會在 `waitMillis` 內每 10ms 重試一次，搶不到回 null

`InventoryService.deductWithRedisLock` 拿到鎖後：
1. `findForUpdate(...)` 取悲觀寫入鎖（避免同 instance 多執行緒同時操作；其實已被 Redis 鎖串行化，但保險）
2. 檢查庫存
3. `save` 扣減
4. 寫稽核紀錄

## 4. 兩種策略比較

| 面向 | OPTIMISTIC | REDIS_LOCK |
|---|---|---|
| 寫入吞吐 | ⭐⭐⭐ 高 | ⭐⭐ 中（鎖串行化） |
| 衝突率高時 | ❌ 重試成本爆增 | ✅ 大家排隊，順序穩定 |
| 跨服務 / 跨機房 | ❌（DB 鎖只在 DB 內）| ✅ 只要 Redis 是同一個就 OK |
| Redis 掛了會怎樣 | ✅ 不影響 | ❌ 必須降級或失敗 |
| 實作複雜度 | 低 | 中（要處理 token、續約） |
| 適用商品熱度 | 一般 | 極熱（秒殺） |
| 強一致性 | DB 等級 | Redis 鎖區段內等級 |

## 5. 高併發更進階解法（本專案沒做，列為 backlog）

1. **Redis 預扣庫存（in-memory 計數）+ 異步落 DB**
   - 啟動時把庫存灌到 Redis
   - 下單先 `DECRBY`；< 0 表示沒了，立即拒絕
   - 異步任務把 Redis 的扣減量持久化回 DB
   - 適合「百萬人秒殺」場景；但對帳邏輯複雜

2. **Token Bucket 限流 + 排隊**
   - 在 controller 前面用 Redis 限流；超過上限直接 429
   - 配合 message queue 異步處理下單

3. **熱點分桶**
   - 把熱門商品庫存切成 N 桶（例如 inventory:1:bucket0…bucket9）
   - 下單隨機選一桶扣，分散熱資料行
   - 每桶用 1 / 2 / 3 的策略

## 6. 怎麼驗證沒有超賣

- **自動測試**：[InventoryConcurrencyIntegrationTest.java](../src/test/java/com/xu/orderservice/integration/InventoryConcurrencyIntegrationTest.java) 用 16 條執行緒打 H2，斷言「成功筆數 + 剩餘 = 初始」。
- **線上測試**：`POST /api/demo/concurrency-test`，看回應的 `overSold` 欄位。
- **手動驗證**：
  ```sql
  -- 應為 0 或正數
  SELECT product_id, available_stock FROM product_inventory WHERE available_stock < 0;
  ```

## 7. 為什麼不用 SELECT FOR UPDATE 包整段邏輯？

可以做，但缺點是：
- **長交易**：包含應用程式邏輯（log、事件發送）期間都鎖著行
- **連線池消耗**：等鎖的執行緒佔著 DB 連線
- **死鎖機率**：跨多 product 時容易死鎖

所以本專案的 OPTIMISTIC 走「**短交易 + 條件式 UPDATE + retry**」的常見折衷做法。
