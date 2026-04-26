# high-throughput-order-service

> 高傳輸量訂單處理系統：模擬電商 / 票券 / 遊戲商城在大量併發下單時，
> 如何穩定處理 **訂單建立 + 庫存扣減 + 防超賣 + 非同步事件 + 快取 + 排程取消**。

技術棧：**Java 21 · Spring Boot 3.3 · MySQL 8 · Redis 7 · RabbitMQ 3.13 · Flyway · MapStruct · springdoc-openapi · Docker Compose · GitHub Actions**

---

## 目錄

- [專案介紹](#專案介紹)
- [系統架構](#系統架構)
- [核心流程](#核心流程)
- [API 範例](#api-範例)
- [快速啟動](#快速啟動windows-11--powershell)
- [測試方式](#測試方式)
- [CI/CD 說明](#cicd-說明)
- [技術設計重點](#技術設計重點)
- [文件導覽](#文件導覽)
- [未來擴充](#未來擴充)

---

## 專案介紹

本專案是一個「**完整工程結構**」的 Java 後端系統，不是 CRUD demo。重點放在：

1. **高併發下單**：以兩種策略（MySQL 樂觀鎖 / Redis 分散式鎖）防超賣，提供 API 切換並做併發壓測。
2. **非同步事件**：訂單流程透過 RabbitMQ 解耦，consumer 負責通知、稽核紀錄；支援 retry 與 DLQ。
3. **快取一致性**：Cache-Aside Pattern，對熱資料（商品、庫存）使用 Redis 快取。
4. **訂單生命週期**：完整狀態機 + 排程逾時取消。
5. **可觀察性**：Actuator、Swagger、結構化錯誤碼與全域 exception handler。
6. **可測試**：JUnit 5 / Mockito / `@WebMvcTest` / `@DataJpaTest` / `@SpringBootTest`，CI 上不需 Docker 也能跑。
7. **可運維**：Dockerfile 多階段建置、docker-compose 一鍵起、GitHub Actions CI。

---

## 系統架構

```
┌──────────────────────────────────────────────────────────────────────┐
│                          Client / Swagger UI                         │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ HTTP / JSON
┌──────────────────────────────▼───────────────────────────────────────┐
│                     Spring Boot Application                          │
│  ┌────────────┐  ┌────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ Controller │→ │  Service   │→ │ Repository   │→ │   MySQL 8    │ │
│  │  (REST)    │  │ (business) │  │  (JPA)       │  │  (orders…)   │ │
│  └────────────┘  └─────┬──────┘  └──────────────┘  └──────────────┘ │
│                        │                                             │
│   ┌────────────────────┼─────────────────────┐                       │
│   │                    │                     │                       │
│   ▼                    ▼                     ▼                       │
│ Redis Cache       Redis Lock           RabbitMQ Publisher            │
│ (product /        (lock:inventory:*)   (order.exchange)              │
│  inventory)                                                          │
│                                                                      │
│                         ▲                     │ consumer             │
│                         │                     ▼                      │
│                  ThreadPool ←──── @RabbitListener (auto retry + DLQ) │
│                  (asyncTaskExecutor)                                 │
└──────────────────────────────────────────────────────────────────────┘
```

完整架構圖與時序圖請見 [docs/architecture.md](docs/architecture.md)。

---

## 核心流程

```
建立訂單                付款                取消 / 逾時
─────────              ─────              ────────────
CREATED  ─────PAID────►  COMPLETED         CREATED ──cancel──► CANCELLED
   │                                       CREATED ──timeout──► EXPIRED
   ▼
扣庫存：依 lockStrategy 走
  - OPTIMISTIC：UPDATE … WHERE available_stock >= q（+ JPA @Version）
  - REDIS_LOCK：SET key NX PX → SELECT FOR UPDATE → 寫回
   │
   ▼
RabbitMQ 事件：order.created / inventory.deducted
              order.paid / order.cancelled
   │
   ▼
Consumer：寫入 order_events、模擬通知
```

---

## API 範例

> Base URL：`http://localhost:8080`，回應一律用 `ApiResponse` 包裝（`success / code / message / data / timestamp`）。

```powershell
# 1) seed demo data
Invoke-RestMethod -Method POST 'http://localhost:8080/api/demo/seed'

# 2) 列商品
Invoke-RestMethod 'http://localhost:8080/api/products'

# 3) 建立訂單（樂觀鎖策略）
$body = @{
  userId = 1
  items  = @(@{ productId = 1; quantity = 1 })
  lockStrategy = 'OPTIMISTIC'
} | ConvertTo-Json
Invoke-RestMethod -Method POST 'http://localhost:8080/api/orders' -ContentType 'application/json' -Body $body

# 4) 付款
Invoke-RestMethod -Method POST 'http://localhost:8080/api/orders/1/pay'

# 5) 查事件
Invoke-RestMethod 'http://localhost:8080/api/orders/1/events'

# 6) 併發測試（會起 N 條執行緒同時下單）
$body = @{ productId = 1; threads = 16; ordersPerThread = 10; quantityPerOrder = 1; lockStrategy = 'OPTIMISTIC' } | ConvertTo-Json
Invoke-RestMethod -Method POST 'http://localhost:8080/api/demo/concurrency-test' -ContentType 'application/json' -Body $body
```

完整 API 規格請見 [docs/api.md](docs/api.md) 或啟動後查看 Swagger UI。

---

## 快速啟動（Windows 11 + PowerShell）

```powershell
# 1) 確認 Docker Desktop 已啟動
docker version

# 2) 一鍵啟動（會建立 .env、build app image、起 mysql/redis/rabbitmq/app）
.\scripts\init-dev.ps1

# 3) 開瀏覽器
start http://localhost:8080/swagger-ui/index.html
start http://localhost:15672  # RabbitMQ 管理頁，guest / guest
```

如果你還沒有 Java/Maven，但只是想跑起來：**用上面的 docker compose 就夠了**（Dockerfile 內含 Maven 21 multi-stage build）。

如果你想直接跑 Spring Boot：請先安裝 [JDK 21](https://adoptium.net/) 與 [Maven 3.9+](https://maven.apache.org/download.cgi)，然後：

```powershell
# 啟動 mysql / redis / rabbitmq 但不啟動 app
docker compose up -d mysql redis rabbitmq

# 本機跑 app
mvn spring-boot:run
```

更詳細的操作請參考 [使用方法.md](使用方法.md)。

---

## 測試方式

| 測試類型 | 指令 | 說明 |
|---|---|---|
| 單元測試 | `mvn test` | 純 Mockito，不需要 Docker |
| 整合測試 | `mvn verify` | 用 H2 + MockBean 模擬 Redis/RabbitMQ，不需要 Docker |
| 手動 smoke | `.\scripts\smoke-test.ps1` | 對「跑起來的 app」做 seed → 下單 → 付款 → 查事件 |
| 併發壓測 | `POST /api/demo/concurrency-test` | 在線上自我測試防超賣 |

---

## CI/CD 說明

`.github/workflows/ci.yml` 有兩個 job：

### `build-test`（每次 push / PR）
1. checkout → setup JDK 21 (Temurin) → maven cache
2. `mvn compile`
3. `mvn test`（surefire）
4. `mvn verify`（failsafe，含 IntegrationTest）
5. `mvn package`
6. `docker build` smoke check
7. 檢查必要文件是否存在
8. 上傳 jar artifact

### `publish-image`（只在 push main 時跑）
- Build & push image 到 **GitHub Container Registry**：
  - `ghcr.io/xu323/high-throughput-order-service:latest`
  - `ghcr.io/xu323/high-throughput-order-service:sha-<short>`
- 用 `GITHUB_TOKEN`，不需額外 secret

**任何有 docker 的機器都能直接跑：**

```powershell
docker pull ghcr.io/xu323/high-throughput-order-service:latest
docker run --rm -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=dev `
  ghcr.io/xu323/high-throughput-order-service:latest
```

> 第一次 push 完，image 預設是 **private**。要公開讓別人也能 pull：
> GitHub → 你的頭像 → Packages → `high-throughput-order-service` → Package settings → 下方 *Change visibility* → Public。

---

## 技術設計重點

| 主題 | 連結 |
|---|---|
| 防超賣（樂觀鎖 vs Redis Lock） | [docs/concurrency-design.md](docs/concurrency-design.md) |
| 資料庫設計與索引 | [docs/database-design.md](docs/database-design.md) |
| Cache 一致性 | [docs/cache-design.md](docs/cache-design.md) |
| RabbitMQ topology + retry/DLQ | [docs/messaging-design.md](docs/messaging-design.md) |
| 安全性 / 例外處理 | [docs/security-notes.md](docs/security-notes.md) |
| 架構決策 / 研究筆記 | [docs/research-notes.md](docs/research-notes.md) |

---

## 文件導覽

- [使用方法.md](使用方法.md) — 給初學者的完整操作手冊
- [docs/project-guide.md](docs/project-guide.md) — 專案結構導覽
- [docs/architecture.md](docs/architecture.md) — 系統架構與時序圖
- [docs/api.md](docs/api.md) — REST API 規格
- [docs/troubleshooting.md](docs/troubleshooting.md) — 常見錯誤排除
- [docs/research-notes.md](docs/research-notes.md) — 技術選型研究

---

## 未來擴充

- [ ] 替換成 Redisson + RedLock，跨機房高可用鎖
- [ ] Outbox Pattern：訂單寫入與事件發送在同一交易（避免事件遺失）
- [ ] 訂單分庫分表（ShardingSphere）
- [ ] Prometheus + Grafana 監控、p99 latency / 壓測報告
- [ ] OAuth2 / JWT 認證
- [ ] Saga 分散式交易（跨支付服務）
