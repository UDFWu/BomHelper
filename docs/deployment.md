# BOM 報告取代、下載及帳號登入

## 資料庫部署

既有資料庫：先備份 BOMSDB，於停止上傳的維護時段執行
[`sql/001_latest_report_and_users.sql`](../sql/001_latest_report_and_users.sql)，再部署新版。
新資料庫：先執行原始 `DB_DDL.sql`，再執行上述升級腳本。

若已完成前次 BomUser 建表，先執行 [`003_user_admin_role.sql`](../sql/003_user_admin_role.sql)
移除帳號權限、狀態及密碼格式 CHECK 限制，再執行 [`004_bootstrap_admin.sql`](../sql/004_bootstrap_admin.sql) 建立第一個管理員。
新資料庫的 001 不再建立帳號規則 CHECK 限制，可直接接著執行 004。已執行舊版 003 的環境，執行新版 005_application_account_policy.sql 即可，不必重建帳號。
004 建立帳號 `admin`、初始密碼 `12345678`、權限 `9999`、狀態 `A`，只將 Jasypt ENC(...) 密文寫入 DB。
若 admin 已存在會停止，不會覆寫帳密或提升既有帳號權限。SQL 本次僅提供，未對實際資料庫執行。

腳本會依 `GitlabGroupId + GitlabProjectId` 分組，保留 `ImportDate` 最新的一筆；
日期相同時保留 Id 最大的一筆。其餘主檔與三張明細表透過原 DDL 的外鍵 cascade 刪除，
再建立 `UQ_BomReport_Group_Project` 唯一限制及 `dbo.BomUser`。
腳本使用交易，失敗時全部回復；請勿略過唯一限制後直接部署。
本次開發未直接修改實際 BOMSDB，也未在 SQL Server 執行此遷移。

上傳會先解析整份 XML，再在同一筆 SERIALIZABLE 交易內刪除該專案舊主檔與明細、
寫入新報告。相同專案可重複使用 Scan ID；其他專案已使用的 Scan ID 會被拒絕。
同時上傳發生鎖定／唯一限制衝突時會回復該次交易並回傳 HTTP 409，可重試。
成功上傳的報告為保留版本，並非依 XML 的掃描時間挑選。

網頁沿用選取的 GitLab Group、由 metadata component name 推斷 Project（移除 `iq_application_`）；
CI 沿用傳入的 Group／Project。請讓同一專案的網頁與 CI 使用相同識別格式，
不要混用數字 ID 與 path，否則資料庫會視為不同專案。

## 帳號與權限

| 欄位 | 用途 |
| --- | --- |
| UserId | 使用者代號，主鍵，nvarchar(100) |
| UserPassValidWord | 一般本機帳號：PBKDF2 雜湊；初始化 admin：Jasypt ENC(...)；0113：NULL |
| Status | A 啟用、D 停用 |
| AuthorityCode | 0170 資訊安全處、0113 資訊研發處、9999 管理員 |
| CreatedBy / CreatedDate | 建立人員代號／日期 |
| UpdatedBy / UpdatedDate | 最後異動人員代號／日期 |

- **9999**：本機管理員，可查詢、下載全部報告；導覽列的「報告檔案上傳」下方顯示「使用者管理」。
  管理頁面 `/users` 可建立 0170／9999 本機帳號及 0113 GitLab 帳號、指定啟用或停用狀態，並分頁檢視帳號及建立／異動資訊。
  選擇「0113 · 資訊研發處」時不需密碼，DB 密碼欄位為 NULL，UserId 須與 GitLab 使用者名稱相同。
  頁面和 POST 都要求 9999，還會重新檢查 DB 中的帳號狀態與權限；GitLab Admin 不具備本機帳號管理權限。
  建立人員與日期由伺服器填寫，不接受前端偽造；列表與錯誤畫面不回傳密碼或雜湊。
  不新增 GitLab 上傳權限，仍沿用既有上傳群組檢核。
- **0170**：登入畫面選「本機帳號」。帳號由 9999 管理員在使用者管理頁面建立，可查詢及下載全部報告。
  不授予 GitLab 管理員身分或報告上傳權限。
- **0113**：選「GitLab 帳號」。登入成功後只在帳號不存在時建立 BomUser；
  系統自動建立時，CreatedBy 與 UpdatedBy 均為 `system`。管理員預先建立或手動異動時，記錄實際管理員帳號。
  已存在的帳號不更改權限、密碼、狀態及建立／異動資訊。非啟用的 GitLab 或本系統帳號無法登入。
  只在登入時同步，沒有背景同步工作；GitLab 權限沿用既有登入取得的 Group／Project 範圍及既有管理員規則。
  不保存 GitLab 密碼，也不能以本機登入使用 0113 帳號。
- 同一 UserId（不區分大小寫）若已是 0170 或 9999，GitLab 登入不新增或覆寫帳號。
  GitLab 登入的存取範圍仍依 GitLab 成員權限，不因同名本機角色提升權限。
- 管理員可在「建立使用者」下方的「異動使用者」設定 A／D，程式填入異動人員與時間。
- 本機帳號停用在下次登入生效；現有登入 session 依原有 session 生命週期處理。

