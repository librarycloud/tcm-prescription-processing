# 中药处方加工与取药管理系统 - 用户端

基于 Vue 3 + Vite 的普通用户 Web 端，独立于管理后台 `web-admin` 和微信小程序 `wechat-miniprogram`。

## 技术栈

- Vue 3
- Vite
- JavaScript
- Vue Router
- Pinia
- Axios
- Element Plus
- qrcode
- ESLint
- Prettier

## 安装

```bash
cd web-user
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
- `VITE_DEV_PORT`：开发服务端口，默认 `5174`

## 启动

```bash
npm run dev
```

## 打包

```bash
npm run build
```

## 功能页面

- `/login`：普通用户手机号 + 密码登录
- `/user/packages`：我的包裹
- `/user/packages/:id`：包裹详情和二维码
- `/profile`：个人资料、手机号和密码修改

## 权限说明

- 用户端登录接口：`POST /auth/user-login`
- 管理员账号不能登录用户端
- 用户端登录态使用 `pickup_web_user_` 前缀，与 `web-admin` 隔离
