# 研究筆記（技術選型 / 取捨）

> 為了讓未來的自己（或下一個工程師）理解「為什麼這樣選」，把研究過的選項記下來。

## 1. JDK 版本

- **選 21**：最新 LTS（2023.09 釋出，至少維護到 2028），支援 virtual thread、pattern matching、record。
- 17 也 OK，但既然做新專案，直接挑長期維護版本減少之後升級成本。

## 2. 建置工具：Maven vs Gradle

| 面向 | Maven | Gradle |
|---|---|---|
| 學習曲線 | 簡單（XML） | 中等（Groovy/Kotlin DSL） |
| Spring Boot 模板 | 預設 | 也支援 |
| CI 範例多寡 | 較多 | 較多 |
| 客製化彈性 | 普通 | 高 |
| 對初學者友好 | ✅ | ⚠️ |

**選 Maven**：對初學者更友善，且 CI 範例最齊。

## 3. ORM：Spring Data JPA vs MyBatis

- **JPA**：Repository 寫法簡單、自動 SQL；缺點是 N+1 / 複雜查詢需要技巧。
- **MyBatis**：手寫 SQL，效能可控、但樣板程式多。

本專案：**主用 JPA + 局部 native query**（庫存的條件式 UPDATE）。
原因：訂單流程的查詢都不複雜；熱路徑的 SQL 用 native 寫，比 JPQL 更可預期。

## 4. 防超賣：四種候選

| 方案 | 優點 | 缺點 |
|---|---|---|
| 1. 樂觀鎖（@Version） | 無鎖、吞吐高 | 衝突率高時重試成本高 |
| 2. 悲觀鎖（SELECT FOR UPDATE） | 簡單直觀 | 行鎖會阻塞、長交易降低吞吐 |
| 3. 條件式 UPDATE（available >= q） | DB 層原子性、最常見 | 需要重試或回滾包裝 |
| 4. Redis 分散式鎖 | 跨服務、串行化臨界區 | 需要鎖續約、單點 Redis 風險 |

本專案：**1 + 3 合併**（OPTIMISTIC）；額外提供 **4**（REDIS_LOCK）作為對照。

實務建議：**極熱商品** → 進 Redis 預扣庫存（in-memory 計數）+ 異步落 DB；
**一般商品** → 用條件式 UPDATE + 樂觀鎖即可。

## 5. 訊息中介：RabbitMQ vs Kafka

| 面向 | RabbitMQ | Kafka |
|---|---|---|
| 模型 | 訊息 broker（push、ack） | 分散式 log（pull） |
| 訂單通知這類少量事件 | ✅ 適合 | 過度設計 |
| 高吞吐串流 / event sourcing | ⚠️ 不擅長 | ✅ 適合 |
| 操作熟悉度 | 高 | 中 |
| 管理頁 | 內建 management plugin | 需另外裝（Kafka UI） |

選 **RabbitMQ**：訂單 / 庫存事件量級不大、需要 routing 與 DLQ，原生支援更直接。

## 6. Cache 策略

- **Cache-Aside**（用本專案）：應用層自己讀寫 cache，最常見、最好理解。
- **Read-Through / Write-Through**：cache 與 DB 整合在 cache provider；複雜度較高。
- **Write-Behind**：先寫 cache，異步 flush DB；資料一致性風險高。

選 **Cache-Aside**。為避免「先刪 cache 再寫 DB」之間的競態，建議「**先寫 DB，後刪 cache**」。

## 7. 測試策略

- **Unit test**：純 Mockito。最快、最穩。
- **Slice test**：`@WebMvcTest` 測 Controller、`@DataJpaTest` 測 Repository。
- **Integration test**：`@SpringBootTest` + H2 + MockBean RabbitMQ/Redis（不依賴 Docker）。
- **Testcontainers**（已加 dependency 但目前不啟用）：適合「真的 MySQL/Redis/RabbitMQ」端對端，但 CI 啟動時間長。

CI 預設跑前三類，能在 GitHub Actions 上保持 < 5 分鐘。

## 8. CI/CD

- **GitHub Actions**：免費 / 與 PR 綁定 / 上手最快。
- 拒絕引入 Sonar / SAST 等重型工具，避免初學者卡關；可後續加上。

## 9. 程式風格

- 目前**不**強制 Checkstyle / Spotless（避免初學者第一次 push 就被擋）。
- 建議未來導入 **Spotless + google-java-format**：低門檻、高一致性。

## 10. 為什麼把 cache、lock 放成獨立 package？

- 隔離「跨領域」邏輯：service 不該關心怎麼解鎖、怎麼序列化 cache。
- 之後若要換 Redisson / Caffeine，只改一個檔案。

## 11. 已知未做的部分（候選 backlog）

- Outbox Pattern（事件可靠性）
- ShedLock（多 instance 排程冪等）
- Resilience4j（外部呼叫的 circuit breaker / retry）
- 監控（Micrometer / Prometheus）
- 認證（Spring Security + JWT）
