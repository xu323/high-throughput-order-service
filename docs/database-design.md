# 資料庫設計

> Migration：[src/main/resources/db/migration/V1__init_schema.sql](../src/main/resources/db/migration/V1__init_schema.sql)
> Seed：[src/main/resources/db/migration/V2__seed_data.sql](../src/main/resources/db/migration/V2__seed_data.sql)

## 0. 整體說明

- DB：MySQL 8 / InnoDB / utf8mb4
- 版本管理：Flyway，每次新增 schema 變更請新增 `V{n}__xxx.sql`，**不要動已執行過的 migration**。
- 應用啟動時 `spring.flyway.enabled=true`，會自動執行新增的 migration。

## 1. ER 圖（簡化）

```
users (1) ───< orders (N) ───< order_items (N) >─── products (1)
                  │                                       │
                  │1                                      │1
                  ▼                                       ▼
              payments (N)                       product_inventory (1)
                  │1
                  ▼
              order_events (N)        stock_deduction_logs (N) 跨資料表稽核
```

## 2. 資料表

### users
| 欄位 | 型別 | 註解 |
|---|---|---|
| id | BIGINT PK | |
| username | VARCHAR(64) UNIQUE | |
| email | VARCHAR(128) UNIQUE | |
| created_at | DATETIME | |

### products
| 欄位 | 型別 | 註解 |
|---|---|---|
| id | BIGINT PK | |
| sku | VARCHAR(64) UNIQUE | 業務識別碼 |
| name | VARCHAR(255) | |
| description | TEXT | |
| price | DECIMAL(12,2) | 用 DECIMAL 避免浮點誤差 |
| created_at / updated_at | DATETIME | |

### product_inventory
| 欄位 | 型別 | 註解 |
|---|---|---|
| product_id | BIGINT PK / FK → products | 1:1 |
| available_stock | INT | 可賣庫存 |
| reserved_stock | INT | 預留 / 待付款（保留欄位） |
| version | BIGINT | JPA `@Version`，**樂觀鎖** |
| updated_at | DATETIME | |

> 把庫存抽出成獨立表的原因：高併發 UPDATE 只打到一張行；商品本身可被讀爆而不影響庫存熱資料行。

### orders
| 欄位 | 型別 | 註解 |
|---|---|---|
| id | BIGINT PK | |
| order_no | VARCHAR(64) UNIQUE | 對外用編號 |
| user_id | BIGINT FK | |
| status | VARCHAR(32) | CREATED / PAID / COMPLETED / CANCELLED / EXPIRED |
| total_amount | DECIMAL(12,2) | |
| lock_strategy | VARCHAR(32) | 紀錄使用哪一種防超賣策略 |
| created_at / updated_at | DATETIME | |
| expires_at | DATETIME NULL | 訂單逾時時間（CREATED 時設定） |

**索引：**
- `UNIQUE(order_no)`：對外查詢
- `KEY(user_id)`：使用者訂單列表
- `KEY(status, expires_at)`：排程器掃逾時訂單時用

### order_items
| 欄位 | 型別 | 註解 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT FK ON DELETE CASCADE | |
| product_id | BIGINT FK | |
| sku, quantity, unit_price, subtotal | | |

### payments
| 欄位 | 型別 | 註解 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT FK | |
| amount | DECIMAL(12,2) | |
| status | VARCHAR(32) | PENDING / SUCCESS / FAILED |
| paid_at | DATETIME NULL | |
| created_at | DATETIME | |

### order_events
| 欄位 | 型別 | 註解 |
|---|---|---|
| id | BIGINT PK | |
| order_id | BIGINT | |
| event_type | VARCHAR(64) | ORDER_CREATED / ORDER_PAID / ... |
| payload | TEXT | JSON 字串 |
| created_at | DATETIME | |

### stock_deduction_logs
| 欄位 | 型別 | 註解 |
|---|---|---|
| id | BIGINT PK | |
| product_id, order_id, quantity | | |
| strategy | VARCHAR(32) | OPTIMISTIC / REDIS_LOCK |
| success | TINYINT(1) | 0 / 1 |
| error_msg | VARCHAR(255) | 失敗原因 |
| created_at | DATETIME | |

## 3. 交易一致性

- **建立訂單**：在 `OrderService.create` 是一個 `@Transactional`：訂單 + items + 每個 item 的庫存扣減；任一失敗整單 rollback。
- **庫存扣減**：使用 native `UPDATE … WHERE available >= q`，DB 原子性已足夠。樂觀鎖只是追加保險。
- **付款**：`PaymentService.pay` 是一個 `@Transactional`，內部呼叫另一個 bean `OrderService.markPaid`，proxy 仍生效。
- **排程取消**：每筆訂單由 `OrderService.expire(id)` 在獨立交易內處理；若一筆失敗不影響其他。

## 4. 索引設計建議

- 高併發更新欄位（`available_stock`）**避免**做為 secondary index 的一部分，否則每次 update 要更新索引頁。
- `orders(status, expires_at)` 為 scheduler 而設；資料量大時可加入 `created_at` 做覆蓋索引。
- `order_events` 寫多讀少；若資料量大可考慮 partition by date。

## 5. 為什麼用 DECIMAL 不用 DOUBLE
浮點數會出現 `0.1 + 0.2 != 0.3`；金額不允許這種誤差。
