# 專案結構導覽

> 第一次接手這個專案時看這份就夠了。

## 1. 目錄樹

```
high-throughput-order-service/
├─ README.md                           專案門面
├─ 使用方法.md                          完全新手版操作手冊
├─ docker-compose.yml                  一鍵起 mysql/redis/rabbitmq/app
├─ Dockerfile                          多階段建置
├─ .env.example                        環境變數樣板
├─ .gitignore
├─ pom.xml                             Maven + JDK 21 + Spring Boot 3.3
├─ .github/
│   └─ workflows/ci.yml                CI：build → test → docker → docs check
├─ .claude/
│   ├─ agents/                         （未使用，預留）
│   ├─ skills/                         本專案專用 Claude Code skills
│   └─ hooks/                          （未使用，預留）
├─ docs/                               全部技術文件
├─ scripts/
│   ├─ init-dev.ps1                    一鍵啟動
│   └─ smoke-test.ps1                  跑一輪基本流程驗證
└─ src/
    ├─ main/
    │   ├─ java/com/xu/orderservice/
    │   │   ├─ OrderServiceApplication.java
    │   │   ├─ common/                 共用工具
    │   │   ├─ config/                 Spring Bean 設定
    │   │   ├─ controller/             REST 入口
    │   │   ├─ service/                業務邏輯
    │   │   ├─ repository/             Spring Data JPA
    │   │   ├─ entity/                 JPA Entity + Enum
    │   │   ├─ dto/                    DTO + Request/Response
    │   │   ├─ mapper/                 MapStruct
    │   │   ├─ exception/              業務例外 + 全域 handler
    │   │   ├─ event/                  RabbitMQ payload/publisher/consumer
    │   │   ├─ lock/                   Redis 分散式鎖
    │   │   ├─ cache/                  Cache-Aside service
    │   │   └─ scheduler/              逾時排程
    │   └─ resources/
    │       ├─ application.yml
    │       ├─ application-dev.yml
    │       ├─ application-test.yml
    │       └─ db/migration/           Flyway SQL
    └─ test/java/com/xu/orderservice/
        ├─ controller/                 @WebMvcTest
        ├─ event/                      Publisher mock test
        ├─ integration/                @SpringBootTest（H2 + Mock broker）
        ├─ lock/                       Redis lock unit test
        ├─ repository/                 @DataJpaTest
        └─ service/                    Service unit test (Mockito)
```

## 2. 怎麼讀程式碼（建議順序）

1. `OrderServiceApplication` — 啟動點
2. `controller/OrderController` — 對外 API
3. `service/OrderService` — 主流程：建立訂單 / 付款 / 取消
4. `service/InventoryService` — 兩種防超賣策略
5. `event/OrderEventPublisher` + `event/OrderEventConsumer` — 異步事件
6. `cache/ProductCacheService` — Cache-Aside
7. `lock/RedisLockService` — 分散式鎖
8. `scheduler/OrderTimeoutScheduler` — 排程逾時
9. `config/*` — Bean 設定
10. `db/migration/*.sql` — schema

## 3. 開發迴圈

```
編程 → mvn test → docker compose up -d → smoke-test.ps1 → commit → push → CI 綠 → 合併
```

## 4. 加新 API 的 checklist

1. `dto/` 加 Request / Response
2. `controller/` 加 endpoint，加 `@Valid`
3. `service/` 加 business method，加 `@Transactional`（若改 DB）
4. `repository/` 加 query
5. 寫 unit test（Mockito）+ controller test（`@WebMvcTest`）
6. 更新 `docs/api.md`
7. 啟動 + 在 Swagger 試一次
8. commit + push

## 5. 加新事件的 checklist

1. `RabbitMQConfig` 新增 routing key + queue + binding（含 DLQ）
2. `OrderEventPublisher` 新增 publish 方法
3. `OrderEventConsumer` 新增 `@RabbitListener` 方法
4. 在 service 適當位置 publish
5. 寫 publisher 的 unit test
6. 更新 `docs/messaging-design.md`

## 6. 加新欄位 / 表

1. 新增 `db/migration/V{n}__xxx.sql`（**不要改舊的**）
2. 對應 entity 加欄位 / 新增 entity
3. repository / service / dto / mapper 同步更新
4. 寫 `@DataJpaTest`
5. 更新 `docs/database-design.md`
