# 中药处方加工与取药管理系统

**TCM Prescription Processing & Pickup Management System**

面向连锁中药房的一体化处方流转、分批加工、智能斗谱、取药通知与核销管理系统。项目采用现代化前后端分离架构，由 Fastify 高性能 API 与 MariaDB 核心数据库提供支撑，统一协同 Web 管理端、普通用户 Web 端、微信小程序、Android 原生药房助手以及 Windows E6 ERP 同步服务，实现中药调剂全链路数字化闭环。

---

## 目录

- [终端生态与技术架构](#终端生态与技术架构)
- [核心业务流程](#核心业务流程)
- [系统功能矩阵](#系统功能矩阵)
- [文档导航](#文档导航)
- [运行环境要求](#运行环境要求)
- [快速开始（本地开发）](#快速开始本地开发)
- [生产部署规范](#生产部署规范)
- [运维备份与排错](#运维备份与排错)
- [核心 API 清单](#核心-api-清单)
- [E6 ERP 对接规范](#e6-erp-对接规范)
- [角色与权限体系](#角色与权限体系)

---

## 终端生态与技术架构

系统由六大核心工程组成，覆盖 PC 桌面运维、移动端手持作业以及第三方 ERP 自动化同步：

```text
                                  ┌──────────────────────────────┐
                                  │      外部 E6 诊所 / 药店库      │
                                  └──────────────┬───────────────┘
                                                 │ (只读同步)
                                                 ▼
                                  ┌──────────────────────────────┐
                                  │    E6Sync 同步工具 (Win)      │
                                  └──────────────┬───────────────┘
                                                 │ HTTP POST (API Key)
                                                 ▼
┌──────────────────┐              ┌──────────────────────────────┐              ┌──────────────────┐
│ Web 管理端 (PC)  │              │                              │              │ 微信小程序 (双端)  │
│ Vue 3 + Vite     ├─────────────►│                              │◄─────────────┤ 原生 + TDesign   │
└──────────────────┘              │                              │              └──────────────────┘
                                  │   Fastify 后端 API 服务      │
┌──────────────────┐              │   - Node.js 24 LTS           │              ┌──────────────────┐
│ Web 用户端 (PC)  ├─────────────►│   - Prisma ORM 7             │◄─────────────┤ Android 药房助手 │
│ Vue 3 + Vite     │              │   - MariaDB 10.6+ / Redis    │              │ Compose + ML Kit │
└──────────────────┘              │   - AES-256-GCM / JWT        │              └──────────────────┘
                                  └───────┬──────────────┬───────┘
                                          │              │
                                          ▼              ▼
                          ┌──────────────────┐  ┌──────────────────┐
                          │ 多渠道通知矩阵   │  │ 本地文件持久化   │
                          │ 短信/邮件/群机器 │  │ 处方附件/调配凭证│
                          └──────────────────┘  └──────────────────┘
```

### 模块技术栈清单

| 模块 | 目录 | 技术栈 | 适用场景与终端 |
| --- | --- | --- | --- |
| **后端服务** | `backend/` | Node.js 24 LTS, Fastify 5, Prisma 7, MariaDB, Redis, JWT, bcrypt, Nodemailer, 阿里云/腾讯云/火山短信 SDK, ExcelJS | 高性能业务 API、数据校验、权限鉴权、异步通知队列、E6 数据接入池 |
| **管理端 Web** | `web-admin/` | Vue 3, Vite, Pinia, Vue Router, Element Plus, ECharts, Axios, qrcode | 全局及门店管理员：处方管理、加工调度、斗谱配置、盘点调拨、打印模板、系统设置 |
| **用户端 Web** | `web-user/` | Vue 3, Vite, Pinia, Vue Router, Element Plus, Axios, qrcode | 顾客端独立门户：查验处方进度、待取/已取包裹、查看 6 位取货码与二维码凭证 |
| **微信小程序** | `wechat-miniprogram/` | 微信原生小程序, TDesign Miniprogram, ES6 | 双端合一：管理员/员工移动工作台（扫码核销、斗谱查药、加工打卡）+ 顾客查件 |
| **Android 端** | `android-app/` | Kotlin, Jetpack Compose, CameraX, Google ML Kit (离线OCR/条码), ONNX PP-OCR | 门店现场专用手持终端（药房助手）：离线快速扫码、调配拍照留档、工序流转 |
| **E6 同步工具** | `E6Sync/` | C#, .NET Framework 4.6.2, WinForms, ADO.NET (SqlClient) | Windows Server 运行工具：单向只读提取浪潮 E6 诊所处方及药店商品库存并上传 |

---

## 核心业务流程

系统全生命周期贯穿「处方接入 ➔ 分批计划 ➔ 工序流转 ➔ 包裹生成 ➔ 多渠道通知 ➔ 取货核销 ➔ 自动闭环」：

```mermaid
flowchart TD
    A[处方录入 / E6 同步导入] --> B[管理员审核并确认]
    B --> C[拆分为 1~N 个 ProcessingPlan]
    C --> D[系统预生成唯一 6 位取货码]
    D --> E[加工工作台排队调度 / 加急排序]
    E --> F[调配完成拍照留存凭证]
    F --> G[工序流转: 浸泡 -> 煎煮 -> 打包]
    G --> H[完成加工 -> 自动生成 Package]
    H --> I[Package 沿用 6 位取货码并生成二维码]
    I --> J[多渠道通知: 短信 / 邮件 / 企微钉钉飞书机器人]
    J --> K{顾客取货方式}
    K -->|到店自提| L[核验 6 位取货码 / 扫二维码]
    K -->|同城跑腿| M[核对跑腿配送信息并核销]
    K -->|快递物流| N[扫描/补录快递单号完成出库]
    L --> O[包裹状态变更为 PICKED]
    M --> O
    N --> O
    O --> P{该处方所有批次均已领取?}
    P -->|是| Q[Prescription 自动完结归档]
    P -->|否| R[保持进行中，等待其他批次]
```

---

## 系统功能矩阵

### 1. 处方全流程与加工工作台
- **处方全生命周期**：支持门店录入自建处方或外部处方（登记外方机构/外方医生），支持图片/文档等多附件上传与在线预览；支持按门店、医生、剂数、总额多维检索。
- **灵活分批计划**：单张处方可自由拆解为多个加工批次（`ProcessingPlan`），各批次独立指定加工方式（水煎、膏方、颗粒等）、取货方式（自提/跑腿/快递）、预计完工时间与优先级。
- **加工调度与排队看板**：支持今日待办、逾期任务、加急任务与明日任务快速视图；支持看板任务手动拖拽排序与一键恢复默认调度。
- **工序流水线与设备监控**：细化支持**浸泡 ➔ 煎煮 ➔ 浓缩 ➔ 打包**等工序打卡；支持录入设备机号并监控工作时长；支持设备故障工序一键转移与异常工序作废；支持调配完成照片上传留存合规凭据。

### 2. 智能斗谱与库位可视化
- **多区域库位建模**：支持对门店百子柜（斗架）、大柜、冰箱、仓库等不同存药区域进行参数化建模（抽屉层数、列数、顶层列数可自定义配置）。
- **药材绑定与格内序号**：支持药材快速绑定到具体斗位并设置格内序（如前/后/左/右）；支持按药材名、拼音简码、编码与物理货位快速检索。
- **模板化批量迁移**：提供标准 Excel 模板，支持斗谱药材全量导出、批量导入，以及跨货位调迁的一键导入调整。

### 3. 包裹生命周期与取药核销
- **取货码终身绑定**：每个加工批次创建时即固定生成唯一的 6 位数字取货码（展示为 `XXX-XXX`），加工完成自动生成包裹并无缝复用该码，彻底避免单据重印与混淆。
- **全渠道极速核销**：支持扫码枪扫描二维码、手工录入 6 位取货码或移动端摄像头快速扫码；支持自提、同城跑腿和快递配送三种取货方式，快递出库强制核验快递单号。
- **状态联动自愈**：严格防重复核销；多批次处方在最后一个包裹核销完毕后自动将到处方标记为已完成。

### 4. 药店商品盘点与库存管理
- **药店商品盘点工作流**：支持新建盘点单、商品候选圈选、初盘录入、复盘比对、系统自动标记异常差异；支持两级审核确认与盘点明细（全部/待复盘/需调整）导出。
- **库存差异台账**：支持商品期初差异登记、手工库存微调、差异核销与撤销审计，支持 Excel 模板导入预检。
- **跨门店借调管理**：规范门店间药材与物品借调，记录调出门店、调入门店、借调数量、预计归还日期；支持出库确认、部分归还、全量归还、逾期监控与撤销。

### 5. 多通道通知矩阵与标签打印
- **全通道通知集成**：内置阿里云 SMS、腾讯云 SMS、火山引擎 SMS、SMTP 邮件服务，支持动态参数替换与连通性测试。
- **Webhook 群机器人**：支持针对各门店或全局配置企业微信、钉钉、飞书群机器人；支持按业务事件（加工完成、逾期提醒、取消异常等）订阅、异步投递与失败重试。
- **可视化标签设计器**：支持加工单、包装标签、取货标签多模板设计；支持配置纸张尺寸（80×50、60×40 等）、DPI、条码/二维码尺寸与自定义展示字段。

### 6. E6 ERP 综合对接与数据中台
- **双模同步架构**：
  - **诊所处方同步**：接收外部处方入驻待确认池，提供医师映射、操作员映射、冲突检测与异常重校验，由管理员审查后批量一键生成处方。
  - **药店库存与商品同步**：增量同步商品零售价、分类映射、条码关联与正库存数据。
- **防重复机制**：采用 `storeCode + externalOrderNo` 联合指纹识别，已生成处方的变更自动拦截并置为冲突状态。

---

## 文档导航

| 文档名称 | 路径 | 适用群体与说明 |
| --- | --- | --- |
| **系统综合使用手册** | [docs/使用说明.md](docs/使用说明.md) | 全体管理员与操作人员必读：SOP 业务全流程、移动端使用与详尽 FAQ |
| **E6 处方同步对接文档** | [docs/E6处方同步API对接文档.md](docs/E6处方同步API对接文档.md) | 外部实施与开发人员：E6 同步接口规范、鉴权、报文定义与联调指南 |
| **管理端前端工程文档** | [web-admin/README.md](web-admin/README.md) | 前端开发：Web 管理端环境配置、路由规范与组件说明 |
| **用户端前端工程文档** | [web-user/README.md](web-user/README.md) | 前端开发：普通用户查件端配置与独立打包说明 |
| **Android 药房助手说明** | [android-app/README.md](android-app/README.md) | 移动端开发：Kotlin + Compose 构建、扫码引擎、Keystore 签名与 CI/CD |
| **E6Sync 同步客户端说明** | [E6Sync/README.md](E6Sync/README.md) | Windows 部署人员：WinForms .NET 4.6.2 客户端配置与定时同步规则 |
| **数据库与迁移规范** | [backend/prisma/README.md](backend/prisma/README.md) | 后端运维：Prisma Migrate 迁移指南与数据库升级维护规范 |

---

## 运行环境要求

- **操作系统**：Linux（Ubuntu 22.04+ / Debian 12+ 推荐）、macOS 或 Windows Server
- **Node.js**：`>= 24.0.0`（后端强制要求，推荐 Node.js 24 LTS）
- **包管理器**：`npm >= 10.0.0`
- **数据库**：MariaDB `10.6+` 或 MySQL `8.0+`（字符集 `utf8mb4`）
- **内存缓存**：Redis `6.2+` 或 `7.x`（管理有效登录会话与防重并发控制）
- **Java / Android 环境**（可选，用于 Android 端编译）：JDK 21, Android SDK 36, Gradle 8.10+
- **.NET 环境**（可选，用于编译 E6Sync）：.NET Framework 4.6.2 Targeting Pack, Visual Studio 2022

---

## 快速开始（本地开发）

### 1. 准备本地基础组件

确认本地 MariaDB 与 Redis 服务已启动。在数据库中创建库及用户：

```sql
CREATE DATABASE tcm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tcm_user'@'localhost' IDENTIFIED BY 'tcm_dev_password';
GRANT ALL PRIVILEGES ON tcm.* TO 'tcm_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2. 启动后端 API 服务

```bash
cd backend
npm install
cp .env.example .env
```

编辑 `backend/.env`，基础开发项配置如下：

```env
DATABASE_URL="mysql://tcm_user:tcm_dev_password@127.0.0.1:3306/tcm"
JWT_SECRET="dev-jwt-secret-at-least-32-characters-long"
REDIS_URL="redis://127.0.0.1:6379"
SETTINGS_ENCRYPTION_KEY="dev_32_byte_base64_encryption_key_here="
PORT=3000
HOST="0.0.0.0"
UPLOAD_DIR="./uploads"
NODE_ENV="development"
```

> **提示**：可使用 `node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"` 生成合规的 32 字节 Base64 加密密钥。

执行数据库初始化并启动服务：

```bash
npm run prisma:generate      # 生成 Prisma Client
npm run prisma:migrate       # 执行数据表迁移
npm run prisma:seed          # 写入初始种子账号与测试门店
npm run dev                  # 启动热重载开发服务器
```

访问后端健康检查验证：`http://localhost:3000/health`。

### 3. 启动前端各端

开启新的终端窗口，分别按需启动前端应用：

```bash
# 启动管理端 Web (默认端口: 5173)
cd web-admin && npm install && npm run dev

# 启动用户端 Web (默认端口: 5174)
cd web-user && npm install && npm run dev
```

### 4. 微信小程序与 Android 启动

- **微信小程序**：打开微信开发者工具，导入 `wechat-miniprogram` 目录，执行 `npm install` 后点击菜单栏「工具 ➔ 构建 npm」。在 `app.js` 中将 `baseUrl` 配置为局域网 IP（真机预览不可使用 `localhost`）。
- **Android 药房助手**：使用 Android Studio 打开 `android-app` 目录，或在终端执行 `./gradlew assembleDebug`，产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 5. 初始默认账号

| 角色类型 | 登录账号（手机号） | 默认密码 | 登录入口 | 权限范围 |
| --- | --- | --- | --- | --- |
| **全局管理员** | `13800000000` | `123456` | Web 管理端 / 小程序管理员入口 / Android 端 | 系统全部功能、所有门店数据与全局系统配置 |
| **门店管理员** | 见系统内创建 | 创建时指定 | Web 管理端 / 小程序管理员入口 / Android 端 | 仅限所绑定的单家门店全部业务数据 |
| **门店员工** | 见系统内创建 | 创建时指定 | Web 管理端 / 小程序管理员入口 / Android 端 | 仅限所绑定的单家门店现场加工与核销业务 |
| **普通用户** | 用户手机号 | 注册或指定 | Web 用户端 / 小程序普通用户入口 | 仅限查询自身绑定的处方与包裹进度 |

> [!WARNING]
> 首次部署上线后，请立即在个人资料或管理后台修改默认管理员密码，切勿在生产环境使用默认密码。

---

## 生产部署规范

以下以 Linux 服务器（项目路径 `/srv/tcm`，管理端域名 `admin.tcm.example.com`，用户端域名 `tcm.example.com`）为例。

### 1. 安装系统依赖与 PM2

```bash
sudo apt update && sudo apt install -y curl git nginx mariadb-server redis-server
# 安装 Node.js 24 LTS
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
sudo apt install -y nodejs
sudo npm install -g pm2
```

### 2. 拉取代码并安全安装依赖

```bash
git clone https://github.com/librarycloud/tcm-prescription-processing.git /srv/tcm
cd /srv/tcm/backend && npm ci
cd /srv/tcm/web-admin && npm ci
cd /srv/tcm/web-user && npm ci
```

### 3. 配置生产环境变量与持久化目录

创建独立持久化上传目录并赋予权限：

```bash
sudo mkdir -p /srv/tcm-data/uploads
sudo chown -R $(whoami):$(whoami) /srv/tcm-data/uploads
```

配置 `backend/.env`（生产示例）：

```env
DATABASE_URL="mysql://tcm_user:RealStrongPassword@127.0.0.1:3306/tcm"
JWT_SECRET="Produce-High-Entropy-Random-String-At-Least-32-Chars"
REDIS_URL="redis://127.0.0.1:6379"
SETTINGS_ENCRYPTION_KEY="Production-32-Byte-Base64-Key="
PORT=3000
HOST="127.0.0.1"
TRUST_PROXY="127.0.0.1,::1"
UPLOAD_DIR="/srv/tcm-data/uploads"
NODE_ENV="production"
```

执行生产数据库迁移：

```bash
cd /srv/tcm/backend
npm run prisma:generate
npm run prisma:deploy        # 生产环境仅使用 deploy，禁止使用 migrate dev
npm run storage:migrate-local # 兼容性迁移早期数据库中的存量附件
```

### 4. 构建前端静态资源

```bash
# 构建管理端
cd /srv/tcm/web-admin
echo "VITE_API_BASE_URL=/api" > .env.production
npm run build

# 构建用户端
cd /srv/tcm/web-user
echo "VITE_API_BASE_URL=/api" > .env.production
npm run build
```

### 5. 使用 PM2 守护后端进程

```bash
cd /srv/tcm
pm2 start npm --name "tcm-backend" --cwd /srv/tcm/backend -- start
pm2 save
pm2 startup
```

常用运维命令：

```bash
pm2 status                           # 查看服务运行状态
pm2 logs tcm-backend --lines 100     # 查看最近 100 行日志
pm2 reload tcm-backend --update-env  # 零停机重载并加载新环境变量
```

### 6. Nginx 生产反向代理配置

编辑 `/etc/nginx/sites-available/tcm`：

```nginx
# 1. 普通用户端门户
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
        client_max_body_size 30M;
        proxy_read_timeout 180s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

# 2. 管理端控制台
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
        client_max_body_size 30M;
        proxy_read_timeout 180s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

启用站点并配置 SSL：

```bash
sudo ln -s /etc/nginx/sites-available/tcm /etc/nginx/sites-enabled/tcm
sudo nginx -t && sudo systemctl reload nginx
# 推荐使用 certbot 获取免费 Let's Encrypt HTTPS 证书
sudo certbot --nginx -d tcm.example.com -d admin.tcm.example.com
```

---

## 运维备份与排错

### 1. 定期备份方案

**数据库结构与数据定时备份**：

```bash
mysqldump -u tcm_user -p --single-transaction --routines --triggers tcm > /srv/backup/tcm-$(date +%F).sql
```

**附件与凭证图片持久化目录备份**：

```bash
tar -czf /srv/backup/tcm-uploads-$(date +%F).tar.gz -C /srv/tcm-data uploads
```

### 2. 高频故障诊断指南

| 现象 | 可能原因 | 排查与修复步骤 |
| --- | --- | --- |
| **API 接口返回 502 Bad Gateway** | 后端服务未运行或异常崩溃 | 执行 `pm2 status` 确认进程状态，执行 `pm2 logs tcm-backend` 排查错误栈，核查 MariaDB/Redis 连接是否正常。 |
| **前端 SPA 页面刷新出现 404** | Nginx 未配置前端路由回退 | 检查 Nginx 配置文件中对应的 `location /` 是否包含 `try_files $uri $uri/ /index.html;`。 |
| **API 路径返回 404 Not Found** | Nginx 反代缺少结尾斜杠 | 确保 Nginx `proxy_pass http://127.0.0.1:3000/;` 包含结尾斜线，以便正确剥离前端发送的 `/api` 前缀。 |
| **重启后管理员已配置的通知密钥失效** | `SETTINGS_ENCRYPTION_KEY` 发生了变动 | 敏感配置采用该密钥进行 AES-256-GCM 密文存储，一旦密钥变更将无法解密。确保 `.env` 中的密钥持久固定。 |
| **登录提示“无权访问”或不断掉线** | Redis 停止运行或内存溢出 | 后端依赖 Redis 校验有效登录凭据。确认 `redis-cli ping` 返回 `PONG`。 |

---

## 核心 API 清单

所有业务接口均带有统一状态码格式响应：`{ "code": 0, "message": "", "data": ... }`。

### 1. 认证鉴权 (`/auth`)
- `POST /auth/login`：管理员/员工用户名密码登录（返回 JWT 与角色）
- `POST /auth/user-login`：普通用户手机号登录
- `POST /auth/wechat-login`：微信小程序一键授权/登录
- `POST /auth/wechat-bind`：绑定微信 OpenID

### 2. 处方管理 (`/admin/prescriptions`)
- `GET /admin/prescriptions`：处方列表查询（支持门店、状态、顾客、医生、日期筛选）
- `POST /admin/prescriptions`：新建处方（自建或外方）
- `GET /admin/prescriptions/:id`：处方详情及附件关联
- `PUT /admin/prescriptions/:id`：修改未加工处方
- `DELETE /admin/prescriptions/:id`：删除未排产处方
- `POST /admin/prescriptions/:id/attachments`：上传处方照片或附件
- `DELETE /admin/prescriptions/:id/attachments/:attachmentId`：删除指定附件

### 3. 加工工作台与工序设备 (`/admin/processing-plans`, `/admin/processing-equipment`)
- `GET /admin/processing-plans`：加工计划列表与看板
- `GET /admin/processing-plans/calendar`：加工日历视图
- `POST /admin/processing-plans` / `POST /admin/processing-plans/batch`：单笔/批量创建加工计划
- `POST /admin/processing-plans/queue/reorder`：拖拽调整今日加工队列
- `POST /admin/processing-plans/queue/restore`：恢复系统默认排队顺序
- `POST /admin/processing-plans/:id/transition`：加工状态流转（开工、完工、取消、恢复）
- `POST /admin/processing-plans/:id/generate-package`：完工并手动生成待取包裹
- `POST /admin/processing-plans/:id/dispensing-photo`：调配完成凭证拍照上传
- `POST /admin/processing-plans/:id/equipment-usages/start`：工序开始（绑定机号与批次）
- `POST /admin/processing-plans/:id/equipment-usages/finish`：工序完成
- `POST /admin/processing-plans/:id/equipment-usages/transfer`：设备故障紧急转移
- `GET /admin/processing-equipment`：加工设备状态监控列表

### 4. 包裹流转与核销 (`/admin/packages`)
- `GET /admin/packages`：包裹列表与待取看板
- `GET /admin/packages/:id`：包裹详情（二维码、取货码、流转时间轴）
- `POST /admin/packages/verify`：**取货核销核心接口**（通过 6 位取货码或二维码核验，区分自提/跑腿/快递出库）
- `POST /admin/packages/:id/notifications`：手动重发取药通知

### 5. 智能斗谱与库位 (`/admin/herb-locations`)
- `GET /admin/herb-locations`：门店斗谱网格与药材明细
- `GET /admin/herb-locations/layout`：获取门店百子柜/大柜物理布局参数
- `PUT /admin/herb-locations/layout`：保存门店物理布局配置
- `POST /admin/herb-locations/assignments`：药材绑定到货位
- `POST /admin/herb-locations/import`：Excel 批量导入斗谱数据
- `GET /admin/herb-locations/export`：导出当前门店斗谱 Excel
- `POST /admin/herb-locations/import-moves`：导入库位调迁表格

### 6. 盘点、差异与调拨 (`/admin/yd-goods-checks`, `/admin/product-differences`, `/admin/store-transfers`)
- `GET /admin/yd-goods-checks`：药店商品盘点单列表
- `POST /admin/yd-goods-checks`：创建新盘点任务
- `POST /admin/yd-goods-checks/:id/initial`：录入初盘数量
- `POST /admin/yd-goods-checks/:id/recount`：录入复盘数据并确认差异
- `GET /admin/product-differences/stats` / `logs`：商品库存差异台账与调整日志
- `POST /admin/store-transfers`：发起门店间调拨申请
- `POST /admin/store-transfers/:id/confirm-outbound`：调出门店确认出库
- `POST /admin/store-transfers/:id/confirm-return`：调入门店确认归还

### 7. E6 ERP 集成 (`/integrations/e6`, `/admin/e6-*`)
- `POST /integrations/e6/v1/prescriptions`：E6 诊所处方单向同步接收（X-API-Key 鉴权）
- `GET /admin/e6-imports`：E6 导入处方审核池
- `POST /admin/e6-imports/:id/confirm`：审核通过并生成正式处方与加工计划
- `POST /admin/e6-imports/merge`：合并相同顾客的 E6 处方导入单
- `GET /admin/e6-pharmacy/products`：E6 药店商品与实时正库存列表

---

## E6 ERP 对接规范

详细报文与联调流程请参阅专属对接指南：[docs/E6处方同步API对接文档.md](docs/E6处方同步API对接文档.md)。

- **同步机制**：接口遵循轻量、幂等、安全的原则，调用方通过 `X-API-Key` 请求头认证。
- **状态流转机制**：外部推送进入待审核池（状态：`0=待确认`、`1=待映射`、`2=导入异常`、`3=已生成处方`、`4=已驳回`、`5=已取消`、`6=数据冲突`、`7=处理中`）。
- **字段规范**：支持订单主表与多行明细（`items`），中药剂数统一换算，重量单位规范归一化为 `g`。

---

## 角色与权限体系

系统通过服务端 JWT 与 RBAC 策略执行严密鉴权，角色定义如下：

| 角色标识 (`role`) | 角色名称 | 核心权限与数据范围 |
| :---: | :---: | --- |
| **`0`** | **全局管理员** (SUPER_ADMIN) | 拥有全系统最高权限。可管理所有门店、所有账号、全局基础字典、系统参数、通知渠道密钥、打印模板与全量审计日志。 |
| **`2`** | **门店管理员** (STORE_ADMIN) | 拥有**所辖单个门店**的完全业务权限。可管理本门店的处方、加工调度、包裹核销、斗谱布局、商品盘点、调拨登记、E6导入审核及门店员工账号。 |
| **`3`** | **门店员工** (STORE_STAFF) | 现场操作人员。仅具备所辖门店的基础日常作业权限（加工排队开工、工序打卡、调配拍照、包裹打包核销、斗谱查药等），无法修改系统配置或删除核心数据。 |
| **`1`** | **普通用户** (USER) | 顾客身份。仅能通过用户端 Web 或小程序查看本人手机号关联的处方进度、待取包裹、6 位取货码及自提二维码凭证。 |

---

## 开源协议与技术支持

本项目仅供授权中药连锁及合作医疗机构使用。如需了解定制化对接或技术支持，请联系项目负责人或提交 Issue。
