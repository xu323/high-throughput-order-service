# API 文件

> 啟動後可直接看 Swagger：`http://localhost:8080/swagger-ui/index.html`

## 0. 通用

### 回應結構（ApiResponse）

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": { ... },
  "timestamp": "2026-04-26T10:00:00+08:00"
}
```

### 錯誤碼

| code | 說明 |
|---|---|
| OK | 正常 |
| VALIDATION_FAILED | 請求驗證失敗（400） |
| NOT_FOUND | 找不到資源（404） |
| CONFLICT | 衝突，例如已存在（409） |
| INSUFFICIENT_STOCK | 庫存不足（409） |
| INVALID_ORDER_STATUS | 訂單狀態不允許此操作（409） |
| LOCK_ACQUISITION_FAILED | 鎖搶不到 / 樂觀鎖重試耗盡（409） |
| PAYMENT_FAILED | 付款失敗（400） |
| INTERNAL_ERROR | 伺服器內部錯誤（500） |

---

## 1. Health

### `GET /health`

```http
GET /health
```

回應：
```json
{ "data": { "status": "UP", "service": "high-throughput-order-service" } }
```

---

## 2. Products

### `GET /api/products`

```json
{ "data": [ { "id": 1, "sku": "SKU-1001", "name": "Limited Sneakers", "price": 3990.00, ... } ] }
```

### `POST /api/products`

```json
{
  "sku": "SKU-XYZ",
  "name": "New Product",
  "description": "...",
  "price": 199.00,
  "initialStock": 100
}
```
回應：`201 Created`，`data` 為 `ProductDto`。

### `GET /api/products/{id}`

回應：`ProductDto`。

### `GET /api/products/{id}/inventory`

```json
{ "data": { "productId": 1, "availableStock": 100, "reservedStock": 0, "version": 0 } }
```

---

## 3. Orders

### `GET /api/orders?userId=1`

回應：使用者最近 100 筆訂單。

### `POST /api/orders`

```json
{
  "userId": 1,
  "items": [ { "productId": 1, "quantity": 2 } ],
  "lockStrategy": "OPTIMISTIC"
}
```
- `lockStrategy` 可省略，預設 `OPTIMISTIC`，可選 `REDIS_LOCK`。
- 成功：`201`，`data.status = CREATED`，`data.expiresAt` 為 15 分鐘後（可由 `order.timeout-minutes` 調整）。
- 失敗：
  - `INSUFFICIENT_STOCK`：庫存不夠
  - `LOCK_ACQUISITION_FAILED`：鎖搶不到（樂觀鎖重試耗盡 / Redis 鎖等待逾時）

### `GET /api/orders/{id}`

回應：`OrderDto`，含 `items[]`。

### `POST /api/orders/{id}/pay`

把訂單從 `CREATED` 轉到 `PAID`。失敗回 `INVALID_ORDER_STATUS`（如已 cancelled）。

### `POST /api/orders/{id}/cancel`

把訂單從 `CREATED` 轉到 `CANCELLED`，並 **補回庫存**。

### `GET /api/orders/{id}/events`

```json
{
  "data": [
    { "id": 10, "orderId": 1, "eventType": "ORDER_CREATED",     "createdAt": "..." },
    { "id": 11, "orderId": 1, "eventType": "INVENTORY_DEDUCTED", "createdAt": "..." },
    { "id": 12, "orderId": 1, "eventType": "ORDER_PAID",         "createdAt": "..." }
  ]
}
```

---

## 4. Demo / Admin

### `POST /api/demo/seed`

寫入 demo 使用者 + 商品 + 庫存。重複呼叫安全（只做一次）。

### `POST /api/demo/reset`

清除訂單相關資料、庫存補回原值。**保留商品與使用者**。

### `POST /api/demo/concurrency-test`

```json
{
  "productId": 1,
  "threads": 16,
  "ordersPerThread": 10,
  "quantityPerOrder": 1,
  "lockStrategy": "OPTIMISTIC"
}
```

回應：

```json
{
  "data": {
    "productId": 1,
    "initialStock": 100,
    "remainingStock": 0,
    "expectedDeducted": 100,
    "actualSuccessOrders": 100,
    "failedOrders": 60,
    "elapsedMillis": 1234,
    "lockStrategy": "OPTIMISTIC",
    "overSold": false
  }
}
```

`overSold` 為 `false` 代表防超賣有效。
