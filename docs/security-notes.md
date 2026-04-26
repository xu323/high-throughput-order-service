# 安全性與程式品質筆記

## 1. 沒有寫死的 secret

- **DB / Redis / RabbitMQ 認證** 全部走 `application*.yml` + 環境變數，預設值僅供 docker compose 本機使用。
- `.env.example` 提供樣板，請把真值放在 `.env`（已被 `.gitignore` 排除）。
- Production 請改用：
  - GitHub Actions Secrets / Repository Variables
  - K8s Secret / Vault / AWS SSM Parameter Store

## 2. SQL injection

- 所有 query 走 Spring Data JPA + named/`@Param` 綁定，**沒有任何字串拼接 SQL**。
- 範例：[ProductInventoryRepository.java](../src/main/java/com/xu/orderservice/repository/ProductInventoryRepository.java) `:productId` 是 PreparedStatement 參數綁定。

## 3. Bean Validation

- Request DTO 用 `@NotNull / @NotBlank / @Size / @Min / @DecimalMin` 等驗證。
- Controller 加 `@Valid`，驗證失敗自動 → `MethodArgumentNotValidException` → `GlobalExceptionHandler` 轉 `VALIDATION_FAILED` 400。

## 4. 全域 exception handler

[GlobalExceptionHandler.java](../src/main/java/com/xu/orderservice/exception/GlobalExceptionHandler.java) 攔截：

| Exception | HTTP | code |
|---|---|---|
| NotFoundException | 404 | NOT_FOUND |
| InsufficientStockException | 409 | INSUFFICIENT_STOCK |
| InvalidOrderStatusException | 409 | INVALID_ORDER_STATUS |
| LockAcquisitionFailedException | 409 | LOCK_ACQUISITION_FAILED |
| BusinessException | 400 | （依實例的 code） |
| MethodArgumentNotValidException | 400 | VALIDATION_FAILED |
| IllegalArgumentException | 400 | VALIDATION_FAILED |
| IllegalStateException | 409 | CONFLICT |
| DataIntegrityViolationException | 409 | CONFLICT |
| 其他 | 500 | INTERNAL_ERROR（log full stack） |

## 5. CORS / CSRF

- 目前**不啟用 Spring Security**，免認證的 demo 環境。
- production 必加：
  - Spring Security + JWT / OAuth2
  - CORS allowed origins 白名單
  - CSRF（如果是 cookie session）

## 6. PII / 個資

- 範例 `users` 表只有 `username / email`；不收密碼 / 信用卡 / 身分證。
- log 不要直接印整個 request；對 email 等欄位 log 時做 masking。

## 7. 連線池

- HikariCP `maximum-pool-size: 30`：根據 DB 容量調整。
- Lettuce `max-active: 50`：cluster / 高併發時調大。

## 8. 程式品質建議

- 命名一致：`*Service / *Controller / *Repository / *Dto / *Mapper / *Config`。
- 不在 controller 寫業務邏輯，全部下放到 service。
- 例外丟業務例外，不要丟 RuntimeException 字串。
- DTO 用 `record`，避免 entity 外洩 lazy proxy。

## 9. Spotless / Checkstyle（可選）

未強制納入，避免新手第一次 commit 就被擋。如果要加：

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>2.43.0</version>
  <configuration>
    <java>
      <googleJavaFormat />
      <removeUnusedImports />
    </java>
  </configuration>
</plugin>
```
然後在 CI 加 `mvn spotless:check`。

## 10. 已知不足（接受 trade-off）

- 沒有認證 → demo 用途
- 沒有 rate limit → 線上請加 nginx / API gateway
- Outbox pattern 未做 → 事件可能遺失（極端情況）
- Cache 雪崩防護未做 → 流量未到該等級
