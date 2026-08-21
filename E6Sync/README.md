# E6Sync

`E6Sync.exe` 是面向浪潮佳软 E6 的 Windows WinForms 处方同步工具。它只读 SQL Server 2008 中的 E6 新零售收款台和处方明细数据，并逐张发送到 E6 同步 API；不会写入、修改或删除 E6 数据库内容。

## 编译

1. 在 Visual Studio 2022 安装“`.NET 桌面开发`”工作负载，以及 `.NET Framework 4.6.2 Targeting Pack`。
2. 打开 `E6Sync.sln`。
3. 在工具栏选择 `Release` 和 `Any CPU`，执行“生成 -> 生成解决方案”。
4. 可执行文件输出在 `E6Sync\\bin\\Release\\E6Sync.exe`。

项目不使用 NuGet 包、Node.js、IIS、SQLite 或 WebView。引用全部来自 .NET Framework 4.6.2：`System.Data.SqlClient`、`System.Net.Http` 和 `System.Web.Extensions`。

## 编译文件与部署

将以下文件复制到 Windows Server 的同一个目录：

- `E6Sync.exe`
- `E6Sync.exe.config`（若生成）
- `config.json`

程序首次启动会自动创建 `logs\\sync.log`。请确保运行帐户对应用目录有创建/写入文件的权限。

目标框架是 `.NET Framework 4.6.2`。部署前请确认 Windows Server 已安装对应运行时。Windows Server 2008 R2 SP1 支持 .NET Framework 4.6.2；原始 Windows Server 2008 的支持和所需补丁取决于具体版本及系统更新状态，应先在目标服务器验证安装条件。

## 配置

部署目录中的 `config.json` 示例：

```json
{
  "e6": {
    "server": "127.0.0.1",
    "database": "E6观前街中医诊所",
    "windowsAuthentication": true,
    "username": "",
    "password": "",
    "defaultDoctorCode": "D001"
  },
  "api": {
    "baseUrl": "https://example.com/api",
    "apiKey": "e6_实际密钥",
    "storeCode": "SZ001"
  },
  "sync": {
    "intervalSeconds": 60,
    "autoSyncEnabled": true,
    "lastSyncTime": ""
  }
}
```

医生/药师映射由后端配置。E6Sync 会将 E6 的 `处方药师`原始值放入 API 的 `e6DoctorCode` 字段，由后端按其配置进行映射。若 E6 的 `处方药师`为空，请将 `defaultDoctorCode` 设置为服务端“E6医师映射”中已配置的编码（如 `D001`）；程序仅在原始值为空时使用该默认值。旧版 `config.json` 中若仍有 `doctorMappings`，程序会忽略它。

SQL Server 2008 使用 Windows 身份验证：请将 `windowsAuthentication` 设为 `true`，并保持 `username` 和 `password` 为空。程序会以启动 `E6Sync.exe` 的 Windows 帐户连接 SQL Server；请先为该帐户授予目标数据库的只读权限。所有配置更新（自动同步保存 `lastSyncTime`）都会先写临时文件再替换原文件。不要把 API Key 提交到源代码库；程序日志也不会写入敏感值。

## 运行与测试

启动程序后先点击“测试 SQL 连接”。该操作只打开和关闭 SQL 连接，不执行任何 SQL 写操作。

API 文档未定义健康检查或测试接口，因此程序不会构造虚假订单测试 API。选择一段包含已知订单的日期，点击“开始同步”进行实际联调：

1. 开始和结束日期均按自然日处理。选择同一天会查询当天 `00:00:00` 至次日 `00:00:00`。
2. 每张订单单独 POST 到 `/integrations/e6/v1/prescriptions`，请求包含 `X-API-Key`。
3. 只有 HTTP `200` 且 JSON `code` 为 `0` 计为成功。`duplicate=true` 计为“重复”而不是失败。
4. GUI 与 `logs\\sync.log` 会记录 API 导入状态：待确认、待映射、导入异常、已生成处方、已驳回、已取消、数据冲突或处理中。

程序启动后自动定时器运行。首次 `lastSyncTime` 为空时，程序不会自动全量查询，只会提示先执行一次手动同步。手动同步整轮没有失败时，程序会将 `lastSyncTime` 更新为同步完成时刻；存在失败或任务取消时不会更新。正常自动同步会查询 `lastSyncTime - 2 分钟` 到当前时间；只有整轮没有失败，才会将 `lastSyncTime` 前移。失败后会在下一轮重新查询，API 的 `storeCode + externalOrderNo` 幂等规则可处理边界重复。

关闭主窗口会隐藏到系统托盘。主窗口的“启用自动同步”复选框和托盘菜单可随时暂停或恢复自动同步，设置会保存到 `sync.autoSyncEnabled`，下次启动仍然生效。托盘菜单提供“打开”“立即同步”“暂停自动同步”和“退出”。自动任务与手动任务由单一锁串行执行。

## 已知字段边界

同步以 `PF新零售收款台_处方明细` 为主表，再按明细表 `PID` 关联 `PF新零售收款台.id`，取得购药人、购药人电话、处方药师、处方备注及操作员；不读取处方登记。没有处方明细的收费不会被查询或上传，`_proofstate=作废` 的单据也会过滤。同步日期和订单时间按同一单据 ID 关联 `AC款台_零售收款记录`，取该单据最后一条收款记录的操作日期；金额使用 `PF新零售收款台.收款金额`。主表 `id` -> `externalOrderNo`，`_proofstate=结单` 标记为已付款，其他状态标记为未付款。明细表 `PID` 对应订单号，`付数`逐行上传为 `items[].doseCount`（管理端显示为剂数），`ri` 为中药顺序，`商品名称`为中药名；`单付数量`为单剂数量，按 `单付数量 * 单位` 换算为克后上传到 `items[].quantity`，不乘付数；`数量`按 `数量 * 单位` 换算为克后上传到 `items[].totalQuantity`。仅接受 `10g`、`10克`、`1g`、`1克`、`克`。时间会按 `yyyy-MM-dd HH:mm:ss` 读取，忽略末尾毫秒。

程序会将零售收款记录的最后操作日期同时作为订单时间和更新时间发送。E6 数据中的“购药人”可为空，订单仍可同步后由后台确认时补充；处方备注超过 500 字符时会自动截断。