專案 Maven artifactId／name、Spring application name 及頁面品牌名稱為 `sbomHelper`，部署產物為 `target/sbomHelper.war`。
外部 Tomcat 以 WAR 名稱決定 context path 時，新預設路徑為 `/sbomHelper`；反向代理設定需同步更新。
Java 套件名稱與資料表名稱維持相容；Jasypt 主密鑰亦沿用既有設定。

管理頁面建立的本機帳號密碼使用 Spring Security 的 PBKDF2-HMAC-SHA256，600,000 次迭代、16-byte 隨機 salt、
256-bit 輸出；不可逆，不提供解密。使用以下工具產生雜湊，再套用
[`sql/002_local_user_example.sql`](../sql/002_local_user_example.sql) 建立帳號。
該 SQL 也包含停用與重設密碼範例，不會建立預設密碼或預設管理員。

Windows PowerShell（已設定 JDK 17+）：

```powershell
.\mvnw.cmd "-Dmaven.repo.local=D:\.m2" -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt" "-DincludeScope=runtime"
$bomClasspath = 'target/classes;' + (Get-Content target/runtime-classpath.txt -Raw).Trim()
java -cp $bomClasspath com.scsb.bomhelper.util.LocalPasswordCli
```

工具要求互動式終端機，以隱藏輸入讀取 8–256 字元密碼，只輸出雜湊。
一般帳號請由管理頁面建立；CLI 與 SQL 範例供管理人員批次作業使用。不要將一般帳號明文密碼放入命令參數。

## 查詢與下載

本次部署須先執行 007，避免舊 XML 欄位在新程式上傳時繼續改寫空白或拒絕 encoding 宣告。

兩種查詢結果皆新增「下載報告」，端點為 `GET /api/v1/bom/reports/{id}/download`。
伺服器重新檢核權限，以 XML attachment 傳回原始 `RawXmlBytes`（不重新排版、不轉換編碼）；
未授權或報告不存在回傳 404，未登入由 Spring Security 導向登入。
檔名為 `<GitLab Project Name>-scan-report.xml`，優先使用 GitLab API 的專案顯示名稱，API 不可用時回退至資料庫專案代號的最後一段。
檔名會替換不合法字元，並支援 UTF-8 中文名稱。
部署前執行 [007_preserve_report_xml_format.sql](../sql/007_preserve_report_xml_format.sql)，
將 RawXmlContent 由 XML 轉為 nvarchar(max)；此步驟對新建與既有 DB 都適用。
請先備份並於維護時段執行；轉換在同一交易中完成，保留現有內容，不可還原已被 XML 型別移除的換行。
另須先執行 [008_preserve_original_report_bytes.sql](../sql/008_preserve_original_report_bytes.sql)，新增 `RawXmlBytes varbinary(max)`。
新上傳直接保存檔案位元組，下載與原檔逐位元組相同，包括 LF／CRLF／CR、縮排、空白、BOM 與編碼宣告。
既有資料沒有 `RawXmlBytes` 時沿用 UTF-8 文字下載；需要重新上傳原檔才能恢復已遺失的格式。
回歸測試以 `cfms-bom.xml` 實際匯入、DB 讀回再下載，比對完整位元組內容。

