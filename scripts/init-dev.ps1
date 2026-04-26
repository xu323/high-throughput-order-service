# =============================================================
# init-dev.ps1
# 一鍵啟動本機開發所需的基礎服務（MySQL / Redis / RabbitMQ + App）。
# 使用方法（PowerShell）：
#   .\scripts\init-dev.ps1
# =============================================================

$ErrorActionPreference = 'Stop'

Write-Host '== Step 1. 檢查 Docker =='
docker version | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Docker 未啟動。請先打開 Docker Desktop 再執行此腳本。'
    exit 1
}

Write-Host '== Step 2. 複製 .env.example -> .env（若不存在） =='
if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host '已建立 .env，後續可自行修改密碼。'
}

Write-Host '== Step 3. 啟動 docker compose =='
docker compose up -d --build

Write-Host '== Step 4. 等待服務 healthy =='
$maxWait = 120
$elapsed = 0
do {
    Start-Sleep -Seconds 5
    $elapsed += 5
    $unhealthy = docker compose ps --format json | ConvertFrom-Json |
        Where-Object { $_.Health -and $_.Health -ne 'healthy' -and $_.Health -ne '' }
    if (-not $unhealthy) { break }
    Write-Host ('  仍在等待 ({0}s) ...' -f $elapsed)
} while ($elapsed -lt $maxWait)

Write-Host ''
Write-Host '== 完成 =='
Write-Host '應用程式：     http://localhost:8080'
Write-Host 'Swagger UI：   http://localhost:8080/swagger-ui/index.html'
Write-Host 'RabbitMQ UI：  http://localhost:15672  （帳號 guest / guest）'
Write-Host '查看 logs：    docker compose logs -f app'
