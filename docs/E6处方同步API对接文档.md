# E6 处方数据同步 API 对接文档

版本：`v1.0`  
更新日期：`2026-07-26`  
适用对象：E6 系统开发、实施及联调人员

## 1. 接口用途

E6 系统通过本接口将处方订单数据同步到中药处方加工系统。

接口接收成功后，数据只进入待确认导入池，不会直接生成正式处方。门店管理员在后台核对数据、补充加工方式、加工日期和取货方式并确认后，系统才会生成正式处方和加工计划。

本接口不会连接或修改 E6 数据库。

## 2. 接口地址

```http
POST {API_BASE_URL}/integrations/e6/v1/prescriptions
```

说明：

- `{API_BASE_URL}` 由系统实施人员提供，例如 `https://example.com/api`。
- 测试环境和生产环境使用不同的地址及 API Key。
- 每次请求只同步一张 E6 订单，不支持数组或批量报文。
- 请求及响应编码统一使用 UTF-8。

## 3. 请求鉴权

每个门店使用独立 API Key。API Key 必须放在 HTTP 请求头中：

```http
X-API-Key: e6_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Content-Type: application/json; charset=utf-8
```

请求体中的 `storeCode` 必须与该 API Key 所属门店一致，否则返回 `401`。

安全要求：

- 仅允许通过 HTTPS 调用生产接口。
- 不得把 API Key 放在 URL、请求体或普通业务日志中。
- 不同门店不得共用 API Key。
- API Key 重置后，旧 Key 立即失效。

## 4. 请求字段

| 字段 | 类型 | 必填 | 长度/格式 | 说明 |
|---|---:|:---:|---|---|
| `externalOrderNo` | string | 是 | 1-100 字符 | E6 原始订单号；同一门店内必须唯一且保持不变 |
| `storeCode` | string | 是 | 2-50 字符 | 门店编码，与系统门店管理中的编码一致 |
| `customerName` | string | 否 | 最多 64 字符 | 顾客姓名；可传空字符串 |
| `phone` | string | 否 | 中国大陆手机号 | 顾客手机号；没有时可省略或传空字符串 |
| `e6DoctorCode` | string | 否* | 1-100 字符 | E6 医师编码；系统按门店配置医生映射 |
| `totalPrice` | string/number | 是 | 非负金额，最多 2 位小数 | 订单总价；推荐使用字符串，例如 `"268.00"` |
| `doseCount` | integer | 是 | 大于 0 | 剂数 |
| `remark` | string | 否 | 最多 500 字符 | 处方或订单备注 |
| `sourceCreatedAt` | string | 否 | ISO 8601 | E6 订单创建时间，必须携带时区 |
| `sourceUpdatedAt` | string | 否 | ISO 8601 | E6 订单最后更新时间，必须携带时区 |

门店编码和医师编码由接口统一转换为大写后匹配。E6 端仍应始终传递稳定、格式一致的编码。

`e6DoctorCode` 为空时，服务端仅在该门店恰好配置一条启用中的医师映射时自动使用该映射；其他情况订单会进入“待映射”，由门店管理员确认时选择系统医生。

### 4.1 请求示例

```json
{
  "externalOrderNo": "E6-20260726-000123",
  "storeCode": "SZ001",
  "customerName": "张三",
  "phone": "13800138000",
  "e6DoctorCode": "D001",
  "totalPrice": "268.00",
  "doseCount": 7,
  "remark": "饭后服用",
  "sourceCreatedAt": "2026-07-26T10:22:00+08:00",
  "sourceUpdatedAt": "2026-07-26T10:25:00+08:00"
}
```

## 5. 成功响应

HTTP 状态码：`200 OK`

```json
{
  "code": 0,
  "message": "同步成功",
  "data": {
    "importId": 81,
    "externalOrderNo": "E6-20260726-000123",
    "status": 0,
    "duplicate": false
  }
}
```

响应字段：

| 字段 | 类型 | 说明 |
|---|---:|---|
| `code` | integer | `0` 表示接口已接收并保存数据 |
| `message` | string | 响应说明 |
| `data.importId` | integer | 本系统导入记录 ID，仅用于联调和问题排查 |
| `data.externalOrderNo` | string | E6 原始订单号 |
| `data.status` | integer | 当前导入状态，见下一节 |
| `data.duplicate` | boolean | `true` 表示该订单号此前已经同步过 |

注意：`code = 0` 表示数据已进入导入池，不表示已经生成正式处方。

## 6. 导入状态

所有状态均使用数字：

| 数值 | 状态 | E6 端处理建议 |
|---:|---|---|
| `0` | 待确认 | 同步完成，无需重试，等待门店管理员确认 |
| `1` | 待映射 | 数据已保存；门店尚未配置该医师编码，无需重试 |
| `2` | 导入异常 | 数据已保存，需联系系统管理员处理 |
| `3` | 已生成处方 | 该订单已经转换为正式处方，无需重试 |
| `4` | 已驳回 | 门店管理员已驳回该订单 |
| `5` | 已取消 | 该导入记录已取消 |
| `6` | 数据冲突 | 正式处方生成后，E6 又发送了不同内容，需人工核对 |
| `7` | 处理中 | 系统正在转换数据，稍后可使用相同订单号再次查询式重试 |

`status = 1` 是正常的业务接收结果，不属于接口失败。E6 端不应因为医生未映射而反复发送同一订单。

## 7. 防重复和更新规则

接口以以下组合识别同一张 E6 订单：

