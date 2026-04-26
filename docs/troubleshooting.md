# 常見錯誤排除

## A. Docker / Compose

| 症狀 | 原因 / 解法 |
|---|---|
| `docker version` 報錯 | Docker Desktop 沒開。打開 → 等 whale 圖示穩定 → 再試 |
| `docker compose up` 永遠 unhealthy | 看 `docker compose logs <service>`；常見是 port 被佔（見下） |
| `bind: address already in use` | 本機已有同 port 的服務（MySQL/Redis/RabbitMQ）。改 `.env` 中 `*_PORT`，例如 `MYSQL_PORT=3307` |
| `image pull rate limit` | 切換網路 / 換 mirror，或本機 docker login |
| Compose 起來但 app 一直 restart | `docker compose logs app`，多半是 DB 連不上；確認 mysql 已 healthy |

## B. Spring Boot 啟動錯誤

| 症狀 | 原因 / 解法 |
|---|---|
| `Communications link failure` | MySQL 未 ready 或主機/port 錯誤；等 30 秒再試或檢查 `.env` |
| `Unknown database 'orderdb'` | 第一次啟動時 `MYSQL_DATABASE` 未生效；`docker compose down -v` 後再 up |
| `Caused by: org.flywaydb.core.api.FlywayException` | 改了已執行過的 migration；新增 `V3__xxx.sql` 不要動歷史檔 |
| `Failed to start bean 'rabbitListenerEndpointRegistry'` | RabbitMQ 沒起 / 認證錯；檢查 `SPRING_RABBITMQ_*` |
| `LettuceConnectionFailureException` | Redis 沒起或 host 錯；檢查 `SPRING_DATA_REDIS_*` |

## C. 編譯 / 測試

| 症狀 | 原因 / 解法 |
|---|---|
| `mvn` 找不到 | 沒裝 Maven，或 PATH 錯。直接用 `docker compose` 即可 |
| `release version 21 not supported` | JDK 不是 21；安裝 Adoptium Temurin 21 |
| `lombok cannot find symbol` | IDE 沒裝 Lombok 外掛（IntelliJ 有內建支援；確認 Annotation Processing 開啟） |
| `MapStruct generated classes not found` | `mvn -U clean compile` 重新 generate；annotationProcessorPaths 已在 pom 設定 |
| 測試紅字：`OptimisticLockingFailureException` | 預期內，會自動重試；若一直失敗，檢查 `MAX_RETRIES` 是否合理 |

## D. 業務錯誤

| 症狀 / API 回應 | 原因 / 解法 |
|---|---|
| `INSUFFICIENT_STOCK` | 庫存真的不夠 → `POST /api/demo/reset` + `seed` |
| `INVALID_ORDER_STATUS` | 對 PAID 訂單 cancel、或對 CANCELLED 訂單 pay |
| `LOCK_ACQUISITION_FAILED` | 樂觀鎖重試 5 次失敗 / Redis 鎖等待 200ms 還沒拿到。降低併發、或調 `lock.redis.wait-millis` |
| `VALIDATION_FAILED` | request body 缺欄位 / 型別錯。看 message 找哪個欄位 |
| `INTERNAL_ERROR` | 伺服器例外。看 app log 抓 stack trace |

## E. RabbitMQ 管理頁

| 症狀 | 解法 |
|---|---|
| 開不起 `localhost:15672` | 確認 `hto-rabbitmq` 是 healthy；`docker compose ps` |
| 帳號 guest 拒絕登入 | 已被 RabbitMQ 視為 remote。本專案在 docker-compose 內部 access，guest 在 `localhost` 視為本地 ✅ |
| Queue 訊息一直累積 | consumer 出錯了。看 app log，或進 DLQ 確認被丟死信 |

## F. Redis

| 症狀 | 解法 |
|---|---|
| `KEYS *` 在 production 不要用 | 會掃整個庫；改用 `SCAN` |
| `cache 永遠 miss` | 檢查 RedisConfig 是否覆寫 RedisTemplate；型別不符會反序列化失敗 |

## G. GitHub push

| 症狀 | 解法 |
|---|---|
| `Permission denied (publickey)` | 用 HTTPS 而非 SSH；或設定 SSH key |
| `403 Repository not found` | 用 PAT 替換密碼；或 `git remote set-url` |
| 大檔被擋 | `.gitignore` 已排除 target/、mysql-data/；別把 build artifact commit 進去 |

## H. CI 失敗

| 症狀 | 解法 |
|---|---|
| `setup-java` 抓不到 | 確認 distribution: temurin、java-version: '21' |
| 測試在 CI 慢 / 卡死 | 整合測試的併發測試在 H2 上會慢；可在 CI 用 `-DexcludedGroups=...` 跳過 |
| `Docker build` 失敗 | 看完整 log；通常是 dependency:go-offline 抓不到，重新跑 |
