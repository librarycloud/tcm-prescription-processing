# 中药处方加工与取药管理系统

**TCM Prescription Processing & Pickup Management System**

面向连锁中药房的处方、分批加工、待领取和取药核销系统。系统采用前后端分离与模块化设计，复用统一的 Package 取货码、二维码和核销流程。

## 技术架构

- 后端：Node.js 24 LTS、Fastify、Prisma ORM、MariaDB
- 管理端与用户端：Vue 3、Vite、Pinia、Vue Router、Axios、Element Plus
- 小程序：微信原生小程序、TDesign Miniprogram
- 认证：JWT、bcrypt
- 后端分层：Controller、Service、Repository/Prisma、Permission
- 管理端适配 PC 浏览器与平板设备

## 业务模块

- Dashboard：首页统计、当前门店信息与加工工作台入口
- 处方管理：建方、修改、查看、拆分加工批次和完成状态计算
- 加工工作台：统一调度加工日期、状态、优先级和队列顺序
- 待领取：显示 `READY_PICKUP` 的加工计划及其 Package
- 包裹管理：取货码、二维码、通知、核销和领取记录
- 系统管理：门店、门店管理员、基础资料、登录日志、操作日志和系统设置
- 个人中心：当前用户资料维护

基础资料包括 Doctors、Dictionary 和 Stores，所有门店共享。处方来源、加工方式和提醒方式均通过 Dictionary 维护，不在业务代码中写死。

业务数据包括 Prescriptions、ProcessingPlan、Packages 和 LoginLogs，统一使用 `storeId` 隔离。全局管理员可查看全部门店；门店管理员只能操作 JWT 中 `storeId` 对应的数据；普通用户只能查看自己手机号对应的 Package。

## 核心流程

```text
创建处方
  -> 拆分 ProcessingPlan
  -> 为每个加工计划生成 6 位取货码，可立即打印取货标签
  -> 进入加工工作台
  -> 开始加工
  -> 加工完成
  -> 自动创建 Package 并沿用加工计划取货码
  -> READY_PICKUP
  -> 二维码/取货码核销
  -> ProcessingPlan = PICKED
  -> 所有批次领取后 Prescription 自动完成
```

一个处方可关联任意数量的 ProcessingPlan，每个加工计划最多关联一个 Package。加工工作台的今日、明日、等待通知及延期均由 `scheduleType`、`processDate`、`status`、`priority` 和 `queueOrder` 动态查询，不建立额外业务表。

## 数据与安全

- JWT 保存 `id`、`role`、`storeId` 和 `phone`
- Service 层通过 PermissionService 生成门店数据范围
- 门店管理员提交的 `storeId` 会被后端忽略，业务数据自动继承当前门店
- Doctors、Dictionary、Stores 使用软删除并保留历史引用
- 业务与基础资料记录创建人、修改人和时间
- 重要操作写入 OperationLogs
- 禁用门店后，其门店管理员不能登录或新增业务
- 短信 Secret 与 SMTP 密码使用 AES-256-GCM 加密保存

## 目录结构

```text
wechat-pickup-system/
  backend/              Fastify API、Prisma schema 与迁移
  web-admin/            管理端 Web 应用
  web-user/             普通用户 Web 应用
  wechat-miniprogram/   微信小程序
```

## 本地运行

创建 MariaDB 数据库：

```sql
CREATE DATABASE wechat_pickup DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

后端：

```bash
cd backend
npm install
cp .env.example .env
npm run prisma:generate
npm run prisma:migrate
npm run prisma:seed
npm run dev
```

主要环境变量：

```env
DATABASE_URL="mysql://root:password@127.0.0.1:3306/wechat_pickup"
JWT_SECRET="replace-with-a-long-random-secret"
E6_API_KEY_HASH_SECRET="replace-with-another-long-random-secret"
SETTINGS_ENCRYPTION_KEY="replace-with-a-long-random-secret"
PORT=3000
HOST="0.0.0.0"
```

`E6_API_KEY_HASH_SECRET` 用于 E6 接入密钥的服务端 HMAC。生产环境请配置独立的随机值，并在升级后为已有 E6 接入重新生成密钥。

MariaDB 使用兼容的 Prisma `mysql` provider，因此连接字符串仍以 `mysql://` 开头。

管理端：

```bash
cd web-admin
npm install
npm run dev
```

用户端：

```bash
cd web-user
npm install
npm run dev
```

小程序通过微信开发者工具导入 `wechat-miniprogram`，安装依赖后执行“工具 -> 构建 npm”。接口地址在 `wechat-miniprogram/app.js` 配置，生产环境必须使用 HTTPS 合法域名。

## 主要接口

```text
POST   /auth/login
POST   /auth/user-login
POST   /auth/wechat-login
POST   /auth/wechat-bind

POST   /integrations/e6/v1/prescriptions

GET    /admin/stats
GET    /admin/prescriptions
POST   /admin/prescriptions
PUT    /admin/prescriptions/:id
DELETE /admin/prescriptions/:id

GET    /admin/processing-plans
POST   /admin/processing-plans
PUT    /admin/processing-plans/:id
POST   /admin/processing-plans/:id/transition
DELETE /admin/processing-plans/:id

GET    /admin/packages
GET    /admin/packages/:id
POST   /admin/packages
PUT    /admin/packages/:id
POST   /admin/packages/verify

GET    /admin/doctors
GET    /admin/dictionaries
GET    /admin/login-logs
GET    /admin/operation-logs

GET    /admin/e6/imports
GET    /admin/e6/imports/:id
POST   /admin/e6/imports/:id/confirm
POST   /admin/e6/imports/:id/reject
POST   /admin/e6/imports/:id/revalidate
GET    /admin/e6/stores/:storeId/config
PUT    /admin/e6/stores/:storeId/config
GET    /admin/e6/doctor-mappings
POST   /admin/e6/doctor-mappings
PUT    /admin/e6/doctor-mappings/:id
DELETE /admin/e6/doctor-mappings/:id
```

### E6 数据导入

E6 对接人员请参阅：[E6 处方数据同步 API 对接文档](docs/E6处方同步API对接文档.md)。

E6 通过门店管理中生成的 API Key 调用同步接口。同步只创建 E6 导入记录，管理员确认后才会在同一事务中创建处方和加工计划。

```http
POST /integrations/e6/v1/prescriptions
X-API-Key: e6_xxx
Content-Type: application/json
```

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
  "sourceCreatedAt": "2026-07-26T10:22:00+08:00"
}
```

E6 导入状态全部使用数字：`0` 待确认、`1` 待映射、`2` 导入异常、`3` 已生成处方、`4` 已驳回、`5` 已取消、`6` 数据冲突、`7` 处理中。

统一成功响应：

```json
{ "code": 0, "message": "", "data": {} }
```

统一错误响应：

```json
{ "code": -1, "message": "错误信息" }
```

## 角色

```text
0 = 全局管理员
1 = 普通用户
2 = 门店管理员
```

Package 继续独立负责最终领取和核销。ProcessingPlan 在创建时预生成取货码，以支持加工前打印取货标签；生成 Package 时沿用该取货码。