```text
storeCode + externalOrderNo
```

规则如下：

1. 第一次同步：创建导入记录，`duplicate = false`。
2. 相同订单、相同内容再次同步：不重复创建，`duplicate = true`。
3. 正式处方生成前内容发生变化：更新导入池中的最新数据，不创建新记录。
4. 正式处方生成后内容发生变化：导入状态变为 `6`，正式处方不会被自动修改。
5. 网络超时或未收到响应时，必须使用相同的 `externalOrderNo` 重试，不能生成新的订单号。

## 8. 错误响应

失败响应格式：

```json
{
  "code": -1,
  "message": "错误信息"
}
```

常见 HTTP 状态码：

| HTTP 状态码 | 说明 | E6 端处理建议 |
|---:|---|---|
| `400` | 请求字段缺失或格式不正确 | 修正数据后再发送，不要无条件重试 |
| `401` | 门店编码、API Key 不正确，或门店 E6 对接未启用 | 检查门店配置和 API Key |
| `429` | 请求频率过高 | 延迟后重试 |
| `500` | 服务端异常 | 保留原订单号并延迟重试；持续失败时联系管理员 |

可能出现的校验信息包括：

```text
请输入E6原始订单号
请输入门店编码
请输入顾客姓名
手机号格式不正确
请输入E6医师编码，或为门店配置唯一的启用医师映射
E6医师编码为空，当前门店有多个启用映射，无法确定医生
总价格式不正确
剂数必须为正整数
E6创建时间格式不正确
E6接入凭证无效
```

E6 端应同时判断 HTTP 状态码和响应体中的 `code`：只有 HTTP `200` 且 `code = 0` 才表示本次同步成功。

## 9. 超时与重试建议

- 建议连接超时设置为 5 秒，请求总超时设置为 15 秒。
- HTTP `400`、`401` 不自动重试，应先修正数据或配置。
- 网络超时、HTTP `429`、HTTP `500` 可重试。
- 推荐重试间隔：1 分钟、5 分钟、15 分钟、30 分钟。
- E6 本地应保存待发送队列和最后一次响应，避免电脑重启后丢失任务。
- 每次重试必须保持 `storeCode` 和 `externalOrderNo` 不变。

## 10. curl 调用示例

```bash
curl --request POST "https://example.com/api/integrations/e6/v1/prescriptions" \
  --header "X-API-Key: e6_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  --header "Content-Type: application/json; charset=utf-8" \
  --data-raw "{\"externalOrderNo\":\"E6-20260726-000123\",\"storeCode\":\"SZ001\",\"customerName\":\"张三\",\"phone\":\"13800138000\",\"e6DoctorCode\":\"D001\",\"totalPrice\":\"268.00\",\"doseCount\":7,\"remark\":\"饭后服用\",\"sourceCreatedAt\":\"2026-07-26T10:22:00+08:00\"}"
```

## 11. C# 调用示例

```csharp
using System.Net.Http.Json;
using System.Text.Json;

var apiBaseUrl = "https://example.com/api";
var apiKey = "e6_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

using var client = new HttpClient
{
    Timeout = TimeSpan.FromSeconds(15)
};

var data = new
{
    externalOrderNo = "E6-20260726-000123",
    storeCode = "SZ001",
    customerName = "张三",
    phone = "13800138000",
    e6DoctorCode = "D001",
    totalPrice = "268.00",
    doseCount = 7,
    remark = "饭后服用",
    sourceCreatedAt = "2026-07-26T10:22:00+08:00"
};

using var request = new HttpRequestMessage(
    HttpMethod.Post,
    $"{apiBaseUrl}/integrations/e6/v1/prescriptions"
);
request.Headers.Add("X-API-Key", apiKey);
request.Content = JsonContent.Create(data);

using var response = await client.SendAsync(request);
var responseText = await response.Content.ReadAsStringAsync();

if (!response.IsSuccessStatusCode)
{
    throw new Exception($"E6同步失败：HTTP {(int)response.StatusCode}，{responseText}");
}

using var json = JsonDocument.Parse(responseText);
var code = json.RootElement.GetProperty("code").GetInt32();
if (code != 0)
{
    throw new Exception($"E6同步失败：{responseText}");
}
```

## 12. 联调检查清单

联调前：

- 确认测试环境完整接口地址。
- 确认门店编码 `storeCode`。
- 获取该门店测试 API Key。
- 提供 E6 医师编码清单，由门店管理员配置医生映射。

联调场景：

1. 正常订单首次同步，返回 `duplicate = false`。
2. 相同订单重复同步，返回 `duplicate = true`，系统没有重复数据。
3. 未映射医师编码同步，返回 `status = 1`。
4. 错误 API Key 调用，返回 HTTP `401`。
5. 缺少必填字段调用，返回 HTTP `400`。
6. E6 模拟网络超时后使用相同订单号重试。
7. 后台确认后再次发送相同内容，返回 `status = 3`。
8. 后台确认后修改内容再次发送，返回 `status = 6`。

## 13. 对接信息确认

| 项目 | 内容 |
|---|---|
| 测试环境 API Base URL | 由实施人员填写 |
| 生产环境 API Base URL | 由实施人员填写 |
| 门店编码 | 由实施人员填写 |
| 测试 API Key | 单独通过安全渠道提供 |
| 生产 API Key | 单独通过安全渠道提供 |
| 接口负责人 | 由双方填写 |
| 联调时间 | 由双方填写 |
