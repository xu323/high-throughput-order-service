---
name: java-spring-boot-architecture
description: 在本專案新增/重構 Java Spring Boot 模組時的對齊指南；確保 layered architecture、命名、Bean 注入方式一致。
---

# 何時使用
- 新增一個新的領域（例如 coupon、shipment）
- 重構既有模組
- 對 Spring Bean / 設定有疑問

# 工作步驟
1. 確認屬於哪一層（controller / service / repository / entity / dto / mapper / config / exception / event / lock / cache / scheduler）。
2. 命名規則：`*Controller / *Service / *Repository / *Dto / *Mapper / *Config`，**不要混用 `Manager / Helper`**。
3. Service：以建構子注入（`@RequiredArgsConstructor`），不用欄位注入。
4. Controller：不寫業務邏輯；參數加 `@Valid`。
5. 例外：丟業務例外（繼承 `BusinessException`），由 `GlobalExceptionHandler` 統一回應。
6. 對外回應一律包成 `ApiResponse<T>`。
7. 如需新增 config bean，放到 `com.xu.orderservice.config`。
8. 更新 `docs/project-guide.md` 與 `docs/api.md`（如新增了 endpoint）。

# 品質標準
- 一個 controller 方法 ≤ 20 行；超過代表業務邏輯漏到了 controller。
- service 方法保持單一職責；DB 寫入加 `@Transactional`。
- 不在 service 寫 raw HTTP 處理。

# 禁止事項
- 不要在 controller 直接呼叫 repository。
- 不要在 entity 上加業務方法（保持為純資料模型 + lifecycle hooks）。
- 不要把 entity 直接回傳給前端（永遠經過 mapper 轉 DTO）。

# 輸出格式
- 完整 Java 檔（不要 diff），含 package、imports、annotations。
