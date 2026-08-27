# 中药处方加工与取药管理系统

**TCM Prescription Processing & Pickup Management System**

面向连锁中药房的处方创建、分批加工、取药通知与核销管理系统。项目采用前后端分离架构，管理端、普通用户 Web 端和微信小程序共用 Fastify API 与 MariaDB 数据库；每个加工计划预生成唯一取货码，完成加工后自动生成包裹并复用该取货码。

## 功能概览

### 处方与加工

- **处方管理**：创建、编辑、查询处方；按门店管理客户、医生、剂数、金额、来源与备注。
- **分批加工**：一个处方可拆分为任意数量的 `ProcessingPlan`，每个加工计划独立设置加工方式、日期、优先级和队列顺序。
- **加工工作台**：按今日、明日、待通知、延期等条件管理待加工任务；支持开始加工、完成加工、延期、接收通知、队列排序与恢复。
- **标签打印**：加工计划创建时生成 6 位取货码，支持加工单、包装单和取货标签的二维码及版式配置。
- **领取闭环**：加工完成后自动创建 `Package`，取货时可通过 6 位取货码或二维码核销；全部加工批次领取后，处方自动变为完成状态。

### 包裹、通知与客户服务

- **包裹管理**：包裹检索、详情、二维码、状态、领取时间、核销人和操作审计。
- **通知发送**：支持配置腾讯云、阿里云、火山引擎短信，以及 SMTP 邮件；可查看发送记录并测试配置。
- **群机器人通知**：支持按门店或全局配置群机器人、事件模板、测试发送、失败重试和投递日志。
- **普通用户服务**：用户可通过手机号和密码登录 Web 端或小程序，查看本人待领取/已领取包裹、取货码和个人资料。

### 门店与运营管理

- **多门店与权限**：全局管理员可管理全部数据；门店管理员只能管理所属门店；普通用户只能访问自己手机号关联的包裹。
- **基础资料**：维护门店、门店管理员、医生和字典项；医生、字典和门店采用软删除，保留历史业务引用。
- **药材库位**：维护门店药材、库位布局、药材绑定关系，支持 Excel 模板导入、导出和库位调整导入。
- **商品差异**：维护商品资料、期初差异、库存调整、核销、撤销与 Excel 导入预览。
- **门店调拨**：登记调出、归还、确认出库/归还、预计归还日期与取消记录。
- **E6 对接**：通过 API Key 接收外部处方，先生成导入记录，再由管理员确认生成处方和加工计划；支持门店配置、医生映射与异常重校验。
- **审计与安全**：记录登录日志、操作日志和通知日志；JWT 保存身份与门店范围；短信 Secret、SMTP 密码和机器人密钥采用 AES-256-GCM 加密保存。

## 技术架构

| 模块 | 技术 |
| --- | --- |
| 后端 | Node.js 24 LTS、Fastify、Prisma ORM、MariaDB |
| 管理端 | Vue 3、Vite、Pinia、Vue Router、Axios、Element Plus、ECharts |
| 用户端 | Vue 3、Vite、Pinia、Vue Router、Axios、Element Plus |
| 小程序 | 微信原生小程序、TDesign Miniprogram |
| 认证与安全 | JWT、bcrypt、Helmet、请求限流 |

## 核心业务流程

```text
创建处方
  -> 拆分 ProcessingPlan
  -> 预生成 6 位取货码并可打印加工标签
  -> 在加工工作台排队、开始加工
  -> 加工完成，自动创建 Package 并沿用取货码
  -> READY_PICKUP（待领取）
  -> 短信/邮件/机器人通知客户或门店
  -> 使用二维码或取货码核销
  -> ProcessingPlan = PICKED
  -> 全部批次领取后 Prescription 自动完成
```

## 项目结构

```text
backend/              Fastify API、Prisma schema、迁移和种子数据
web-admin/            管理端 Vue 应用（默认开发端口 5173）
web-user/             普通用户 Vue 应用（默认开发端口 5174）
wechat-miniprogram/   微信小程序
docs/                 E6 等业务对接文档
```

