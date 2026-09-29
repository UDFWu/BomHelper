# 本機開發資料位置

## Maven：D:\.m2

依此次設定，使用以下 PowerShell 指令建置：

```powershell
.\scripts\build-local.ps1
```

腳本將 Maven 相依套件存放於 `D:\.m2`，Maven Wrapper 本身的下載檔放於
`D:\.m2\wrapper`。使用已安裝的 Maven 時：

```powershell
.\scripts\build-local.ps1 -MavenCommand 'D:\idea\plugins\maven\lib\maven3\bin\mvn.cmd'
```

直接執行 Maven 或 IDE 時，請同樣指定 `-Dmaven.repo.local=D:\.m2`；
IntelliJ IDEA 可將 Maven 的 Local repository 設為 `D:\.m2`。
此設定沒有搬移或刪除 C 槽既有快取，也沒有修改使用者全域 Maven 設定。
可用 `-Repository` 覆寫腳本位置以便其他環境使用。

## Codex：可將 CODEX_HOME 指向 D 槽

官方文件說明 Codex 的設定、歷史與部分日誌／快取位於 `CODEX_HOME`，
預設為 `~/.codex`。因此可將此資料根目錄改為 `D:\codex\home`；
這不代表桌面應用程式所有 AppData／瀏覽器快取都會一起搬移。
來源：[官方 Config and state locations](https://learn.chatgpt.com/docs/config-file/config-advanced#config-and-state-locations)。

目前工作階段仍使用 `C:\Users\d2588\.codex`，本次未搬移使用中的資料或變更其環境變數。
建議在工作結束後依序處理：

1. 完全結束 Codex 桌面程式、CLI 與相關背景工作，避免複製正在寫入的 SQLite／session 資料。
2. 備份原 `.codex`，將其內容複製到新的空資料夾 `D:\codex\home`；保留原目錄作回復用途。
3. 在外部 PowerShell 設定使用者層級的環境變數：

   ```powershell
   [Environment]::SetEnvironmentVariable('CODEX_HOME', 'D:\codex\home', 'User')
   ```

4. 重新登入 Windows 或從已載入新環境變數的程序啟動 Codex，確認設定、登入、任務與外掛正常，
   並確認新增的日誌／session 寫入 D 槽。桌面版若仍使用原位置，應先確認其啟動方式有繼承該環境變數。
5. 驗證完成後再決定是否清理原目錄；不要直接把整個 `.codex` 當成可刪除快取。

外掛安裝另有 `~/.codex/plugins/cache`，其中包含目前執行中的外掛檔案，
不能在使用中直接移走；官方並未在上述文件保證一個單獨的「所有桌面快取路徑」設定。
來源：[官方外掛快取位置](https://developers.openai.com/plugins/build/plugins#how-local-marketplaces-work)。
