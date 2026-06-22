# BomHelper

SBOM (Software Bill of Materials) 影響範圍與組成查詢系統。  
解析 CycloneDX 格式的弱點掃描報告，與公司自架 GitLab 整合做使用者驗證與權限控管。

## 功能特色

- **GitLab 登入整合**：透過公司自架 GitLab 帳號密碼登入，後端代為呼叫 GitLab OAuth API 驗證身份
- **權限自動過濾**：根據登入者在 GitLab 上所屬的 Group / Project，自動過濾可查詢的 BOM 報告範圍
- **GitLab Admin 全權限**：GitLab 系統管理員可不受 Group / Project 限制，瀏覽所有報告
- **雙向 SBOM 查詢**：依專案名稱查該專案使用的套件，或依套件名稱查受影響的所有專案
- **網頁手動上傳**：登入後可上傳 CycloneDX XML 報告（僅限自己有權限的 Group）
- **Jenkins CI 上傳**：提供獨立的 CI 端點，Jenkins pipeline 可直接從 workspace 推送報告

## 技術棧

- Java 17
- Spring Boot 3.4.5（Web + Security + Data JPA + Thymeleaf）
- Microsoft SQL Server（正式環境）/ H2（測試）
- CycloneDX core 9.0.0、Jackson XML
- 前端：Thymeleaf + 原生 JavaScript（iframe 切換內容頁）

## 環境準備

1. JDK 17
2. 連線得到公司自架 GitLab Server
3. SQL Server 一台（dev profile 預設指向 `localhost:1433`，DB 名 `BOMSDB`）

## 設定

主要設定檔在 `src/main/resources/application.properties`，請務必修改：

```properties
# 公司自架 GitLab 的 Base URL（無尾斜線）
gitlab.base-url=https://gitlab.example.com

# 內網自簽憑證才需要設 true
gitlab.trust-self-signed=false
```

完整設定一覽：

| Key | 預設值 | 說明 |
|---|---|---|
| `gitlab.base-url` | `https://gitlab.example.com` | GitLab Server URL |
| `gitlab.connect-timeout-ms` | `5000` | HTTP 連線逾時 |
| `gitlab.read-timeout-ms` | `10000` | HTTP 讀取逾時 |
| `gitlab.page-size` | `100` | 同步 Groups/Projects 時每頁筆數 |
| `gitlab.trust-self-signed` | `false` | 是否信任自簽憑證 |

DB 連線設定請看 `application-dev.properties` / `application-stage.properties`。

## 開發者快速啟動

```bash
# 1. 編譯
./mvnw clean compile

# 2. 啟動（預設使用 dev profile）
./mvnw spring-boot:run

# 3. 開瀏覽器
# http://localhost:8080
# 會被導向 /login，請用 GitLab 帳號密碼登入
```

## API 端點

### 一般使用者（需登入）

| Method | Path | 說明 |
|---|---|---|
| `GET` | `/` | 首頁（含側邊欄與 iframe） |
| `GET` | `/search` | SBOM 雙向查詢頁 |
| `GET` | `/upload` | 手動上傳頁 |
| `GET` | `/me` | 取得目前登入者資訊（JSON） |
| `GET` | `/me/groups` | 登入者的 GitLab Groups |
| `GET` | `/me/projects` | 登入者的 GitLab Projects |
| `GET` | `/api/v1/bom/search` | 套用權限過濾的查詢 |
| `POST` | `/api/v1/bom/upload` | 手動上傳（需登入；驗證 Group 權限） |
| `POST` | `/logout` | 登出 |

### CI 整合（免驗證，限內網）

| Method | Path | 說明 |
|---|---|---|
| `GET` | `/api/ci/v1/bom/health` | 健康檢查 |
| `POST` | `/api/ci/v1/bom/upload` | Jenkins pipeline 上傳 BOM 報告 |

---

## Jenkins Pipeline 整合

`POST /api/ci/v1/bom/upload` 提供給 Jenkins 等 CI 工具直接上傳 CycloneDX BOM 報告，不需身份驗證。建議於 nginx / 防火牆做 IP 白名單限制。

### Request 規格

- Content-Type：`multipart/form-data`
- Form 欄位：

| 欄位 | 必填 | 說明 |
|---|---|---|
| `file` | ✅ | CycloneDX BOM XML 檔案 |
| `gitlabGroupId` | ✅ | GitLab Group ID 或 path（例如 `ncbs_mid`） |
| `gitlabProjectId` | ✅ | GitLab Project ID 或 path（蓋過 XML metadata 推斷值） |
| `importedBy` | 選 | 預設 `jenkins`，建議帶 `jenkins:JOB#BUILD` 方便追溯 |

### Response

成功（HTTP 200）：

```json
{
  "success": true,
  "message": "BOM 檔案匯入成功",
  "gitlabGroupId": "12345",
  "gitlabProjectId": "67890",
  "importedBy": "jenkins:my-pipeline#42",
  "fileName": "bom.xml"
}
```

失敗（HTTP 400/500）：

```json
{ "success": false, "message": "此 Scan ID (xxx) 的報告已存在，請勿重複上傳。" }
```

### curl 一行版

