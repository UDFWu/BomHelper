# admin 的 Jasypt 密碼

新建 admin 執行 [004](../sql/004_bootstrap_admin.sql)。已有 admin 則先執行
[005](../sql/005_application_account_policy.sql) 移除舊 CHECK，再執行
[006](../sql/006_admin_jasypt_password.sql)，將密碼重設為 12345678。
006 不更改角色或啟用狀態。本次未直接執行實際 DB SQL。

啟動應用程式前設定：

```powershell
$env:JASYPT_ENCRYPTOR_PASSWORD='BomHelper'
java -jar target/sbomHelper.war
```

SQL 儲存完整 ENC(...) 字串；使用 PBEWITHHMACSHA512ANDAES_256、1000 次迭代、
RandomSaltGenerator 與 RandomIvGenerator，與 JasyptCli 相同。
Jasypt 為可逆加密，持有金鑰即可解密；正式程式設定未內建預設金鑰。
若同時指定 jasypt.encryptor.password，請與環境變數保持一致。
此金鑰也供既有應用程式 ENC(...) 設定解密，部署時須確認那些設定使用相同金鑰。

登入相容既有 PBKDF2 帳號，管理頁面新建帳號仍使用 PBKDF2。
驗證包含正確密碼登入，以及錯誤密碼、缺少金鑰與錯誤金鑰拒絕登入。
