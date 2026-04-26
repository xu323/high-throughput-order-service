---
name: cicd-quality
description: 改動 CI workflow / Docker / Maven build 時，保持綠燈與初學者友好。
---

# 何時使用
- 改 `.github/workflows/ci.yml`
- 改 Dockerfile / docker-compose.yml
- 加 Maven plugin
- 改變測試切分策略

# 工作步驟
1. CI 步驟保持線性：checkout → setup-java → cache → compile → test → verify → package → docker build → docs check → upload。
2. 永遠標 `actions/setup-java@v4` + `cache: maven`。
3. Docker build：不要把 secrets 寫進 image；用 multi-stage 把 build 階段與 runtime 階段分開。
4. docker-compose 服務都加 `healthcheck`，下游服務用 `depends_on: condition: service_healthy`。
5. 加新文件後同步更新 `docs/` 的列表，並把它加進 ci.yml 的「docs check」清單。

# 品質標準
- `mvn -B -ntp ...` 一律加 `-B -ntp`，避免 CI log 噪音。
- CI 跑時間 < 10 分鐘；超過要切 job。
- 失敗的 step 必須有清楚 error message，不要 `|| true` 掩蓋。

# 禁止事項
- 不要把 token / 密碼 commit 進 workflow（用 `${{ secrets.X }}`）。
- 不要 `actions/checkout@v2` 等舊版本。
- 不要在 production image 安裝 mvn / curl 等不必要工具。

# 輸出格式
- 完整的 yml / Dockerfile（不要 diff）
