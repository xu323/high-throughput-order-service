# .claude/hooks

預留資料夾。如未來要在「PostToolUse / Stop / Notification」等事件觸發自動化（例如：每次寫完 Java 檔自動跑 `mvn -q -pl . compile`），可在這裡放 hook 設定，並在 `.claude/settings.json` 註冊。

目前不放 hook 是為了避免初學者被 hook 失敗訊息干擾。
