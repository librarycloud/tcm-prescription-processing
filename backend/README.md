# Fastify 核心后端服务 (Backend API Engine)

> [!IMPORTANT]
> **运行时环境**：Node.js 24 LTS (ES Module 规范，`"type": "module"`)  
> **核心框架**：Fastify 5.x (超高性能异步 Web 框架)  
> **数据访问**：Prisma ORM 7.x (采用 `@prisma/adapter-mariadb` 原生驱动驱动 MariaDB 10.6+)  
> **缓存与锁**：Redis 5.x+ (验证码防刷、限流、分布式高并发锁)

---

## 1. 架构总览与核心设计

本后端作为中药处方加工与取药管理系统的统一枢纽，为 Web 管理端、Web 用户端、微信小程序双端、Android 药房助手以及 Windows E6Sync 同步工具提供稳定、强类型、高并发的 RESTful API 服务。

```mermaid
flowchart TD
    subgraph Clients[多元化终端接入]
        WebAdmin[Web 管理端 PC]
        WebUser[Web 用户端 H5/PC]
        MiniProg[微信小程序 双端]
        Android[Android 原生助手]
        E6Sync[E6Sync Windows 同步引擎]
    end

    subgraph Security[网关与安全中间件]
        RateLimit["@fastify/rate-limit 限流防护"]
        Helmet["@fastify/helmet 安全响应头"]
        CORS["@fastify/cors 跨域策略"]
        JWTAuth["JWT 鉴权拦截器 & RBAC 角色过滤"]
        ApiKeyAuth["E6 专属 X-API-Key 门店隔离验证"]
    end

    subgraph Core[Fastify 业务控制器]
        AuthCtrl[认证模块: 登录/登出/刷新]
        PrescriptionCtrl[处方全生命周期管理]
        PlanCtrl[加工排产/工序打卡/凭证上传]
        LocationCtrl[斗谱矩阵与药材抽屉管理]
        CheckCtrl[药店商品盘点与库存差异]
        NotifyCtrl[多通道异步通知引擎]
        IntegrationCtrl[E6 数据导入池与防冲突]
    end

    subgraph Persistence[持久化与基础设施]
        Prisma["Prisma ORM 7 (MariaDB Driver Adapter)"]
        MariaDB[(MariaDB 10.6+ 业务主库)]
        Redis[(Redis 缓存/限流/分布式锁)]
        LocalFS[(本地磁盘 / 文件持久化)]
    end

    Clients --> RateLimit
    RateLimit --> Helmet --> CORS
    CORS --> JWTAuth & ApiKeyAuth
    JWTAuth & ApiKeyAuth --> Core
    Core --> Prisma --> MariaDB
    Core --> Redis
    Core --> LocalFS
```

---

## 2. 目录结构说明

```text
backend/
├── prisma/
│   ├── schema.prisma              # 核心数据库模型定义 (50+ 模型实体)
│   ├── seed.js                    # 种子数据初始化脚本 (初始账号、预设字典)
│   ├── migrations/                # Prisma 生产发布迁移版本目录
│   └── README.md                  # 迁移基线与维护指南
├── src/
│   ├── app.js                     # Fastify 应用入口、插件装载与服务启动
│   ├── config/                    # 环境变量解析、常量定义与加解密配置
│   ├── constants/                 # 系统全局枚举 (角色码、状态机码、单据类型)
│   ├── controllers/               # 业务控制器（入参清洗、业务编排、响应格式化）
│   ├── middlewares/               # 中间件（JWT 守卫、角色拦截、请求日志）
│   ├── routes/                    # 路由声明分包
│   │   ├── authRoutes.js          # 统一认证路由
│   │   ├── adminRoutes.js         # 管理员核心业务 API
│   │   ├── userRoutes.js          # C 端用户专属查询 API
│   │   ├── integrationRoutes.js   # E6 第三方数据接入开放 API
│   │   └── releaseRoutes.js       # Android APK 自动更新与分发 API
│   ├── services/                  # 核心服务层（数据库事务、复杂报表统计、打印引擎）
│   └── utils/                     # 辅助库（AES-256-GCM 对称加解密、短信网关、邮件传输）
├── scripts/                       # 自动化运维工具 (Android Release 抓取、存储迁移等)
├── uploads/                       # 本地附件、处方影像、调配照片持久化目录
├── package.json                   # 依赖定义与运行脚本
└── .env                           # 生产/开发环境变量 (禁止提交至 Git)
```

---

## 3. 环境变量配置清单 (`.env`)

