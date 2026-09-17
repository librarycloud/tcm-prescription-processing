# Prisma 数据库架构与迁移运维指南 (Prisma Migrations SOP)

> [!CAUTION]
> **严正警告**：
> 绝对禁止在已存有正式业务数据的生产服务器上执行 `prisma migrate reset`！该操作会直接 `DROP DATABASE` 清空全部药房处方及库存数据！

---

## 1. 迁移基线与历史架构演进

本项目数据库采用 **Prisma ORM 7** 进行模式定义与迁移管理。由于前期开发迭代产生了大量细粒度的小版本迁移文件，为保证生产环境初始化速度与迁移的整洁性，团队在 Git Commit `2c653bb`（`feat: allow store staff miniprogram access`）进行了**迁移基线重置 (Baseline Reset)**。

- **当前唯一合法基线**：`migrations/00000000000000_baseline/`
  该目录包含自系统创建截至基线提交点的完整 DDL 镜像，包含了全部 50+ 张业务表、外键约束、唯一索引与默认值。
- **历史归档目录**：`migrations_archive_20260824/`
  仅用于历史版本溯源与法律审计。Prisma 引擎已忽略此目录，**切勿**将其中的任何文件复制回 `migrations/` 目录中。

---

## 2. 场景 A：全新空数据库初始化

当你需要搭建一套全新的测试环境、开发环境或为新客户部署全新的独立实例时：

1. 准备一个全新建立的 MariaDB 数据库（字符集推荐 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`）。
2. 在 `backend/.env` 中正确配置目标 `DATABASE_URL`：
   ```env
   DATABASE_URL="mysql://tcm_user:YourPass@127.0.0.1:3306/tcm_db"
   ```
3. 在 `backend/` 目录下依次执行迁移部署与种子数据灌入：
   ```bash
   cd backend
   
   # 1. 自动应用基线及后续所有迁移
   npm run prisma:deploy
   
   # 2. 生成类型声明
   npm run prisma:generate
   
   # 3. 灌入系统预设超管、初始门店及基础字典
   npm run prisma:seed
   ```

---

## 3. 场景 B：存量生产数据库平滑过渡方案

如果目标生产服务器在迁移重置前已经上线，且当前的数据库结构已经与 Git Commit `2c653bb` 保持一致，但其内部的 `_prisma_migrations` 元数据表记录的仍是旧的碎片版本，此时需要平滑对齐元数据：

### 第 1 步：全库物理备份与元数据表快照 (必须执行)

在执行任何变更前，首先通过 `mysqldump` 进行物理备份，并在数据库内部备份元数据表：

```sql
-- 1. 创建元数据备份表并归档旧记录
CREATE TABLE `_prisma_migrations_backup_20260824` LIKE `_prisma_migrations`;
INSERT INTO `_prisma_migrations_backup_20260824` SELECT * FROM `_prisma_migrations`;

-- 2. 清空当前的旧版本迁移痕迹
DELETE FROM `_prisma_migrations`;
```

### 第 2 步：将新的基线版本无损标记为“已应用”

在部署服务器的 `backend/` 目录下执行以下命令：

```bash
# 标记基线迁移为已完成（此操作只在 _prisma_migrations 插入一条已执行记录，绝不会重复执行建表 SQL）
npx prisma migrate resolve --applied 00000000000000_baseline

# 检查当前迁移状态
npx prisma migrate status

# 执行部署以应用基线之后的任何新增迁移
npm run prisma:deploy
```

> [!TIP]
> 执行完毕后，`_prisma_migrations` 表内将以 `00000000000000_baseline` 为唯一根起点，后续开发的所有新迁移（例如新增字段或表）均可直接通过标准的 `npm run prisma:deploy` 自动增量执行。

---

## 4. 日常开发新功能如何新增迁移

当你在本地开发中修改了 `prisma/schema.prisma` 并需要落地到数据库时：

1. 确保本地 MariaDB 开发服务正常运行。
2. 运行开发迁移命令并输入易于理解的迁移描述：
   ```bash
   npx prisma migrate dev --name add_prescription_label_field
   ```
3. Prisma 会自动对比当前 schema 与数据库结构，在 `prisma/migrations/` 下生成一个新的时间戳迁移目录，并自动执行 DDL。
4. 随后务必将新生成的迁移 SQL 连同 `schema.prisma` 一并提交到 Git 仓库。