```bash
curl -fsS --fail-with-body \
     -X POST "http://bomhelper.internal:8080/api/ci/v1/bom/upload" \
     -F "file=@target/bom.xml" \
     -F "gitlabGroupId=12345" \
     -F "gitlabProjectId=67890" \
     -F "importedBy=jenkins:${JOB_NAME}#${BUILD_NUMBER}"
```

### 完整 Jenkinsfile 範例

把下列檔案放在**呼叫端專案**（產生 BOM 的那個專案）的 repo 根目錄，命名為 `Jenkinsfile`：

```groovy
pipeline {
    agent any

    environment {
        // BomHelper 服務位址（內網）
        BOMHELPER_URL    = 'http://bomhelper.internal:8080'

        // 該專案在 GitLab 上的 Group / Project ID
        GITLAB_GROUP_ID   = '12345'
        GITLAB_PROJECT_ID = '67890'

        // 弱點掃描器輸出的 CycloneDX XML 路徑（相對於 workspace）
        BOM_REPORT_PATH   = 'target/bom.xml'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Build') {
            steps { sh 'mvn -B clean package -DskipTests' }
        }

        stage('Vulnerability Scan') {
            steps {
                // 範例：用 cyclonedx-maven-plugin 產出 BOM
                // 實際弱點掃描指令請依貴公司使用的工具替換
                sh 'mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom'
                sh "ls -la ${BOM_REPORT_PATH}"
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                    curl -fsS "${BOMHELPER_URL}/api/ci/v1/bom/health" \
                         -o /tmp/health.json
                    cat /tmp/health.json
                '''
            }
        }

        stage('Upload BOM to BomHelper') {
            steps {
                script {
                    def importedBy = "jenkins:${env.JOB_NAME}#${env.BUILD_NUMBER}"
                    sh """
                        curl -fsS --fail-with-body \
                             -X POST "${BOMHELPER_URL}/api/ci/v1/bom/upload" \
                             -F "file=@${BOM_REPORT_PATH}" \
                             -F "gitlabGroupId=${GITLAB_GROUP_ID}" \
                             -F "gitlabProjectId=${GITLAB_PROJECT_ID}" \
                             -F "importedBy=${importedBy}" \
                             -o /tmp/bom-upload-response.json
                        echo "=== Upload response ==="
                        cat /tmp/bom-upload-response.json
                    """
                }
            }
        }
    }

    post {
        success {
            echo "✅ BOM 報告已成功上傳到 BomHelper"
        }
        failure {
            echo "❌ Pipeline 失敗，請查看上方日誌"
        }
        always {
            archiveArtifacts artifacts: "${BOM_REPORT_PATH}", allowEmptyArchive: true
            archiveArtifacts artifacts: '/tmp/bom-upload-response.json', allowEmptyArchive: true
        }
    }
}
```

### 安全性提醒

CI 端點 `/api/ci/**` 在 Spring Security 中設定為 `permitAll`，**不做身份驗證**。  
請至少做以下其中一項額外保護：

1. **網段限制**：用 nginx 的 `allow / deny` 或防火牆只允許 Jenkins 主機 IP 對 BomHelper 8080。
2. **共享 API Key**：日後可擴充為 `X-API-Key` 驗證機制。

## 專案結構

```
src/main/java/com/scsb/bomhelper/
├── BomHelperApplication.java
├── config/
│   ├── CurrentUserAdvice.java         全域 model：注入 currentUser
│   ├── GitLabProperties.java          gitlab.* 設定
│   ├── GitLabRestClientConfig.java    RestClient bean（含自簽憑證支援）
│   └── SecurityConfig.java            Spring Security filter chain
├── controller/
│   ├── AuthController.java            /login、/me、/me/groups、/me/projects
│   ├── BomController.java             /api/v1/bom/*（需登入）
│   ├── CiBomController.java           /api/ci/v1/bom/*（CI 用）
│   └── PageController.java            首頁 + iframe 子頁
├── dto/
│   ├── gitlab/                        GitLab API DTOs
│   └── *.java                         CycloneDX XML DTOs
├── entity/                            JPA Entities
├── repository/                        Spring Data JPA repositories
├── security/
│   ├── GitLabAuthenticationProvider   呼叫 GitLab 驗證身份
│   └── GitLabUserPrincipal            session 中的使用者主體
└── service/
    ├── BomImportService               解析 XML 並寫入 DB
    └── GitLabService                  GitLab API client
```

## 注意事項 / FAQ

**Q: 我用帳號密碼登入失敗？**  
A: 公司 GitLab 可能停用了 `grant_type=password`，請在 GitLab 管理介面開啟 OAuth Password 模式；若帳號啟用 2FA 也會無法用密碼登入。

**Q: 登入成功但首頁中間顯示「localhost 拒絕連線」？**  
A: Spring Security 預設 `X-Frame-Options: DENY` 會擋掉 iframe，本專案已改為 `SAMEORIGIN`，請確保套用最新程式碼並重啟。

**Q: SBOM 雙向查詢查不到資料？**  
A: 後端 INFO log 會印出 `[Search] user=..., groupIds=[...], projectIds=[...]`，請對照資料庫中 `BomReport.GitlabGroupId` / `GitlabProjectId` 的值，確認您所屬的 Group/Project 識別碼有對上。
