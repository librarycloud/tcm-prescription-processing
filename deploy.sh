#!/bin/bash

# 设置：一旦有命令执行失败（返回非 0），整个脚本立即退出，防止带病部署
set -e

echo "======================================================="
echo "🚀 开始部署 中药处方加工与取药管理系统后台更新"
echo "======================================================="

# 1. 拉取并硬重置为最新代码
echo "📦 [1/3] 正在从 GitHub 拉取最新代码并强制重置..."
git fetch origin
git reset --hard origin/main

# 2. 部署后端 (Node.js + Prisma)
echo "⚙️ [2/3] 正在更新并启动后端服务..."
cd backend
echo "  - 安装后端依赖..."
npm install
echo "  - 执行数据库迁移 (Prisma Migrate Deploy)..."
npx prisma migrate deploy
echo "  - 重启后端守护进程 (PM2)..."
# 注意：假设你的后端入口是 src/app.js，且 pm2 命名为 tcm-backend
# 如果这是第一次运行 pm2，它会执行 start，否则执行 restart
pm2 restart tcm-backend || pm2 start src/app.js --name "tcm-backend"
cd ..

# 3. 部署前端 (Vue 3 / Vite)
echo "🖥️ [3/3] 正在构建 Web 管理后台静态资源..."
cd web-admin
echo "  - 安装前端依赖..."
npm install
echo "  - 执行 Vite 打包构建..."
npm run build
cd ..

echo "======================================================="
echo "✅ 部署全部成功完成！当前时间: $(date)"
echo "======================================================="