面向管理员和门店操作人员的完整使用教程见：[docs/使用说明.md](docs/使用说明.md)。

## 运行要求

- Node.js `24.x` 或更高版本（后端在 `package.json` 中要求 `>=24.0.0`）
- npm `10+`
- MariaDB `10.6+` 或兼容的 MySQL
- 生产部署建议使用 Linux、Nginx 和 PM2
- 小程序生产环境需要配置 HTTPS 合法域名

## 本地开发

### 1. 创建数据库

```sql
CREATE DATABASE tcm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tcm_user'@'localhost' IDENTIFIED BY 'replace-with-a-strong-password';
GRANT ALL PRIVILEGES ON tcm.* TO 'tcm_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2. 启动后端

```bash
cd backend
npm install
cp .env.example .env
```

编辑 `backend/.env`，至少配置以下值：

```env
DATABASE_URL="mysql://tcm_user:replace-with-a-strong-password@127.0.0.1:3306/tcm"
JWT_SECRET="replace-with-a-long-random-secret"
REDIS_URL="redis://127.0.0.1:6379"
SETTINGS_ENCRYPTION_KEY="replace-with-a-32-byte-base64-key"
PORT=3000
HOST="0.0.0.0"
UPLOAD_DIR="./uploads"
NODE_ENV="development"
```

生成 Prisma Client、初始化数据库和种子数据后启动开发服务：

```bash
npm run prisma:generate
npm run prisma:migrate
npm run prisma:seed
npm run dev
```

健康检查地址：`http://localhost:3000/health`

> MariaDB 使用 Prisma 的 `mysql` provider，因此 `DATABASE_URL` 以 `mysql://` 开头。
> Redis 用于保存有效登录会话；后端启动和已登录接口校验都需要连接 `REDIS_URL`。

### 3. 启动管理端

```bash
cd web-admin
npm install
cp .env.example .env
npm run dev
```

确认 `web-admin/.env` 至少包含以下开发配置：

```env
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://localhost:3000
VITE_DEV_PORT=5173
```

默认访问地址为 `http://localhost:5173`。

### 4. 启动用户端

```bash
cd web-user
npm install
cp .env.example .env
npm run dev
```

确认 `web-user/.env` 至少包含以下开发配置：

```env
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://localhost:3000
VITE_DEV_PORT=5174
```

默认访问地址为 `http://localhost:5174`。

两个前端开发服务器都会将 `/api` 代理到 `VITE_PROXY_TARGET`（默认 `http://localhost:3000`）。

### 5. 运行小程序

使用微信开发者工具导入 `wechat-miniprogram`，安装依赖后执行“工具 -> 构建 npm”。在 `wechat-miniprogram/app.js` 配置接口地址；正式环境必须使用已备案并加入微信合法域名的 HTTPS 地址。

## 生产部署

以下示例以 Ubuntu/Debian、项目目录 `/srv/tcm`、域名 `tcm.example.com` 为例。请替换为实际服务器路径、域名、数据库密码和密钥。

### 1. 安装基础软件

安装 Node.js 24、MariaDB、Nginx 和 PM2。确认版本：

```bash
node -v
npm -v
mariadb --version
```

全局安装 PM2：

```bash
npm install -g pm2
pm2 --version
```

### 2. 获取代码并安装依赖

```bash
git clone git@github.com:librarycloud/tcm-prescription-processing.git /srv/tcm
cd /srv/tcm/backend
npm ci

cd /srv/tcm/web-admin
npm ci

cd /srv/tcm/web-user
npm ci
```

若服务器没有 lockfile 对应的 npm 环境，可将 `npm ci` 改为 `npm install`；生产环境优先使用 `npm ci`，以保证依赖版本可重复。

### 3. 配置生产环境变量和数据库

创建数据库及受限数据库账号后，创建后端配置：

```bash
cd /srv/tcm/backend
cp .env.example .env
```

`backend/.env` 的生产示例：

