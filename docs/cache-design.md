# Cache 設計

> 實作：[ProductCacheService.java](../src/main/java/com/xu/orderservice/cache/ProductCacheService.java)
> Bean：[RedisConfig.java](../src/main/java/com/xu/orderservice/config/RedisConfig.java)

## 0. 為什麼要 cache

| 操作 | 不快取 | 加上 cache |
|---|---|---|
| 商品讀取 | 每次都打 DB | 命中 cache → 微秒級回應 |
| 庫存查詢 | 高頻時容易壓垮 DB | cache 即可 |

對「**多讀少寫**」的資料用 cache，CPU、DB 都會放鬆。

## 1. 模式：Cache-Aside

```
讀取                              寫入
----                              ----
GET cache                         UPDATE DB
  hit ─► 回傳                       │
  miss                              ▼
   │                              evict cache
   ▼                              （不要更新 cache，避免併發競態）
QUERY DB
   │
   ▼
PUT cache（含 TTL）
   │
   ▼
回傳
```

## 2. Key 設計

| key | 對應 |
|---|---|
| `product:{id}` | `ProductDto`（TTL 300s） |
| `inventory:{productId}` | `InventoryDto`（TTL 30s）|

- **TTL 不同**：商品變動少 → 5 分鐘；庫存變動多 → 30 秒（限制 cache 與真實值差距）
- **不存 entity**：DTO 是 record，序列化穩定；entity 包 lazy proxy 容易爆炸

## 3. 序列化

`RedisConfig` 用 `GenericJackson2JsonRedisSerializer`：
- key：`StringRedisSerializer`
- value：JSON（含型別資訊）
- 啟用 `JavaTimeModule` 才能存 `LocalDateTime`

## 4. 寫策略：先 DB 後 evict

```java
@Transactional
public void deduct(...) {
    int n = inventoryRepo.conditionalDeduct(productId, qty);  // 1. DB
    ...
    cache.evictInventory(productId);                          // 2. 失效
}
```

不直接「寫入新值到 cache」的原因：
- 多個寫入同時發生時，誰最後寫入 cache 不可控
- evict 後下次讀觸發 miss，會把當下 DB 真實值回填，**永遠正確**

## 5. 一致性風險

| 風險 | 說明 | 緩解 |
|---|---|---|
| 短暫不一致 | DB 寫成功但 evict 失敗（網路） | TTL 兜底；重要場景可補一次 evict |
| Cache stampede（雪崩） | 大量同 key 同時 miss → DB 被打爆 | 加 lock；或對 miss 做 single-flight |
| 髒讀 | 先讀到舊 cache、又寫入舊值覆蓋 DB | 寫入路徑不要從 cache 讀，永遠從 DB 讀 |

本專案規模小，未實作雪崩防護；流量上來時建議：
- 對熱 key 加 jitter TTL（避免同時失效）
- single-flight：同一 key 同時 miss 時只放一個進去 DB，其他等結果

## 6. 監控建議
- Redis：`MEMORY USAGE`, `INFO stats` 中的 `keyspace_hits / misses`
- App：把 cache hit/miss 紀錄成 Micrometer Counter

## 7. 怎麼觀察 cache（Windows / PowerShell）

```powershell
docker compose exec redis redis-cli
> KEYS *
> GET product:1
> TTL product:1
> DEL product:1     # 手動讓 cache 失效，下次查就回 miss
```
