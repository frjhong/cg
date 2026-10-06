# 實際範例驗證報告

使用提供的原始 `0916.docx` 與 `0930.docx`，未修改原始上傳檔。

## 結果

- 以正文日期民國 115/09/30 的 `0930.docx` 作範本。
- 彙整 27 個工作事項，2 組跨週重複採最新完整版本，14 個舊檔獨有事項保留，插入 22 個原始正文區塊。
- Applications Manager 採最新軟體更新、處理中；汰換專案只保留最新版本；防毒事件、GitLab、CDC/T2 專案等舊事項保留。
- 不同週／對象的 TABLE 異動與資料減量保留為不同作業，1,044,832 與 4,670,754 兩個筆數都存在。
- 清除失效的自動分頁快取與 1 個尾端純空白段落；手動分頁、其他段落間留白與所有可見範本正文及格式保留。

## 實際執行的檢查

初版 `mvn verify`：13 項 JUnit 測試，0 失敗、0 錯誤。涵蓋原始範例合併、全部勝出區塊、去重與舊事項保留、原始可見段落與段落格式、頁面設定、未變更的範本資源、編號定義、圖檔／超連結關聯、樣式衝突、旧檔父子章節、日期衝突、AI 遺漏／重複引用、XXE、防錯及 HTTP 介接。

實際啟動可執行 JAR，在 Chromium 上完成：兩份範例上傳、指定日期、27 列比對結果、Word 下載、JSON 下載、390px 手機寬度无水平溢出、刪除結果及下載回傳 404；無 JavaScript 錯誤。瀏覽器下載的 DOCX 正文 XML 與產出的彙整 Word 完全一致。

LibreOffice 25.2 實際開啟並匯出 PDF：3 頁，每頁都有內容，無空白頁。檢查 PDF 中關鍵的進度、筆數、碳排資料、原專案姓名及防毒分析結果均可讀取。Linux 缺少原始標楷體，排版檢查使用 Noto Serif CJK TC 替代字型；DOCX 中的原始字型設定保留。PDF 頁數與换行仅供辅助驗證，不宣稱與 Microsoft Word 像素一致。

## AI 驗證範圍

2026-10-06 修正「AI 工作事項缺漏或重複」：將混合錯誤訊息拆為 ID 重複、欄位缺漏、章節引用及版本清單等具體原因，加強提示的全域唯一 ID 與完整區塊引用規則。正式 AI 用戶端在回傳計畫前先驗證；不合格時回送原因要求修正，合計最多 3 次請求，仍不合格便停止，不產生 Word，也不切換範例計畫。

新版隨附 JAR 已實際透過 `scripts/run.sh ai` 啟動，連接本地模擬 API：第一次回應故意重複事項 ID，第二次依驗證回饋回傳合法計畫。透過 `/api/merge` 上傳原始兩份 Word，再實際下載 DOCX，確認 27 個事項、2 組去重、14 個舊檔獨有事项與新進度、GitLab、兩個減量筆數。此測試使用 live-ai 程式流程，並未啟用 fixture-replay；API 仍為本地模擬服務。結果見 `artifacts/ai-recovery-smoke.json`。

本次 `./mvnw clean verify`：24 項測試，0 失敗、0 錯誤。新增 11 項 HTTP 回歸案例，直接使用提供的兩份原始 Word：9 種不合格 AI 回應（事項 ID 重複、不存在章節、缺 reason、版本空、正文遺漏、區塊重複、不合法 JSON、空計畫、輸出截斷）首次遭拒後，模擬 AI 回傳修正計畫，實際產出 Word 並確認最新進度、14 個舊檔獨有事項與兩個減量筆數仍保留；另驗證連續 3 次無效計畫會停止，以及 HTTP 401 不重試。這些回應由本地模擬 API 提供，不代表已執行外部模型推論。

本環境未提供外部 AI API 金鑰。此範例使用 AI 助理依實際正文判讀建立的語意分組計畫，綁定兩份檔案的 SHA-256，標示為 fixture-replay。已透過本地 HTTP 相容 API 測試真實用戶端請求／回應與計畫驗證；未測試外部模型的實際語意品質。正式處理其他文件，需移除 AI_FIXTURE 並設定 AI_API_KEY。

詳細結果：`artifacts/validation.json`、`artifacts/彙整比對紀錄.json`。Word、PDF 及桌面／手機介面截圖也位於 `artifacts/`。

## Maven Wrapper 與啟動方式補充驗證

新增官方 Maven Wrapper 3.3.4（固定 Maven 3.9.9），含 `mvnw`、`mvnw.cmd`、Wrapper JAR、發行檔與 Wrapper JAR 的 SHA-256 校驗值。使用新的空 Wrapper 快取實際下載並確認 Maven 3.9.9，再用 `./mvnw clean verify` 建置；13 項測試全部通過。

Linux `scripts/run.sh demo` 從 `/tmp` 啟動成功，可讀取範例計畫並完成兩份原始 Word 的 HTTP 彙整與下載。`scripts/run.sh ai` 使用啟動檢查用的假金鑰，確認清除原有 `AI_FIXTURE`、切換至 live-ai 及啟動建置後 JAR；未對外發出 AI 推論請求。參數錯誤與缺少金鑰會回傳退出碼 2；Shell 語法檢查通過。附 Windows `run.cmd` 與 PowerShell 指令，但本環境是 Linux，未實際執行 Windows 腳本。

README 已包含 JDK 設定、Wrapper 建置、跨平台啟動、AI／範例重播、直接 JAR 與 Maven 開發服務、環境變數、代理與 CA、API、專案結構及常見錯誤處理。Wrapper 本次驗證使用此雲端環境的代理與信任儲存庫，這些環境專屬設定未寫入專案。
