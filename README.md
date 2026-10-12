# 中药处方加工与取药管理系统

**TCM Prescription Processing & Pickup Management System**

面向连锁中药房的一体化处方流转、分批加工、智能斗谱、取药通知与核销管理系统。项目采用现代化前后端分离架构，由 Fastify 高性能 API 与 MariaDB 核心数据库提供支撑，统一协同 Web 管理端、普通用户 Web 端、微信小程序、Android 原生药房助手、iOS 原生药房助手以及 Windows E6 ERP 同步服务，实现中药调剂全链路数字化闭环。

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

系统由七大核心工程组成，覆盖 PC 桌面运维、移动端手持作业以及第三方 ERP 自动化同步：

```mermaid
flowchart TD
    E6[(外部 E6 诊所 / 药店库)] -- 只读同步 --> E6Sync[E6Sync 同步工具 Win]
    E6Sync -- HTTP POST API Key --> API

    WebAdmin[Web 管理端 PC<br>Vue 3 + Vite] --> API
    WebUser[Web 用户端 PC<br>Vue 3 + Vite] --> API
    MiniProgram[微信小程序 双端<br>原生 + TDesign] --> API
    Android[Android 药房助手<br>Compose + ML Kit] --> API
    IOS[iOS 药房助手<br>SwiftUI + Vision OCR] --> API

    API((Fastify 后端 API 服务<br>Node.js 24 LTS<br>Prisma ORM 7<br>MariaDB 10.6+ / Redis))
    
    API --> Notif[多渠道通知矩阵<br>短信/邮件/群机器]
    API --> Storage[(本地文件持久化<br>处方附件/调配凭证)]
```

### 模块技术栈清单

| 模块 | 目录 | 技术栈 | 适用场景与终端 |
| --- | --- | --- | --- |
| **后端服务** | `backend/` | Node.js 24 LTS, Fastify 5, Prisma 7, MariaDB, Redis, JWT, bcrypt, Nodemailer, 阿里云/腾讯云/火山短信 SDK, APNs (HTTP/2), 极光 JPush, FCM, ExcelJS | 高性能业务 API、数据校验、权限鉴权、多通道通知（短信/邮件/群机器人/APP 推送）、设备令牌管理、零库存与效期调度器、E6 数据接入池 |
| **管理端 Web** | `web-admin/` | Vue 3, Vite, Pinia, Vue Router, Element Plus, ECharts, Axios, qrcode | 全局及门店管理员：处方多附件管理、加工调度看板、斗谱配置、盘点调拨闭环、热敏打印模板设计、多通道通知设置与系统维护 |
| **用户端 Web** | `web-user/` | Vue 3, Vite, Pinia, Vue Router, Element Plus, Axios, qrcode | 顾客端独立门户：查验处方进度、待取/已取包裹、查看 6 位取货码与二维码凭证 |
| **微信小程序** | `wechat-miniprogram/` | 微信原生小程序, TDesign Miniprogram, ES6 | 双端合一：管理员/员工移动工作台（扫码核销、斗谱查药、加工打卡、调拨确认）+ 顾客查件 |
| **Android 端** | `android-app/` | Kotlin, Jetpack Compose, CameraX, Google ML Kit (离线OCR/条码), ONNX PP-OCR, 极光 JPush, FCM | 门店现场手持终端（药房助手）：离线快速扫码、处方多图平滑缩放/拍照留档、工序流水打卡、私有化服务器配置、远程推送与深度链接 |
| **iOS 端** | `ios-app/` | Swift 5.9+, SwiftUI, Apple Vision OCR, AVFoundation, APNs, UserNotifications, QuickLook | 门店现场 iOS/iPadOS 原生终端（药房助手）：Vision 离线 OCR 中心加权纠偏、iPad Split View 响应式分栏、原生 APNs 推送、触觉反馈与深色模式 |
| **E6 同步工具** | `E6Sync/` | C#, .NET Framework 4.6.2, WinForms, ADO.NET (SqlClient) | Windows Server 运行工具：单向只读提取浪潮 E6 门诊处方与药店库存/货位并上传，全局 WITH (NOLOCK) 高并发查询与来源统计 |

---

## 核心业务流程