```env
DATABASE_URL="mysql://tcm_user:strong-db-password@127.0.0.1:3306/tcm"
JWT_SECRET="use-a-long-random-secret-at-least-32-characters"
REDIS_URL="redis://127.0.0.1:6379"
SETTINGS_ENCRYPTION_KEY="use-a-32-byte-base64-key"
PORT=3000
HOST="127.0.0.1"
TRUST_PROXY="127.0.0.1,::1"
IPDB_PATH=""
UPLOAD_DIR="/srv/tcm-data/uploads"
NODE_ENV="production"
```

上传目录需要长期保留并允许后端进程读写，建议放在项目目录之外：

```bash
sudo mkdir -p /srv/tcm-data/uploads
sudo chown -R "$(id -un)":"$(id -gn)" /srv/tcm-data/uploads
```

生成高强度随机值的示例：

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

执行生产迁移。**新数据库**可直接执行；已有数据库迁移历史需要变更时，请先备份并阅读 [Prisma 迁移说明](backend/prisma/README.md)。

```bash
cd /srv/tcm/backend
npm run prisma:generate
npm run prisma:deploy
```

从数据库二进制字段切换到本地存储的首次发布，在迁移成功后执行一次兼容搬迁命令。该命令可重复执行，只处理尚未搬迁的附件和加工照片：

```bash
npm run storage:migrate-local
```

首次部署需要演示账号或基础数据时，再执行一次：

```bash
npm run prisma:seed
```

> 种子数据可能写入初始业务数据。上线前请确认内容符合实际门店，不要在已有生产数据上盲目重复执行。

### 4. 构建前端静态文件

生产环境的前端请求路径应统一为 `/api`，由 Nginx 转发到后端。分别创建 `.env.production`：

```bash
cat > /srv/tcm/web-admin/.env.production <<'EOF'
VITE_API_BASE_URL=/api
EOF

cat > /srv/tcm/web-user/.env.production <<'EOF'
VITE_API_BASE_URL=/api
EOF
```

构建：

```bash
cd /srv/tcm/web-admin
npm run build

cd /srv/tcm/web-user
npm run build
```

构建产物分别位于 `web-admin/dist` 和 `web-user/dist`。建议将管理端部署到 `admin.tcm.example.com`，用户端部署到 `tcm.example.com`；也可按实际需要使用两个独立域名。

### 5. 使用 PM2 后台运行 Node 后端

在项目根目录执行以下命令。PM2 会运行 `backend/package.json` 中的 `start` 脚本，并管理日志、故障重启和进程状态：

```bash
cd /srv/tcm
pm2 start npm --name tcm-backend --cwd /srv/tcm/backend -- start
pm2 status
pm2 logs tcm-backend
```

常用 PM2 运维命令：

```bash
pm2 restart tcm-backend             # 重启服务
pm2 reload tcm-backend --update-env # 平滑重载并读取新的环境变量
pm2 stop tcm-backend                # 停止服务
pm2 delete tcm-backend              # 删除 PM2 进程记录
pm2 logs tcm-backend --lines 200    # 查看最近 200 行日志
pm2 monit                           # 实时查看 CPU 和内存
```

设置开机自启。先执行下列命令，再按终端输出的 `sudo ...` 命令完成系统服务注册，最后保存当前进程列表：

```bash
pm2 startup
pm2 save
```

验证后端：

```bash
curl http://127.0.0.1:3000/health
```

预期返回结构包含：

```json
{ "code": 0, "message": "", "data": { "status": "ok" } }
```

### 6. 配置 Nginx

以下配置将用户端静态文件部署在主域名，管理端部署在 `admin` 子域名，并将两端的 `/api/` 请求转发到本机后端。保存为 `/etc/nginx/sites-available/tcm` 后启用：

