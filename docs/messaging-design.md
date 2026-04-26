# Messaging 設計（RabbitMQ）

> 實作：
> - Topology：[RabbitMQConfig.java](../src/main/java/com/xu/orderservice/config/RabbitMQConfig.java)
> - Publisher：[OrderEventPublisher.java](../src/main/java/com/xu/orderservice/event/OrderEventPublisher.java)
> - Consumer：[OrderEventConsumer.java](../src/main/java/com/xu/orderservice/event/OrderEventConsumer.java)

## 0. 為什麼用訊息佇列

下單時要做的事：
1. 寫 DB
2. 扣庫存
3. **寄通知 / 寫稽核 / 推送 / 更新搜尋索引 / ...**

第 3 類「**事後副作用**」如果同步做：
- 任一服務慢就拖慢下單
- 任一服務掛就導致下單失敗

把它們**異步化**到 message queue：主流程只負責 publish；後續由 consumer 慢慢處理。

## 1. Topology

```
                                       order.created.queue ─► OrderEventConsumer.onOrderCreated
                                       order.paid.queue    ─► OrderEventConsumer.onOrderPaid
order.exchange (topic) ──┬──► routing  order.cancelled.queue ─► OrderEventConsumer.onOrderCancelled
                         │             inventory.deducted.queue ─► OrderEventConsumer.onInventoryDeducted
                         │
                         └─ 每個 queue 都掛：
                             x-dead-letter-exchange   = order.dlx
                             x-dead-letter-routing-key = <queue>.dlq

order.dlx (topic) ──► <queue>.dlq  （死信佇列；失敗重試耗盡的訊息會落到這裡）
```

| 名稱 | 值 |
|---|---|
| Exchange | `order.exchange`（topic）|
| DLX | `order.dlx`（topic） |
| Queues | `order.created.queue`、`order.paid.queue`、`order.cancelled.queue`、`inventory.deducted.queue` |
| Routing keys | `order.created`、`order.paid`、`order.cancelled`、`inventory.deducted` |
| DLQ | `<queue>.dlq` |

## 2. Payload

```java
public record OrderEventPayload(
    String eventType,
    Long orderId,
    String orderNo,
    Map<String, Object> attributes,
    OffsetDateTime occurredAt
) {}
```
- 用 record + Jackson JSON converter
- `attributes` 為動態欄位（避免每加一個欄位就要動 schema）
- `occurredAt` 給排序 / 對帳用

## 3. Publisher

```java
rabbitTemplate.convertAndSend("order.exchange", "order.created", payload);
```

時機：
| 事件 | 時機 |
|---|---|
| `order.created` | OrderService.create 結束前 |
| `inventory.deducted` | 每個 item 扣完後 |
| `order.paid` | OrderService.markPaid |
| `order.cancelled` | OrderService.cancel / expire（payload.attributes.reason 區分） |

> 注意：目前 publisher 與 DB 寫入「**不同一交易**」。極端狀況下可能 DB 寫成功但發送失敗。
> 嚴格場景應導入 **Outbox Pattern**：把事件寫入 `outbox` 表（同一交易），再用 polling worker 推到 broker。

## 4. Consumer + Retry + DLQ

設定（[application.yml](../src/main/resources/application.yml)）：
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          initial-interval: 1s
          max-attempts: 3
          multiplier: 2.0
        acknowledge-mode: auto
        prefetch: 16
        concurrency: 4
        max-concurrency: 16
```

- **retry**：失敗 3 次（指數退避）後，**訊息會被丟到該 queue 的 DLQ**（透過 queue 的 `x-dead-letter-*` argument）。
- **prefetch**：每個 consumer 一次最多預先取 16 筆，平衡吞吐與公平性。
- **concurrency**：每個 listener container 跑 4 條工作執行緒；尖峰可擴到 16。

DLQ 觀察：[OrderEventConsumer.onDlq](../src/main/java/com/xu/orderservice/event/OrderEventConsumer.java) 為示範，會把進入 DLQ 的訊息印 ERROR。實務上 DLQ 應有監控告警 / 人工介入 / 修 bug 後 republish。

## 5. 在管理頁觀察

`http://localhost:15672`（guest / guest）：
1. **Exchanges** → `order.exchange` → 看 bindings
2. **Queues** → 任一 queue → `Get messages` 偷看
3. **DLQ**：故意丟 bad payload 來模擬

## 6. Async + ThreadPool

`onOrderPaid` 標 `@Async("asyncTaskExecutor")`：耗時的副作用（模擬寄信）會被丟到我們的 ThreadPool（[AsyncConfig.java](../src/main/java/com/xu/orderservice/config/AsyncConfig.java)），不阻塞 listener。

> 小心：用 `@Async` 後 listener 會「**先 ack 再執行**」。如果寄信失敗就不會自動重試。
> 嚴格場景請改回同步、由 RabbitMQ 的 retry/DLQ 來保證至少一次。

## 7. 冪等性

Consumer 要假設「**至少一次**」：
- 寫 `order_events` 時 OK，因為這是新增、不會壞
- 模擬通知（log）也 OK
- 任何「**對外呼叫 / 寫業務狀態**」的 consumer，請以 `eventId` 或 `(orderId, eventType)` 做 dedup
