# 中药处方加工与取药管理系统 - 管理端

基于 Vue 3 + Vite 的企业后台前端项目，独立于微信小程序目录和普通用户 Web 端，用于对接现有 Node.js/Fastify 后端。

## 技术栈

- Vue 3
- Vite
- JavaScript
- Vue Router
- Pinia
- Axios
- Element Plus
- qrcode
- ECharts
- ESLint
- Prettier

## 安装

```bash
cd web-admin
npm install
```

## 环境变量

复制环境变量示例：

```bash
cp .env.example .env
```

变量说明：

- `VITE_API_BASE_URL`：前端请求基础路径，开发环境默认 `/api`
- `VITE_PROXY_TARGET`：Vite 开发代理目标，默认 `http://localhost:3000`
- `VITE_DEV_PORT`：开发服务端口，默认 `5173`

## 启动

```bash
npm run dev
```

开发环境会将 `/api` 代理到后端服务，后端接口路径保持 `/auth/login`、`/admin/packages` 等原始形式。

## 打包

```bash
npm run build
```

## 预览

```bash
npm run preview
```

## 代码检查与格式化

```bash
npm run lint
npm run format
```

## 目录说明

```text
src/
  api/          Axios 请求封装与接口模块
  assets/       Logo、头像、空状态图
  components/   公共业务组件
  layouts/      后台统一布局
  router/       Vue Router 路由与权限守卫
  stores/       Pinia 状态管理
  styles/       全局样式与主题变量
  utils/        日期、手机号、二维码、权限、状态等工具
  views/        登录、管理端、用户端、个人资料、404 页面
```

## 登录与权限

- 登录接口：`POST /auth/login`
- 登录成功后保存 JWT 与用户信息
- Axios 自动携带 `Authorization: Bearer <token>`
- `role === 0` 或 `role === 2` 可以进入管理后台
- `role === 0` 可管理全部门店和系统设置，`role === 2` 仅能管理所属门店包裹
- 普通用户 Web 端请使用 `web-user`
- 路由守卫会拦截未登录、越权访问和已登录用户重复进入登录页

## 功能页面

管理员：

- 概览：统计卡片与最近 7 天新增趋势
- 包裹列表：分页、搜索、状态筛选、排序、刷新、编辑、核销
- 新增包裹
- 包裹详情：完整字段、二维码、取货码、修改审计信息
- 编辑包裹：修改物品名称、备注、收件人、手机号
- 核销：输入 6 位数字取货码或扫码核销

用户：

- 我的包裹
- 包裹详情：二维码、取货码、物品信息、状态、取货时间、核销人
- 个人资料：默认只读，点击修改后可更新昵称、手机号和密码
