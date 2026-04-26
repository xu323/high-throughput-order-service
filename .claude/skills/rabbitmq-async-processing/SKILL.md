---
name: rabbitmq-async-processing
description: 新增 RabbitMQ 事件 / consumer 時，套用 topology + retry + DLQ + 冪等性原則。
---

# 何時使用
- 新增異步事件型別
- 新增 consumer
- 修改 RabbitMQ topology

# 工作步驟
1. 在 `RabbitMQConfig` 加：
   - 新 routing key 常數
   - 新 `Queue`（用 `buildQueue(name)` 預設掛 DLX）
   - 新 `Binding`（exchange ↔ queue）
   - 新 `Queue` for DLQ + DLX binding
2. 在 `OrderEventPublisher` 加 publish 方法。
3. 在 `OrderEventConsumer` 加 `@RabbitListener` 方法。
4. payload 用 `OrderEventPayload`，不要新建一份。
5. consumer 必須**冪等**：以 `(orderId, eventType)` 或外部 idempotency key 去重。
6. 對外呼叫（寄信、推播）放 `@Async("asyncTaskExecutor")`。
7. 寫 Mockito test 驗 publisher 用了正確 routing key（參考 `RabbitMQEventPublisherTest`）。
8. 更新 `docs/messaging-design.md`。

# 品質標準
- retry / DLQ 必須有；不要把 listener 退回 `auto` 不掛 DLX。
- consumer 處理時間應 < 200ms；長任務丟 ThreadPool。
- 訊息結構變更要保持向後相容（用 `attributes` Map 加欄位）。

# 禁止事項
- 不要在 publisher 等待 consumer 完成（不要做同步）。
- 不要在 consumer 內呼叫 publisher 形成事件循環。
- 不要把整個 entity 塞 payload；只放 id + 必要欄位。

# 輸出格式
- 變更的 Java 檔
- 對應 publisher 測試
- docs 更新
