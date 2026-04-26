---
name: java-unit-testing
description: 撰寫 / 修改 Java 單元測試或整合測試時，套用 JUnit 5 + Mockito + Slice test 慣例。
---

# 何時使用
- 新增測試
- 既有測試 fail 時診斷
- 加 controller / service 後補測試

# 工作步驟
1. 命名：`{Class}Test`（unit）、`{Flow}IntegrationTest`（整合，會被 failsafe 執行）。
2. 用 `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`，**不要用 `@SpringBootTest`** 做純單元測試。
3. Controller test 用 `@WebMvcTest(XxxController.class)` + `@MockBean`。
4. Repository test 用 `@DataJpaTest` + `@ActiveProfiles("test")`，跑 H2。
5. Integration test 用 `@SpringBootTest` + `@ActiveProfiles("test")` + Mock RabbitMQ/Redis（或 Testcontainers）。
6. 斷言用 `AssertJ`（`assertThat`），不要混 hamcrest。
7. 一個測試斷言一件事；用 `given/when/then` 結構命名。
8. 別 mock 你正在測的類別本身。

# 品質標準
- `mvn test` 在 60 秒內跑完。
- `mvn verify` 在 5 分鐘內跑完。
- 測試名稱描述行為，不是描述方法（`create_throws_when_stock_insufficient` 而非 `testCreate1`）。

# 禁止事項
- 不要 `Thread.sleep()` 等異步；改用 `Awaitility`。
- 不要在 unit test 啟動 Spring context。
- 不要在測試中對外連線（DB / Redis / RabbitMQ）；除非 Testcontainers + 標 `@Tag("docker")`。

# 輸出格式
- 完整測試類別