系统全生命周期贯穿「处方接入 ➔ 分批计划 ➔ 工序流转 ➔ 包裹生成 ➔ 多渠道通知 ➔ 取货核销 ➔ 自动闭环」：

```mermaid
flowchart TD
    A[处方录入 / E6 同步导入] --> B[管理员审核并确认]
    B --> C[拆分为 1~N 个 ProcessingPlan]
    C --> D[系统预生成唯一 6 位取货码]
    D --> E[加工工作台排队调度 / 加急排序]
    E --> F[调配完成拍照留存凭证 多图/自动旋转]
    F --> G[工序流转: 浸泡 -> 煎煮 -> 打包]
    G --> H[完成加工 -> 决策立即生成或暂缓生成包裹]
    H --> I[Package 沿用 6 位取货码并生成核销二维码]
    I --> J[多渠道通知: 短信 / 邮件 / 群机器人 / APP 原生推送]
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
- **处方全生命周期**：支持门店录入自建处方或外部处方（登记外方机构/外方医生）；支持**多张处方照片及文档附件上传**，提供水平缩略图轮播与全屏无级平滑缩放预览，支持 EXIF 自动校正拍摄朝向；支持按门店、医生、销售员、剂数、总额多维检索。
- **灵活分批计划**：单张处方可自由拆解为多个加工批次（`ProcessingPlan`），各批次独立指定加工工艺（水煎、膏方、颗粒、打粉等）、取货方式（自提/跑腿/快递）、预计完工时间与优先级。
- **加工调度与排队看板**：支持今日待办、逾期任务、加急任务与明日任务快速视图；支持看板任务手动拖拽排序与一键恢复加急默认调度规则。
- **工序流水线与设备监控**：细化支持**浸泡 ➔ 煎煮 ➔ 浓缩 ➔ 打包**等工序打卡；支持录入设备机号并监控工作时长；支持设备故障工序一键转移与异常工序作废；支持调配完成照片上传留存合规凭据；支持完工时**立即生成包裹**与**暂缓生成包裹**（用于汤剂降温澄清或合并打包）灵活决策。

### 2. 智能斗谱与库位可视化
- **多区域库位建模**：支持对门店百子柜（斗架）、大柜、冰箱、仓库等不同存药区域进行参数化建模（抽屉层数、列数、顶层列数可自定义配置）。
- **药材绑定与格内序号**：支持药材快速绑定到具体斗位并设置格内序（如前/后/左/右）；支持一斗多药。
- **智能拼音与多音字检索**：全面升级中药搜索算法，原生支持药名全拼、拼音首字母简码以及**常见中药多音字智能解析**（如茜草、重楼、参类），秒级定位斗架网格坐标。
- **模板化批量迁移与货位维护**：提供标准 Excel 模板，支持斗谱药材全量导出、批量导入与大换斗调迁；支持单独增量同步与筛选商品物理货位。

### 3. 包裹生命周期与取药核销
- **取货码终身绑定**：每个加工批次创建时即固定生成唯一的 6 位数字取货码（展示为 `XXX-XXX`），加工完成自动生成包裹并无缝复用该码，彻底避免单据重印与混淆。
- **全渠道极速核销**：支持扫码枪扫描二维码、手工录入 6 位取货码或移动端摄像头快速扫码；支持自提、同城跑腿和快递配送三种取货方式，快递出库强制核验快递单号。
- **状态联动自愈**：严格防重复核销；多批次处方在最后一个包裹核销完毕后自动将到处方标记为已完成。
- **多端设备会话管理**：支持在线设备会话查验与远程下线踢出，退出登录自动解绑销毁推送令牌，防范隐私泄露。

### 4. 药店商品盘点与库存管理
- **药店商品盘点工作流**：支持新建盘点单、商品候选圈选、初盘录入、复盘比对、系统自动标记异常差异；支持两级审核确认与盘点明细（全部/待复盘/需调整）导出；盘点时支持直接核对与修正商品货位。
- **库存差异台账**：支持商品期初差异登记、手工库存微调、差异核销与撤销审计，支持 Excel 模板导入预检。
- **跨门店调拨全流程闭环**：规范门店间药材与物品借调，覆盖调入申请 ➔ 调出确认出库 ➔ 调入收货 ➔ 调入发起归还 ➔ **调出核实确认归还** 全链路状态流转；新增「待归还确认」状态胶囊与调拨双方门店权限物理隔离。
- **智能库存与效期预警**：
  - `INVENTORY_ZERO`：药店商品正库存降为零或低于安全阈值时即刻触发预警；
  - `EXPIRY_REMINDER`：内置每日定时 Cron 调度器，自动扫描临期药品批号并推送批量预警报告。

### 5. 多通道通知矩阵与标签打印
- **多渠道提货通知集成**：内置阿里云 SMS、腾讯云 SMS、火山引擎 SMS、SMTP 邮件服务，支持动态参数替换与连通性测试。
- **移动端 APP 远程推送 (Push)**：深度集成 iOS 原生 APNs 与 Android 极光 (JPush) / FCM 双通道推送体系；支持 Deep Link 深度链接直接点击直达处方、调拨或包裹详情。
- **通知偏好云端同步**：店员可跨端自主配置接收的通知分类开关（加急处方、包裹上架、库存预警等），数据云端实时同步。
- **Webhook 群机器人**：支持针对各门店或全局配置企业微信、钉钉、飞书群机器人；支持按业务事件（加工完成、逾期提醒、取消异常、零库存等）订阅、异步投递与失败重试。
- **可视化标签设计器**：支持加工单、包装标签、取货标签多模板设计；支持配置纸张尺寸（80×50、60×40 等）、DPI、条码/二维码尺寸与自定义展示字段。

### 6. E6 ERP 综合对接与数据中台
- **双模同步架构**：
  - **诊所处方同步**：接收外部处方入驻待确认池，全面重构支持**销售员号映射**（原医师工号对应 E6 销售员）与操作员工号映射；支持审核池按批次独立指定加工方式与多方合并。
  - **药店库存与商品同步**：增量同步商品零售价、分类映射、条码关联、正库存以及**物理货位编号**。
  - **多单位换算适配**：除克（`g`）外，原生支持盒、袋、包、瓶、支等多种包装单位，自动识别无需拆零。
- **高并发与防重复**：E6 SQL Server 读取语句全局添加 `WITH (NOLOCK)` 避免锁表；增量同步日志记录触发来源统计；采用 `storeCode + externalOrderNo` 联合指纹识别，已生成处方的变更自动拦截并置为冲突状态。

### 7. 移动端专属能力与合规私有化部署
- **双原生移动终端**：Android（Kotlin + Jetpack Compose）与 iOS（SwiftUI + Apple Vision OCR），离线高精度识别药盒 SKU 与条形码，支持中心加权与字符智能容错。
- **iPad Split View 双栏架构**：iOS 针对 iPadOS 进行响应式适配，处方、工序、斗谱、调拨全功能支持分栏侧边栏浏览，大幅提升调配中心台式平板作业效率。
- **局域网私有化部署便捷性**：登录页与个人中心支持扫码或手动录入服务器地址（支持纯局域网 IP:Port），配套标准 HTTP 落地页规避微信内置浏览器拦截自定义 URL Scheme。
- **法律合规与品牌展示**：登录页与关于页底部常驻 ICP 备案号、公司版权信息，并内置《用户协议》与《隐私政策》长文本富文本弹窗查阅。

---

## 文档导航

| 文档名称 | 路径 | 适用群体与说明 |
| --- | --- | --- |
| **系统综合使用手册 (SOP)** | [docs/使用说明.md](docs/使用说明.md) | 全体管理员与操作人员必读：SOP 业务全流程、移动端使用与业务规范 |
| **生产部署与运维手册** | [docs/生产部署与运维手册.md](docs/生产部署与运维手册.md) | 运维/实施工程师：Linux 环境配置、Node 24、MariaDB/Redis 调优、Nginx 反代与 PM2 集群 |
| **常见问题排障手册 (FAQ)** | [docs/常见问题与排障指南(FAQ).md](docs/常见问题与排障指南(FAQ).md) | 药房一线与实施支持：扫码枪/热敏打印机外设排查、E6 连接、短信失败与单据异常处理 |
| **E6 处方同步对接文档** | [docs/E6处方同步API对接文档.md](docs/E6处方同步API对接文档.md) | 外部实施与开发人员：E6 同步接口规范、销售员映射、多包装单位与联调指南 |
| **SeaweedFS 对象存储手册** | [docs/SeaweedFS对象存储部署手册.md](docs/SeaweedFS对象存储部署手册.md) | 运维架构师：高并发处方附件与调剂拍照影像对象存储集群搭建与灾备 |
| **后端核心服务文档** | [backend/README.md](backend/README.md) | 后端开发：Fastify 5 框架、Prisma MariaDB 适配、Redis 锁、推送通知与 RBAC |
| **微信小程序端文档** | [wechat-miniprogram/README.md](wechat-miniprogram/README.md) | 前端开发：双端合一架构、TDesign 组件、Canvas 2D 凭证与页面清单 |
| **管理端前端工程文档** | [web-admin/README.md](web-admin/README.md) | 前端开发：Web 管理端环境配置、通知设置、路由权限守卫与业务组件说明 |
| **用户端前端工程文档** | [web-user/README.md](web-user/README.md) | 前端开发：普通用户查件端配置与独立打包说明 |
| **Android 药房助手说明** | [android-app/README.md](android-app/README.md) | 移动端开发：Kotlin + Compose 构建、扫码引擎、极光/FCM 推送与 CI/CD |
| **iOS 药房助手说明** | [ios-app/README.md](ios-app/README.md) | 移动端开发：SwiftUI 构建、Vision OCR、iPad Split View、APNs 原生推送 |
| **E6Sync 同步客户端说明** | [E6Sync/README.md](E6Sync/README.md) | Windows 部署人员：WinForms .NET 4.6.2 客户端配置、销售员同步与定时规则 |
| **数据库与迁移规范** | [backend/prisma/README.md](backend/prisma/README.md) | 后端运维：Prisma Migrate 迁移指南与数据库升级维护规范 |

---

## 运行环境要求

- **操作系统**：Linux（Ubuntu 22.04+ / Debian 12+ 推荐）、macOS 或 Windows Server
- **Node.js**：`>= 24.0.0`（后端强制要求，推荐 Node.js 24 LTS）
- **包管理器**：`npm >= 10.0.0`
- **数据库**：MariaDB `10.6+` 或 MySQL `8.0+`（字符集 `utf8mb4`）
- **内存缓存**：Redis `6.2+` 或 `7.x`（管理有效登录会话与防重并发控制）
- **Java / Android 环境**（可选，用于 Android 端编译）：JDK 21, Android SDK 36, Gradle 8.10+
- **iOS / macOS 环境**（可选，用于 iOS 端编译）：macOS 14+ (Sonoma/Sequoia), Xcode 15.0+, CocoaPods 1.14+, iOS 15.0+ 部署目标
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

### 4. 移动工作台（小程序、Android 与 iOS）启动

- **微信小程序**：打开微信开发者工具，导入 `wechat-miniprogram` 目录，执行 `npm install` 后点击菜单栏「工具 ➔ 构建 npm」。在 `app.js` 中将 `baseUrl` 配置为局域网 IP（真机预览不可使用 `localhost`）。
- **Android 药房助手**：使用 Android Studio 打开 `android-app` 目录，或在终端执行 `./gradlew assembleDebug`，产物位于 `app/build/outputs/apk/debug/app-debug.apk`。
- **iOS 药房助手**：使用 Xcode 打开 `ios-app/TCMAdmin`，配置开发团队签名后直接运行（推荐 iOS 15+ 真机）。初次运行可在登录页右上角「服务器设置」配置后端 API Base URL。

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

### 1. 认证鉴权与设备会话 (`/auth`, `/app`)
- `POST /auth/login`：管理员/员工用户名密码登录（返回 JWT、角色与登录会话 JTI）
- `POST /auth/user-login`：普通用户手机号快速登录
- `POST /auth/wechat-login`：微信小程序一键授权/登录
- `POST /auth/wechat-bind` / `unbind`：绑定/解绑微信 OpenID
- `POST /auth/logout`：退出登录并自动清理注销当前移动设备推送令牌
- `GET /auth/sessions`：当前账号多端活跃设备会话清单
- `DELETE /auth/sessions/:jti`：远程踢出/吊销指定端登录会话
- `GET /auth/public-configs`：获取系统公开品牌、ICP 备案号、版权声明与服务配置
- `GET /app/legal-docs`：获取系统《用户协议》与《隐私政策》长文本富文本

### 2. 处方与多附件管理 (`/admin/prescriptions`)
- `GET /admin/prescriptions`：处方列表多维筛选（门店、状态、顾客、医生、日期）
- `POST /admin/prescriptions`：新建处方（自建处方或登记外方机构/外院医生）
- `GET /admin/prescriptions/:id`：处方详情、拆分批次与多附件明细
- `PUT /admin/prescriptions/:id`：修改未加工处方基本信息
- `DELETE /admin/prescriptions/:id`：物理删除未排产的草稿处方
- `POST /admin/prescriptions/:id/attachments`：上传处方照片或附件（支持多图同时上传、EXIF 自动校正）
- `GET /admin/prescriptions/:id/attachments/:attachmentId`：获取/下载指定处方附件
- `DELETE /admin/prescriptions/:id/attachments/:attachmentId`：精准删除指定附件

### 3. 加工工作台与工序设备 (`/admin/processing-plans`, `/admin/processing-equipment`)
- `GET /admin/processing-plans`：加工批次列表与实时排队看板
- `GET /admin/processing-plans/calendar`：月度加工日历视图
- `POST /admin/processing-plans` / `POST /admin/processing-plans/batch`：单笔/批量创建加工计划
- `POST /admin/processing-plans/queue/reorder`：看板拖拽调整今日加工排产队列
- `POST /admin/processing-plans/queue/restore`：一键恢复系统默认加急排队调度
- `POST /admin/processing-plans/:id/transition`：加工状态流转（开工、完工、取消、恢复）
- `POST /admin/processing-plans/:id/generate-package`：完工并生成待取包裹（支持暂缓生成后手动补生成）
- `POST /admin/processing-plans/:id/dispensing-photo`：调配抓配完成影像凭据上传
- `POST /admin/processing-plans/:id/equipment-usages/start`：工序开始（绑定机号与批次）
- `POST /admin/processing-plans/:id/equipment-usages/finish`：工序完工打卡
- `POST /admin/processing-plans/:id/equipment-usages/transfer`：设备突发故障工序紧急迁移
- `GET /admin/processing-equipment`：煎药/浓缩设备在线运行状态监控

### 4. 包裹流转与多端核销 (`/admin/packages`)
- `GET /admin/packages`：包裹列表与待取货看板
- `GET /admin/packages/:id`：包裹详情（展示 6 位永久取货码、核销二维码、全生命周期流转时间轴）
- `POST /admin/packages/verify`：**核心核销接口**（支持自提、同城跑腿与快递配送扫码出库）
- `POST /admin/packages/:id/notifications`：手动补发提货短信/通知

### 5. 智能斗谱与库位 (`/admin/herb-locations`)
- `GET /admin/herb-locations`：门店百子柜网格布局与药材分布图
- `GET /admin/herb-locations/layout` / `PUT /layout`：获取/保存门店抽屉层列等物理布局参数
- `POST /admin/herb-locations/assignments`：药材绑定至具体斗位与格内序号（前/后/左/右）
- `POST /admin/herb-locations/import`：Excel 批量导入斗谱数据
- `GET /admin/herb-locations/export`：全量导出当前门店斗谱 Excel
- `POST /admin/herb-locations/import-moves`：导入库位大换斗调迁表格

### 6. 盘点、差异与调拨闭环 (`/admin/yd-goods-checks`, `/admin/product-differences`, `/admin/store-transfers`)
- `GET /admin/yd-goods-checks`：药店商品盘点单列表
- `POST /admin/yd-goods-checks`：圈选范围创建盘点任务
- `POST /admin/yd-goods-checks/:id/initial`：录入初盘数量（支持直接修正货位）
- `POST /admin/yd-goods-checks/:id/recount`：复盘比对并确认账实差异
- `GET /admin/product-differences/stats` / `logs`：商品库存差异台账与调整日志
- `POST /admin/store-transfers`：发起跨门店借调申请
- `POST /admin/store-transfers/:id/confirm-outbound`：调出门店确认出库发运
- `POST /admin/store-transfers/:id/returns`：调入门店登记归还明细
- `POST /admin/store-transfers/:id/returns/:returnId/confirm`：调出门店核实验收并**确认归还（结案闭环）**
- `POST /admin/store-transfers/:id/cancel`：撤销借调单

### 7. 移动端推送与预警调度 (`/admin/device-tokens`, `/admin/system-configs`)
- `GET /admin/device-tokens`：查询已注册设备推送令牌
- `POST /admin/device-tokens`：注册移动端设备推送令牌（支持 APNs / JPush / FCM 与个性化通知偏好）
- `DELETE /admin/device-tokens`：注销删除当前设备推送令牌
- `POST /admin/system-configs/trigger-expiry-scan`：手动立即触发药品效期临期巡检扫描

### 8. E6 ERP 集成 (`/integrations/e6`, `/admin/e6-*`)
- `POST /integrations/e6/v1/prescriptions`：E6 诊所处方单向安全接收（X-API-Key 鉴权）
- `GET /admin/e6-imports`：E6 待确认导入池（支持状态筛选、模糊搜索）
- `POST /admin/e6-imports/:id/confirm`：审核通过并按批次独立指定加工工艺生成正式处方
- `POST /admin/e6-imports/merge`：同顾客多张单据一键合并处方导入
- `GET /admin/e6-pharmacy/products`：E6 药店商品档案与实时正库存列表
- `GET /admin/e6-pharmacy/locations`：E6 药店商品物理货位编号列表

---

## E6 ERP 对接规范

详细报文与联调流程请参阅专属对接指南：[docs/E6处方同步API对接文档.md](docs/E6处方同步API对接文档.md)。

- **同步机制**：接口遵循轻量、幂等、安全的原则，调用方通过 `X-API-Key` 请求头认证；E6 本地采用单向读取（ADO.NET），查询语句全局带 `WITH (NOLOCK)`，杜绝收银排队锁表。
- **销售员与人员映射**：原“医师编码”全面重构为「销售员号」（`salespersonCode`），在系统内可灵活建立销售员与执业医师的映射，支持收银员/操作员映射。
- **多单位支持**：原生识别重量单位（`g`）及包装规格单位（`盒`、`袋`、`包`、`瓶`、`支`），无需强拆单位。
- **状态流转机制**：外部推送进入待审核池（状态：`0=待确认`、`1=待映射`、`2=导入异常`、`3=已生成处方`、`4=已驳回`、`5=已取消`、`6=数据冲突`、`7=处理中`）。
- **防重复机制**：采用 `storeCode + externalOrderNo` 联合指纹识别，已生成处方的单据发生外部二次变更时自动置为冲突保护状态。

---

## 角色与权限体系

系统通过服务端 JWT 与 RBAC 策略执行严密鉴权，角色定义如下：

| 角色标识 (`role`) | 角色名称 | 核心权限与数据范围 |
| :---: | :---: | --- |
| **`0`** | **全局管理员** (SUPER_ADMIN) | 拥有全系统最高权限。可管理所有门店、所有账号、全局基础字典、系统参数、通知渠道密钥、打印模板与全量审计日志。 |
| **`2`** | **门店管理员** (STORE_ADMIN) | 拥有**所辖单个门店**的完全业务权限。可管理本门店的处方、加工调度、包裹核销、斗谱布局、商品盘点、调拨登记、E6导入审核及门店员工账号。 |
| **`3`** | **门店员工** (STORE_STAFF) | 现场一线作业人员。具备加工排队开工、工序打卡、调配拍照、包裹核销、斗谱查药、商品初盘录入与**跨门店调拨全流程（发起申请/调入确认/出库扫码/入库打卡）**；E6 处方导入与库存差异台账具备**只读查阅**权限（无权审批或登记），无法修改系统核心配置。 |
| **`1`** | **普通用户** (USER) | 顾客身份。仅能通过用户端 Web 或小程序查看本人手机号关联的处方进度、待取包裹、6 位取货码及自提二维码凭证。 |

---

## 开源协议与技术支持

本项目仅供授权中药连锁及合作医疗机构使用。如需了解定制化对接或技术支持，请联系项目负责人或提交 Issue。