設定伺服器環境變數 `GITLAB_ADMIN_TOKEN` 為管理者 Personal Access Token（需可讀取 API），不要將 token 寫入版本控制。
未設定 token 時，仍支援 `gitlab.admin-username`／`gitlab.admin-password` 管理者帳密設定。
本機或 GitLab 登入成功後，以管理者 API 分頁取得所有 Group／Project 及其 members/all（包含繼承成員），
僅將 Owner（50）與 Maintainer（40）保留於伺服器記憶體；全部成功才替換名單，失敗保留前次完整名單並記錄警告，不阻擋登入。
兩種查詢的欄位名稱改為「負責人」，僅列出 Project members/all 的 Owner／Maintainer（含群組繼承成員）。
不另外追加 Group Owner／Group Maintainer，避免同一人重複出現；相同角色與顯示名稱去重後排序。
依完整群組／專案路徑分別查詢，單次搜尋同專案只查一次；快取未命中時以管理者 API 查詢。
未設定或無法讀取且沒有快取時顯示「-」。主管快取不改變查詢報告的使用者權限篩選。
參考 [GitLab Project API](https://docs.gitlab.com/api/project_members/) 與 [Group API](https://docs.gitlab.com/api/group_members/)。
專案最上層顯示六欄：名稱、掃描時間、上傳者、套件數量、負責人、下載；明細不重複放下載連結。
群組請使用完整 namespace 路徑（子群組須包含上層），或數字 Group ID；專案可使用數字 ID、完整路徑或相對群組的 project path。

表單登入、登出與網頁上傳已啟用 CSRF；CI 仍沿用原本免登入、免 CSRF 的網路整合端點。
既有 CI 端點必須由反向代理／防火牆限制到受信任的 Jenkins，否則可被用來取代任意專案報告。

## 驗證與套件風險

自動測試使用隔離 H2 資料庫與模擬 GitLab，不會連線實際 SQL Server／GitLab。
驗證涵蓋專案取代、明細清除、失敗回復、Scan ID 衝突、下載與登入權限、0113 同步、
密碼 salt、CSRF、XML 外部實體阻擋、中文 UTF-16 和完整 XML 保存及頁面渲染。
SQL Server 原生 XML 型別、鎖定與遷移仍需於預備環境驗收。

2026-09-29 更新：依需求使用 Spring Boot 3.5.16、Jasypt 4.0.4，移除程式未使用的 CycloneDX core 9.0.0。
另以 BOM 屬性統一更新 Jackson 到 2.21.6、Tomcat 到 10.1.60、Log4j2 到 2.25.5，
修補基準套件版本仍命中的公告。
來源：[Spring Boot 發布紀錄](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)、
[Jackson 公告](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-q4xh-88c3-wmh7)、
[Tomcat 公告](https://tomcat.apache.org/security-10.html)、
[Log4j 公告](https://logging.apache.org/security.html#CVE-2026-49844)。

Spring Boot 3.5.16 是官方 3.5 系列最後的 OSS 版本；本次依指定維持 Boot 3，
以上安全修補版本由專案明確管理。後續仍應持續比對實際依賴，不能將此次結果視為永久保證。

Jasypt 4.0.4 仍命中 **低風險 CVE-2026-9370 / GHSA-jgj7-c8vj-w563**，
涉及 GCM 密碼模式的預設 salt。保留此元件以相容既有 `ENC(...)` 設定，
初始化 admin 使用 JasyptCli 的 AES 模式及隨機 salt／IV，未使用上述 GCM 預設模式。
來源：[上游問題說明](https://github.com/ulisesbocchio/jasypt-spring-boot/issues/431)、
[弱點資料](https://osv.dev/vulnerability/GHSA-jgj7-c8vj-w563)。

重跑執行期套件比對（需可連線 Maven Central 與 OSV）：

```powershell
.\mvnw.cmd "-Dmaven.repo.local=D:\.m2" test dependency:list "-DincludeScope=runtime" "-DoutputFile=target/dependencies.txt"
python scripts/audit_dependencies.py target/dependencies.txt target/osv-audit.json
```

只傳公開套件座標與版本至 OSV；中／高／嚴重或未分類風險會回傳非零狀態，僅有低風險符合此次驗收條件。
OSV 比對是當次資料庫快照，不等同完整弱掃或沒有其他未知弱點的保證。

最終驗證：Spring Boot 3.5.16 下 28 項測試通過，WAR 打包成功；79 個執行期相依套件的 OSV 比對未命中中／高／嚴重或未分類公告，僅兩個 Jasypt 套件命中同一項低風險公告。
可查閱 [相依版本與檢查快照](dependency-audit-2026-09-29.json)。此次未涵蓋 Maven 建置外掛、JDK、作業系統與外部 Servlet 容器。
本次未新增相依套件；另以 `node scripts/test-search-render.cjs` 驗證六欄、單一下載連結及 HTML 跳脫。
GitLab API 使用模擬 HTTP 驗證分頁與路徑編碼；SQL Server 遷移腳本尚未在實際 BOMSDB 執行。


## UI 與本機建置

所有 HTML（含動態產生的查詢表格）已移除 style 屬性、style 區塊與 JavaScript style 設定，
改用 `src/main/resources/static/css` 下的 base、dashboard、login、style、users 五個樣式檔。
下拉箭頭右側間距共用 `--select-arrow-inset: 18.9px`（0.5 cm × 96 ÷ 2.54），
瀏覽器縮放或作業系統顯示縮放會影響實體量測尺寸。

Maven 套件改存 `D:\.m2`，使用 `scripts/build-local.ps1` 建置。
Codex 資料根目錄的移轉方式及界線見 [本機開發資料位置](local-development.md)。
瀏覽器預覽權限未獲允許，本次未完成實際畫面驗證；HTML 與權限行為已由整合測試驗證。

## 權限規則由程式管理

AccountPolicy 定義本機角色 0170／9999、GitLab 角色 0113 與狀態 A／D。
Spring Security 保護路由，UserManagementService 檢核管理員與建立帳號資料，
LocalAuthenticationProvider 驗證密碼、啟用狀態及角色；未知角色／狀態無法本機登入。
DB 僅保存 AuthorityCode、Status、密碼雜湊及稽核資料，不配置角色可使用的功能，
也不透過 CHECK、trigger 或 stored procedure 執行帳號規則。
仍由程式讀取 DB 中目前角色與狀態，以拒絕已撤銷的管理員工作階段。
PK、NOT NULL、欄位長度與報告唯一性／外鍵屬資料完整性限制，予以保留。

既有資料庫請執行 [005_application_account_policy.sql](../sql/005_application_account_policy.sql)。
此腳本只移除先前提供的三個帳號 CHECK，不刪除帳號、不變更密碼或權限代碼。
直接執行 SQL 寫入資料會繞過應用程式輸入檢核；一般帳號建立請使用管理頁面。