在 `backend/` 根目录创建 `.env` 文件，完整参数定义如下：

```env
# ==========================================
# 基础运行环境
# ==========================================
NODE_ENV=development
PORT=3000
HOST=0.0.0.0

# ==========================================
# 数据库连接 (MariaDB 10.6+)
# ==========================================
DATABASE_URL="mysql://tcm_user:YourStrongPassword@127.0.0.1:3306/tcm_db?connection_limit=20&pool_timeout=10"

# ==========================================
# Redis 缓存与限流
# ==========================================
REDIS_URL="redis://:YourRedisPassword@127.0.0.1:6379/0"

# ==========================================
# 鉴权与敏感数据加解密 (必须使用高强度随机字符串)
# ==========================================
JWT_SECRET="e9f1a23c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a"
JWT_EXPIRES_IN="7d"

# AES-256-GCM 主密钥（必须为 32 字节 / 64 字符十六进制串，用于加密第三方密钥及隐私手机号）
ENCRYPTION_KEY="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

# ==========================================
# 本地存储持久化
# ==========================================
UPLOAD_DIR="./uploads"
MAX_FILE_SIZE_MB=20

# ==========================================
# 多渠道通知配置 (可选，按需启用)
# ==========================================
# 邮件 SMTP
SMTP_HOST=smtp.exmail.qq.com
SMTP_PORT=465
SMTP_SECURE=true
SMTP_USER=no-reply@yourdomain.com
SMTP_PASS=YourEmailPassword
SMTP_FROM_NAME="中药处方房管系统"

# 阿里云短信
ALISMS_ACCESS_KEY_ID=
ALISMS_ACCESS_KEY_SECRET=
ALISMS_SIGN_NAME=
ALISMS_TEMPLATE_CODE=

# ==========================================
# GitHub Release 同步 (用于 Android 端无缝发版)
# ==========================================
GITHUB_REPOSITORY="your-org/tcm-prescription-processing"
GITHUB_TOKEN=""
```

---

## 4. 核心脚本与常用命令

所有命令均需在 `backend/` 目录下执行：

| 命令 | 用途与执行说明 |
|---|---|
| `npm run dev` | 以监听模式 (`node --watch`) 启动本地开发服务，代码变更自动热重载。 |
| `npm run start` | 生产环境启动命令。 |
| `npm run prisma:generate` | 依据 `schema.prisma` 重新生成 Prisma 强类型客户端代码。 |
| `npm run prisma:deploy` | 在当前数据库中安全执行所有待落地的迁移脚本（**生产环境专用**，不丢数据）。 |
| `npm run prisma:migrate` | 本地开发环境创建并应用新的数据库变更。 |
| `npm run prisma:seed` | 执行 `prisma/seed.js`，初始化全局超级管理员、测试门店与基础数据字典。 |
| `npm run sync:android-release`| 从 GitHub Release 抓取最新的签名 APK 存入本地版本发布池。 |
| `npm test` | 执行 Node.js 原生单元测试套件 (`node --test`)。 |

---

## 5. 安全性规范与权限体系 (RBAC)

系统内置了严格的四级角色模型，并在 Fastify 路由层注入鉴权装饰器：

| 角色码 (`role`) | 角色名称 | 权限边界与访问范围 |
| :---: | :---: |---|
| **`0`** | **全局管理员** | 拥有系统全量权限。可跨门店查看数据、新增/启停门店、配置 E6 API Key、维护短信/邮件全局通道、管理全局系统审计日志。 |
| **`1`** | **普通用户 (C端)** | 仅能通过 `/user/*` 专属路由凭手机号查询与自身有关的处方与包裹提货码，无法触碰任何管理端接口。 |
| **`2`** | **门店管理员** | 受限于其所在的单一门店。负责本店的处方人工复核审核、加工排产调度、员工账号管理、斗谱调整、盘点复核。 |
| **`3`** | **门店操作员 (员工)**| 移动端/桌面端现场作业。执行工序拍照打卡、扫码提货核销、斗谱查药、商品初盘录入与**跨门店调拨全流程放行**；E6 处方导入与库存差异台账具有**只读查看权限**（无权审批或发起调整变更）。 |

---

## 6. 生产部署与进程守护 (PM2 示例)

在生产服务器（如 Linux Ubuntu / Debian）推荐使用 `pm2` 保持进程高可用常驻：

```bash
cd backend
npm install --omit=dev
npm run prisma:deploy

# 使用 pm2 启动并命名
pm2 start src/app.js --name "tcm-backend" -i max --env production

# 保存开机自启
pm2 save
pm2 startup
```
