# 系統架構

## 0. 一句話定位
Spring Boot 單體應用，圍繞「訂單建立 + 防超賣」設計；
透過 Redis（cache/lock）、RabbitMQ（async）、ThreadPool（背景任務）構成完整的高併發後端骨架。

## 1. 元件圖

```
                       ┌──────────────────────────────────┐
                       │   Client (Browser / Swagger UI)  │
                       └──────────────┬───────────────────┘
                                      │ HTTP/JSON
                       ┌──────────────▼───────────────────┐
                       │   Spring Boot Application        │
                       │                                  │
   Controller ─► Service ─► Repository (JPA) ──┐          │
       │           │                           │          │
       │           ├─► RedisLockService ───────┼──► Redis │
       │           ├─► ProductCacheService ────┘          │
       │           └─► OrderEventPublisher ──────► RabbitMQ Exchange
       │                                                  │
       │              @RabbitListener(...) ◄────────────  │
       │                  └─► consumers ─► OrderEventRepo │
       │                                                  │
       │              @Scheduled cron: */1 * * * *        │
       │                  └─► OrderTimeoutScheduler       │
       │                                                  │
       └─► Async Task Executor (ThreadPool)               │
                                                          │
                       Repository ──► MySQL 8 (InnoDB)    │
                       └──────────────────────────────────┘
```

## 2. 套件結構（Layered + Feature 混合）

```
com.xu.orderservice
├─ OrderServiceApplication      啟動點
├─ common/                      共用工具：ErrorCode、OrderNoGenerator
├─ config/                      Bean 設定：Redis / RabbitMQ / Async / OpenAPI
├─ controller/                  REST 入口
├─ service/                     業務邏輯（Product / Inventory / Order / Payment / Demo / 併發測試）
├─ repository/                  Spring Data JPA
├─ entity/                      JPA Entity + Enum
├─ dto/                         Request / Response / API 包裝
├─ mapper/                      MapStruct 自動產生 entity↔dto
├─ exception/                   業務例外 + 全域 handler
├─ event/                       RabbitMQ payload / publisher / consumer
├─ lock/                        RedisLockService
├─ cache/                       ProductCacheService
└─ scheduler/                   逾時取消排程
```

## 3. 主要時序圖

### 3.1 建立訂單（OPTIMISTIC）

```
Client → POST /api/orders
   │
   ▼
OrderController → OrderService.create
   ├─► ProductRepo.findById（每個 item）
   ├─► OrderRepo.save (Order + items)
   ├─► InventoryService.deduct
   │       └─► UPDATE product_inventory
   │           SET available = available - q
   │           WHERE product_id = ? AND available >= q
   │           ↑ 條件式 + @Version 樂觀鎖；衝突重試最多 5 次
   ├─► EventPublisher.publishInventoryDeducted (RabbitMQ)
   └─► EventPublisher.publishOrderCreated      (RabbitMQ)

Consumer (async) ─► OrderEventRepo.save (寫稽核紀錄)
```

### 3.2 付款

```
Client → POST /api/orders/{id}/pay
   ▼
OrderController → PaymentService.pay
   ├─► PaymentRepo.save (status=SUCCESS)
   └─► OrderService.markPaid → status=PAID + publishOrderPaid
```

### 3.3 取消 / 逾時

```
Client → POST /api/orders/{id}/cancel       OR        Scheduler
   ▼                                                   每分鐘掃 expires_at < now
OrderService.cancel/expire                              ▼
   ├─► o.status = CANCELLED / EXPIRED              OrderService.expire(id)
   ├─► InventoryService.restock(...)
   └─► EventPublisher.publishOrderCancelled
```

## 4. 主要設計決策

| 決策 | 為什麼 |
|---|---|
| Java 21 + Spring Boot 3.3 | 最新 LTS；`record`、virtual thread 雖未啟用但可未來導入 |
| Maven | 主流、CI 支援好、IDE 不必額外設定 |
| MySQL + Flyway | 訂單資料適合關聯式；Flyway 可追版本、易 rollback |
| JPA + 原生條件式 SQL 並存 | 一般 CRUD 用 JPA；扣庫存熱路徑用 native UPDATE 更穩 |
| 事件以 RabbitMQ 解耦 | 通知 / 稽核可獨立擴充，不阻塞下單路徑 |
| 兩種防超賣策略可切換 | 教學：方便對比；實務：不同熱度商品可選不同策略 |
| 全域 ApiResponse 包裝 | 客戶端統一錯誤處理，搭配錯誤碼便於 i18n |

## 5. 與檔案的對應

| 元件 | 檔案 |
|---|---|
| 進入點 | [OrderServiceApplication.java](../src/main/java/com/xu/orderservice/OrderServiceApplication.java) |
| ThreadPool | [config/AsyncConfig.java](../src/main/java/com/xu/orderservice/config/AsyncConfig.java) |
| RabbitMQ topology | [config/RabbitMQConfig.java](../src/main/java/com/xu/orderservice/config/RabbitMQConfig.java) |
| Redis 鎖 | [lock/RedisLockService.java](../src/main/java/com/xu/orderservice/lock/RedisLockService.java) |
| Cache | [cache/ProductCacheService.java](../src/main/java/com/xu/orderservice/cache/ProductCacheService.java) |
| 防超賣 | [service/InventoryService.java](../src/main/java/com/xu/orderservice/service/InventoryService.java) |
| 訂單流程 | [service/OrderService.java](../src/main/java/com/xu/orderservice/service/OrderService.java) |
| 排程 | [scheduler/OrderTimeoutScheduler.java](../src/main/java/com/xu/orderservice/scheduler/OrderTimeoutScheduler.java) |
| 全域例外 | [exception/GlobalExceptionHandler.java](../src/main/java/com/xu/orderservice/exception/GlobalExceptionHandler.java) |