```nginx
server {
    listen 80;
    server_name tcm.example.com;

    root /srv/tcm/web-user/dist;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:3000/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 80;
    server_name admin.tcm.example.com;

    root /srv/tcm/web-admin/dist;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:3000/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

启用并检查配置：

```bash
sudo ln -s /etc/nginx/sites-available/tcm /etc/nginx/sites-enabled/tcm
sudo nginx -t
sudo systemctl reload nginx
```

生产环境应为两个域名配置 TLS 证书，并将 80 端口跳转到 HTTPS。后端保持监听 `127.0.0.1:3000`，不应直接暴露到公网。

### 7. 更新发布流程

每次发布前先备份数据库。完成代码更新后，按以下顺序执行：

```bash
cd /srv/tcm
git pull --ff-only

cd backend
npm ci
npm run prisma:generate
npm run prisma:deploy
pm2 reload tcm-backend --update-env

cd ../web-admin
npm ci
npm run build

cd ../web-user
npm ci
npm run build

sudo systemctl reload nginx
```

如果前端构建失败，旧的 `dist` 文件仍会保留；确认新构建成功后再重载 Nginx。

## 备份与排错

### 数据库备份

请在变更迁移、升级版本或批量导入前备份数据库：

```bash
mysqldump -u tcm_user -p --single-transaction --routines --triggers tcm > tcm-$(date +%F).sql
```

恢复前请先在测试环境验证备份文件：

```bash
mariadb -u tcm_user -p tcm < tcm-YYYY-MM-DD.sql
```

处方附件和加工照片存放在 `UPLOAD_DIR`，数据库备份不再包含已搬迁的文件，还需要同步备份上传目录：

```bash
tar -czf tcm-uploads-$(date +%F).tar.gz -C /srv/tcm-data uploads
```

### 常见检查项

```bash
pm2 status
pm2 logs tcm-backend --lines 200
curl http://127.0.0.1:3000/health
sudo nginx -t
sudo systemctl status nginx
```

- API 返回 502：检查 `pm2 status`、后端日志和 `PORT=3000` 是否正常监听。
- 前端刷新后 404：检查 Nginx 的 `try_files $uri $uri/ /index.html` 是否保留。
- 接口路径 404：确认前端生产环境 `VITE_API_BASE_URL=/api`，并确认 Nginx 的 `location /api/` 使用了带结尾 `/` 的 `proxy_pass`。
- 登录或通知配置重启后不可用：检查 `JWT_SECRET`、`REDIS_URL` 和 `SETTINGS_ENCRYPTION_KEY` 是否固定保存；Redis 必须可用，发布时不要更换已有生产密钥。
- 数据库迁移失败：停止发布，保留错误日志，从备份和 [Prisma 迁移说明](backend/prisma/README.md) 核对当前状态后再处理。

## 主要接口

```text
GET    /health

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
GET    /admin/processing-plans/calendar
POST   /admin/processing-plans
POST   /admin/processing-plans/batch
POST   /admin/processing-plans/:id/transition
POST   /admin/processing-plans/:id/generate-package

GET    /admin/packages
GET    /admin/packages/:id
POST   /admin/packages
PUT    /admin/packages/:id
POST   /admin/packages/verify

GET    /admin/doctors
GET    /admin/dictionaries
GET    /admin/login-logs
GET    /admin/operation-logs
```

## E6 数据导入

E6 对接人员请参阅：[E6 处方数据同步 API 对接文档](docs/E6处方同步API对接文档.md)。

E6 通过门店管理中生成的 API Key 调用同步接口。同步只创建 E6 导入记录，管理员确认后才会在同一事务中创建处方和加工计划。升级到 bcrypt 密钥存储后，已有 E6 接入需要在门店管理中重新生成一次 API Key。

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

E6 导入状态：`0` 待确认、`1` 待映射、`2` 导入异常、`3` 已生成处方、`4` 已驳回、`5` 已取消、`6` 数据冲突、`7` 处理中。

统一成功响应：

```json
{ "code": 0, "message": "", "data": {} }
```

统一错误响应：

```json
{ "code": -1, "message": "错误信息" }
```

## 角色说明

```text
0 = 全局管理员
1 = 普通用户
2 = 门店管理员
```
