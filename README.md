# Java Spring Boot Word 週報彙整工具

支援多份 `.docx` 上傳，由 AI 理解章節、續行、專案背景與工作事項。後端依報告日期選用同事項的最新完整版本，保留舊檔獨有事項，直接在最新 Word 的 OOXML 上插入內容。附繁體中文網頁介面、Word 下載及逐事項 JSON 比對紀錄。

## 從 GitHub 下載完整成品

本專案放在私人儲存庫 [frjhong/cg](https://github.com/frjhong/cg)。登入有權限的 GitHub 帳號後，按 **Code → Download ZIP**，即可一次取得程式碼、Maven Wrapper、原始範例、可執行 JAR 及所有彙整成品。

- [彙整 Word](artifacts/彙整週報_1150930.docx)：開啟檔案頁面，按 **Download raw file** 下載。
- [彙整 PDF](artifacts/彙整週報_1150930.pdf)、[比對紀錄](artifacts/彙整比對紀錄.json)、[排版預覽](artifacts/彙整Word預覽.png)。
- [所有成品](artifacts/)與[原始 Word 範例](src/test/resources/samples/)。

解壓縮 ZIP 後，進入 `cg-main`；若使用 `git clone https://github.com/frjhong/cg.git`，則進入 `cg`。在該目錄執行下方指令即可。`DELIVERY-MANIFEST.json` 記錄各交付檔案的大小及 SHA-256，可用來核對下載完整性。

## 環境與 Maven Wrapper

建置需要 **JDK 21**（包含 `javac`），不需另外安裝 Maven。專案附 Apache Maven Wrapper 3.3.4，固定下載並使用 Maven 3.9.9，包含 Windows 啟動檔與 Wrapper JAR。Maven 發行檔以 SHA-256 驗證。首次使用需連線下載 Maven 與專案依賴；範例重播啟動已建置的 JAR 則不需 AI 金鑰或外部 API 連線。

解壓縮專案後，先進入含 `pom.xml` 的專案根目錄。確認 JDK：

```text
java -version
javac -version
```

兩者應顯示 21；若使用多套 Java，設定 `JAVA_HOME` 指向 JDK 根目錄，並將其 `bin` 加入 `PATH`。

| 作業 | Linux / macOS | Windows PowerShell |
|---|---|---|
| 確認 Wrapper 版本 | `./mvnw --version` | `.\mvnw.cmd --version` |
| 建置並執行全部測試 | `./mvnw clean verify` | `.\mvnw.cmd clean verify` |
| 僅執行測試 | `./mvnw test` | `.\mvnw.cmd test` |

建置產物是 `target/weekly-report-1.0.0.jar`。下載的專案包另附 `artifacts/weekly-report-1.0.0.jar`，可直接執行。每次修改程式後，請重新執行 `clean verify`。

## 快速試用：提供範例的重播模式

Linux / macOS：

```bash
./scripts/run.sh demo
```

Windows PowerShell：

```powershell
.\scripts\run.cmd demo
```

開啟 <http://localhost:8080>，選擇 `src/test/resources/samples/0916.docx` 與 `0930.docx`，按「開始彙整」，完成後下載 Word 與比對 JSON。此模式僅接受完整的兩份原始範例，不可修改內容或改用其他週報。停止服務請按 `Ctrl+C`。

啟動腳本會切換到專案目錄，設定範例計畫的絕對路徑，優先執行 `target/` 的 JAR，其次使用 `artifacts/` 的 JAR；兩者都不存在時，以 Wrapper 建置並執行測試後啟動。

重播計畫由本次 AI 助理依實際週報建立，SHA-256 綁定兩份原始檔，用於驗證解析、分組套用、保留格式與下載流程，**不是外部模型 API 的實際推論結果**。本環境未提供外部 AI 金鑰，外部模型推論尚未實測。

## 正式 AI 模式：處理自己的週報

Linux / macOS：

```bash
export AI_API_KEY='替換成你的金鑰'
export AI_MODEL='gpt-4.1'
export AI_BASE_URL='https://api.openai.com/v1'
./scripts/run.sh ai
```

Windows PowerShell：

```powershell
$env:AI_API_KEY = '替換成你的金鑰'
$env:AI_MODEL = 'gpt-4.1'
$env:AI_BASE_URL = 'https://api.openai.com/v1'
.\scripts\run.cmd ai
```

`ai` 模式會清除啟動程序的 `AI_FIXTURE`，避免先前範例設定影響正式處理；未設定金鑰時直接提示並退出。金鑰只由伺服器環境變數讀取，不送到瀏覽器。週報文字會傳送至指定的 AI API，原始 DOCX 二進位檔與圖片不直接送出。

AI 分組計畫在產檔前會檢查 ID、章節引用、版本與每個原始正文區塊的完整性。首次計畫不合格時，伺服器會回送具體錯誤，要求 AI 重新產生完整計畫，合計最多 3 次請求；每次都必須通過相同檢查。三次仍不合格就停止並顯示最後的具體錯誤，不會刪除缺漏事項或自動切換為範例重播。HTTP 金鑰／模型錯誤不會觸發此修正流程。

## 其他啟動方式

透過 Maven 啟動開發服務（先設定金鑰，並清除重播設定）：

```bash
unset AI_FIXTURE
./mvnw spring-boot:run
```

```powershell
Remove-Item Env:AI_FIXTURE -ErrorAction SilentlyContinue
.\mvnw.cmd spring-boot:run
```

直接執行建置後的 JAR（同樣使用前述環境變數）：

```text
java -jar target/weekly-report-1.0.0.jar
```

只使用專案包隨附的版本時，可改成 `java -jar artifacts/weekly-report-1.0.0.jar`。若直接執行 JAR 或 Maven，請在專案根目錄執行；要自行啟用重播時，設定 `AI_FIXTURE=src/test/resources/sample-plan.json`。

## 設定

| 環境變數 | 預設值 | 用途 |
|---|---|---|
| `AI_API_KEY` | 空 | 正式 AI 模式必填，僅由伺服器讀取 |
| `AI_BASE_URL` | `https://api.openai.com/v1` | OpenAI 相容 API 基底網址，程式附加 `/chat/completions` |
| `AI_MODEL` | `gpt-4.1` | 模型名稱，需支援 Chat Completions 的 `response_format: json_object` |
| `AI_FIXTURE` | 空 | 範例重播計畫路徑；設定後優先使用重播，忽略外部 AI |
| `PORT` | `8080` | HTTP 監聽埠 |
| `HTTPS_PROXY` | 未設定 | AI 用戶端的 HTTP CONNECT 代理，例如 `http://proxy.example.com:8080` |

更換連接埠：Linux/macOS 用 `PORT=8081 ./scripts/run.sh demo`；PowerShell 先執行 `$env:PORT = '8081'` 再啟動。瀏覽器改開 <http://localhost:8081>。`.env` 不會自動載入，請在啟動前設定環境變數。

Maven 的依賴下載代理另由 Maven `settings.xml` 管理，例如 `./mvnw -s /path/to/settings.xml clean verify`；Windows 將命令替換成 `.\mvnw.cmd`。Wrapper 第一次下載 Maven 的代理可透過 JVM 的 `-Dhttps.proxyHost`、`-Dhttps.proxyPort` 設定。企業自訂 CA 可用 JVM `-Djavax.net.ssl.trustStore=/path/to/cacerts` 設定；不關閉 TLS 驗證。Wrapper 啟動 JVM 與 Maven 可使用 `MAVEN_OPTS`；直接啟動 JAR 時可用 `JAVA_TOOL_OPTIONS`，或把 JVM 選項放在 `java` 與 `-jar` 之間。

## 範例結果與驗證

- 正文民國日期判定：115/09/16 → 2026-09-16；115/09/30 → 2026-09-30。採 `0930.docx` 作範本，與上傳順序無關。
- 合計 27 個事項，2 組跨週重複事項採最新版本，14 個舊檔獨有事項保留。
- Applications Manager 採 09/30「軟體更新、處理中」；電子商務主機汰換只保留最新專案段落。
- 防毒事件、GitLab、CDC 同步設定、CDC/T2 移轉專案等舊檔獨有工作保留。不同週期／模組的 TABLE 異動與資料減量批次保留，兩個減量筆數都存在。
- 彙整後所有範本可見內容與格式保留；段落屬性、字型、粗體、縮排、行距、頁面設定與原始編號定義均檢查。移除舊的自動分頁快取 `lastRenderedPageBreak`，讓 Word 重新排版；移除文件尾端不含任何內容／控制元素的純空白段落，避免新增內容後擠出尾端空白頁，並記錄移除數量。手動分頁與段落間留白保留。舊檔 DB2 條目接到範本的編號 36；新增專案接到專案編號，進度子清單另匯入編號定義。
- 測試直接讀取原始 Word，檢查勝出內容、舊進度移除、範本資源、日期、惡意 XML、AI 遺漏／重複引用、HTTP AI 相容協定、上傳、下载、稽核與刪除；另測資源關聯和樣式衝突。

24 項 JUnit 測試已通過；完整原始驗證見 [VALIDATION.md](VALIDATION.md)。產物位於 `artifacts/`，包括彙整 Word、比對 JSON、排版 PDF、截圖與隨附 JAR。`./mvnw test`（Windows 用 `.\mvnw.cmd test`）會重新以真實範例產生 Word 與比對 JSON；PDF 與截圖不會由 Maven 測試自動更新。

專案結構：

```text
weekly-report/
├── mvnw / mvnw.cmd            Maven Wrapper
├── .mvn/wrapper/              固定版本、校驗值與 Wrapper JAR
├── pom.xml                    Spring Boot 3.5.6 / Java 21
├── scripts/run.sh / run.cmd   AI 與範例重播啟動腳本
├── src/main/java/             Word 解析、AI 分組、格式匯入與 API
├── src/main/resources/        服務設定與繁體中文網頁
├── src/test/                  24 項測試、兩份 Word 範例與語意計畫
├── target/                    本機建置產物（不隨專案包附送）
├── artifacts/                 隨附 JAR、彙整檔與驗證產物
└── VALIDATION.md              範例測試的證據與限制
```

## API

```bash
curl -F 'files=@src/test/resources/samples/0916.docx' \
     -F 'files=@src/test/resources/samples/0930.docx' \
     -F 'dates={}' http://localhost:8080/api/merge
```

回應包含 `id`、`downloadUrl`、`auditUrl` 與 `summary`。

| 方法 | 路徑 | 用途 |
|---|---|---|
| GET | `/api/config` | AI 設定狀態與重播標記，不含金鑰 |
| POST | `/api/merge` | multipart `files`，選填 `dates` JSON |
| GET | `/api/reports/{id}/word` | 下載 DOCX |
| GET | `/api/reports/{id}/audit` | 查看逐事項選擇依據 |
| DELETE | `/api/reports/{id}` | 刪除暫存結果 |

`dates` 格式：`{"0930.docx":"2026-09-30"}`。優先使用指定日期，其次正文前兩個非空標題段，再其次檔名完整年月日。不猜測只有 `MMDD` 的年份。相同事項若有兩個相同日期版本，會停止並提示指定日期；最新日期有多份不同事項文件時，依檔名排序選範本。

## 常見問題

| 狀況 | 處理方式 |
|---|---|
| `Permission denied: ./mvnw` 或啟動腳本 | 執行 `chmod +x mvnw scripts/run.sh`；或使用 `sh ./mvnw` / `sh scripts/run.sh demo` |
| `release version 21 not supported`、找不到 `javac` | 安裝完整 JDK 21，確認 `JAVA_HOME`、`java -version` 與 `javac -version` 指向同一套 JDK |
| `UnsupportedClassVersionError` | 執行 JAR 的 Java 太舊，切換至 Java 21 或更新版本 |
| Wrapper／依賴下载失敗 | 首次建置需要網路；確認 Maven Central 可達，以及 Maven 代理、自訂 CA、Wrapper JVM 代理設定 |
| `PKIX path building failed` | 將企業 CA 匯入受信任的 Java trustStore，並指定 `javax.net.ssl.trustStore` |
| `Port 8080 was already in use` | 停止原服務，或設定另一個 `PORT` |
| 「尚未設定 AI API」 | 設定 `AI_API_KEY` 後重啟；要試用提供範例則執行 `run.sh demo` / `run.cmd demo` |
| 重播模式拒絕其他文件 | 重播只支援指定原始範例，改用 `ai` 模式並設定金鑰 |
| AI 回應 HTTP 401 / 403 / 404 | 檢查金鑰權限、API 基底網址及模型名稱；`AI_BASE_URL` 不要包含 `/chat/completions` |
| 「AI 遺漏內容」或計畫驗證失敗 | 新版自動回送具體驗證錯誤並要求 AI 修正，最多 3 次；仍失敗則停止產檔。依最後錯誤檢查模型的 JSON 支援及文件結構，更新後需重新啟動新版 JAR |
| 下載回傳 404 | 結果已刪除、過期或服務已重啟，請重新上傳彙整 |
| Word 與 PDF 換行不同 | 安裝原週報使用的字型；保留格式不等於不同排版引擎像素一致 |

## 實作與限制

- `AiPlanner` 以 JSON 回傳章節與各事項的原始區塊引用；模型不生成改寫的正文。`MergePlan` 要求正文／圖片／表格區塊完整且只使用一次，禁止把工作段落排除為 metadata。
- `MergeService` 依程式解析的日期選版本，在所屬章節最後一個原始內容區塊後插入獨有事項。模板原內容保留，跨檔相同事項只保留最新完整版本；不累積已被新版本取代的舊背景描述。
- `WordPackage` 用 JDK ZIP 與安全 XML 解析，避免整份重建造成格式遺失。範本頁首頁尾、圖片、設定及其關聯保留。`PartImporter` 複製舊事項的圖檔／超連結與依賴資源、編號，衝突樣式重新命名，保留範本定義。
- 表格以完整區塊保留，不逐格重排。跨區塊書籤、舊內容的評論／註腳／尾註、圖片項目符號尚不支援，會明確拒絕以避免損壞。複雜 OLE、資料綁定內容控制項、外部連結圖、修訂追蹤未經完整測試；提供範例為段落與編號清單。
- 新增內容後 Word 自然重新分頁；完整保留原頁數不合理。可保留頁面設定與格式，實際字型與分頁仍取決於閱讀端安裝字型。視覺驗證使用 LibreOffice，不等同 Microsoft Word 的像素一致性。
- 支援 2–20 檔、每檔 10MB、單次 50MB；解壓縮、ZIP 路徑、XML 外部實體及 AI 輸入大小有檢查。
- 結果以隨機 UUID 存在記憶體，24 小時到期，可刪除，重啟即清空，上限 25 組。適合單人／內部原型；公開多使用者部署應在反向代理加入身分驗證並改為各使用者的持久化儲存。
- 模型可能做出錯誤的語意分組。逐事項來源與理由可供檢查；本次已驗證重播內容及 AI HTTP 介接，環境未提供外部 AI 金鑰，因此未執行外部模型品質測試。
