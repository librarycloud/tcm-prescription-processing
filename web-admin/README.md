# Web 管理端 (Web Admin)

基于 Vue 3 + Vite 的企业级药房业务管理中台前端项目，面向全局管理员、门店管理员及门店员工，提供包括处方、加工排产、库存盘点、智能斗谱及系统配置在内的全功能桌面端作业环境。

---

## 技术栈

- **核心框架**：Vue 3 (Composition API, `<script setup>`)
- **构建工具**：Vite
- **路由与状态**：Vue Router, Pinia
- **UI 组件库**：Element Plus
- **图表与可视化**：ECharts
- **网络与工具**：Axios, qrcode (用于前端直接渲染凭证与热敏打印标签)
- **工程化**：ESLint, Prettier

---

## 目录结构

```text
src/
├── api/          # Axios 实例封装及所有后端 RESTful API 路由模块
├── assets/       # 静态资源（Logo、插画、SVG 空状态图等）
├── components/   # 全局及业务公共组件（如状态标签、数据字典下拉框）
├── layouts/      # 后台管理系统骨架（侧边栏菜单、顶栏、面包屑、内容区）
├── router/       # 前端路由定义与导航守卫（权限拦截、动态重定向）
├── stores/       # Pinia 状态树（登录凭据、用户信息、当前选中的门店隔离上下文）
├── styles/       # 全局样式、Element Plus 主题覆盖变量、过渡动画
├── utils/        # 工具库（日期格式化、金额防抖、防重复提交、打印驱动辅助等）
└── views/        # 页面视图组件
    ├── login/    # 管理员登录
    ├── profile/  # 账号个人资料与密码修改
    └── admin/    # 后台核心功能页面模块 (见下方清单)
```

---

## 功能模块清单 (`src/views/admin/`)

当前 Web 管理端已完整实现以下核心业务的 PC 操作界面：

- **大盘概览** (`Dashboard.vue`)：处方、加工单、包裹核心指标看板与 ECharts 趋势图。
- **处方与外方库** (`Prescriptions.vue`, `PrescriptionDetail.vue`)：处方建档、附件图文管理、拆分加工计划。
- **加工工作台** (`ProcessingPlans.vue`)：拖拽式排队调度、工序流水线管理、调配照片凭证核查、流转生成包裹。
- **加工设备管控** (`ProcessingEquipment.vue`)：煎药机/打粉机设备定义、状态监控与故障时转移指派。
- **智能斗谱网格** (`HerbLocations.vue`)：百子柜与大柜抽屉矩阵二维可视化、药材位置绑定与批量调迁。
- **包裹与取药核销** (`Packages.vue`, `Verify.vue`)：条码/二维码扫码核销出库、物流单号强关联录入。
- **药店商品盘点** (`YdGoodsChecks.vue`)：药店库存初盘录入、差异筛查、复盘与两级审核。
- **库存差异台账** (`ProductDifferences.vue`)：调增调减登记、期初数据初始化、核销撤销审计。
- **门店调拨** (`StoreTransfers.vue`)：跨店借调出入库确认与逾期监控。
- **E6 处方导入审核** (`E6Imports.vue`)：外部 E6 数据认领、医师映射、单据合并、转正式处方。
- **打印标签设计器** (`PrintTemplates.vue`)：可视化拖拽调整热敏纸标签尺寸与内容展示项。
- **消息推送配置**：短信集成 (`SmsSettings.vue`)、邮件配置 (`EmailSettings.vue`)、群机器人 (`RobotNotifications.vue`)。
- **基础与账号权限**：门店管理 (`Stores.vue`)、基础字典 (`BasicData.vue`)、员工账号 (`StoreAdmins.vue`)、普通用户 (`Users.vue`)。
- **审计与运维**：移动端版本发布管理 (`AppReleases.vue`)、登录与操作审计 (`LoginLogs.vue`, `OperationLogs.vue`)。

---

## 开发指南

### 1. 依赖安装

```bash
cd web-admin
npm install
```

### 2. 环境变量配置

复制环境变量文件：

```bash
cp .env.example .env
```

请确保 `.env` 中的以下核心变量满足要求：

```env
# 接口请求路径前缀（通常不修改）
VITE_API_BASE_URL=/api

# Vite 开发服务器代理目标（应指向本地后端启动地址）
VITE_PROXY_TARGET=http://127.0.0.1:3000

# Web 开发服务器端口
VITE_DEV_PORT=5173
```

### 3. 本地启动

```bash
npm run dev
```

成功后访问 `http://localhost:5173`。开发环境下 Vite 会自动将前端发出的 `/api/*` 请求通过反向代理转发至 Node.js 宿主服务，彻底解决跨域并模拟生产环境路径。

### 4. 生产构建打包

```bash
npm run build
```

产物将生成至 `dist/` 目录下，交由 Nginx 等 Web 服务器进行静态托管及 `/api/` 反向代理。

### 5. 代码质量检查与格式化

提交代码前推荐执行格式化校验：

```bash
npm run lint
npm run format
```

---

## 登录鉴权与路由隔离说明

1. 业务使用 `POST /auth/login` 接口进行鉴权，后端下发 JWT Token。
2. Axios 拦截器 (`src/api/request.js`) 会自动为所有请求挂载 `Authorization: Bearer <token>` 请求头。
3. Vue Router 全局前置守卫 (`src/router/index.js`) 拦截机制：
   - 强校验目标页面是否需要鉴权，若无 Token 则重定向至 `/login`。
   - 依据用户信息中的 `role` 屏蔽越权菜单，全局管理员（`role === 0`）与门店管理员（`role === 2`）、门店员工（`role === 3`）在侧边栏看到的菜单范围不同。
   - **数据域隔离**：门店管理员仅能在各页面看到自己所在门店的数据。
