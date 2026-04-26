# =============================================================
# smoke-test.ps1
# 對啟動中的服務做一輪「基本流程」驗證：
#   seed -> 列商品 -> 建立訂單 -> 付款 -> 查詢事件
# 使用方法（PowerShell）：
#   .\scripts\smoke-test.ps1            # 預設 http://localhost:8080
#   .\scripts\smoke-test.ps1 -BaseUrl http://localhost:8080
# =============================================================

param(
    [string]$BaseUrl = 'http://localhost:8080'
)
$ErrorActionPreference = 'Stop'

function Step($name) { Write-Host ''; Write-Host ('== ' + $name + ' ==') -ForegroundColor Cyan }
function CallJson($method, $path, $body = $null) {
    $params = @{ Method = $method; Uri = "$BaseUrl$path"; ContentType = 'application/json' }
    if ($body) { $params.Body = ($body | ConvertTo-Json -Depth 8) }
    return Invoke-RestMethod @params
}

Step '1) Health'
$health = CallJson 'GET' '/health'
$health | ConvertTo-Json -Depth 5

Step '2) Seed demo data'
$seed = CallJson 'POST' '/api/demo/seed'
$seed | ConvertTo-Json -Depth 5

Step '3) List products'
$products = CallJson 'GET' '/api/products'
$products.data | Format-Table id, sku, name, price

$productId = $products.data[0].id
$userId    = 1

Step ('4) Create order (productId={0})' -f $productId)
$order = CallJson 'POST' '/api/orders' @{
    userId       = $userId
    items        = @(@{ productId = $productId; quantity = 1 })
    lockStrategy = 'OPTIMISTIC'
}
$order.data | Format-Table id, orderNo, status, totalAmount

$orderId = $order.data.id

Step ('5) Pay order {0}' -f $orderId)
$paid = CallJson 'POST' "/api/orders/$orderId/pay"
$paid.data | Format-Table id, orderNo, status

Step '6) Query order events'
$events = CallJson 'GET' "/api/orders/$orderId/events"
$events.data | Format-Table id, eventType, createdAt

Step 'Smoke test passed.'
