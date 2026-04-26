---
name: technical-writing
description: 撰寫或更新本專案 docs / README / 使用方法 時，保持「初學者可讀」+「資深工程師可參考」的雙重標準。
---

# 何時使用
- 加新功能 → 同步更新 docs
- 改了行為 → 改文件
- 寫新的 design note

# 工作步驟
1. 標題使用 H1 / H2 / H3，避免跳級。
2. 先寫「為什麼」（problem statement）→ 再寫「怎麼做」→ 再寫「取捨」。
3. 給 Windows 11 + PowerShell 範例（指令）；Linux/macOS 補在後面或註解。
4. 用 markdown table 比較選項。
5. 連結到原始碼用相對路徑：`[file.java](../src/.../File.java)`。
6. Diagram 用 ASCII art；簡單清楚 > 漂亮但難維護。
7. 結尾常常加「未做但建議的下一步」清單。

# 品質標準
- 整篇可在 5 分鐘內讀完概觀，10 分鐘讀完細節。
- 沒有未定義的縮寫。
- 給的指令必須能直接複製貼上跑（不要寫 `<your-host>` 之類佔位符，給預設值）。

# 禁止事項
- 不要寫「我」、「我們覺得」這類主觀語氣，多用客觀陳述。
- 不要重複 README 的內容；用連結串起來。
- 不要把過時/實驗性決策寫進 docs；放 `research-notes.md` 才適合。

# 輸出格式
- 完整 markdown，繁體中文
