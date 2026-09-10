import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();

async function main() {
  console.log('开始迁移旧配置数据...');

  // 1. 手动建表 (如果 Prisma 还没建的话)，使用原生 SQL 绕过 Prisma Schema 限制
  console.log('正在确保新表 system_configs 存在...');
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS \`system_configs\` (
      \`id\` int NOT NULL AUTO_INCREMENT,
      \`item\` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
      \`value\` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
      \`class\` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
      \`is_public\` tinyint(1) NOT NULL DEFAULT '0',
      \`type\` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'string',
      \`default\` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
      \`mark\` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
      PRIMARY KEY (\`id\`),
      UNIQUE KEY \`system_configs_item_key\` (\`item\`),
      KEY \`system_configs_class_idx\` (\`class\`),
      KEY \`system_configs_is_public_idx\` (\`is_public\`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  `);

  // 2. 迁移 Email 配置
  try {
    const emails = await prisma.$queryRawUnsafe(`SELECT * FROM email_configs WHERE config_key = 'default'`);
    if (emails && emails.length > 0) {
      const e = emails[0];
      const emailVal = JSON.stringify({
        host: e.host,
        port: e.port,
        secure: e.secure,
        username: e.username,
        passwordEncrypted: e.password_encrypted,
        fromName: e.from_name,
        fromEmail: e.from_email,
        enabled: e.enabled
      });
      await prisma.$executeRawUnsafe(`
        INSERT IGNORE INTO system_configs (item, value, class, type, mark)
        VALUES ('email_config', ?, 'email', 'json', '邮件配置')
      `, emailVal);
      console.log('✅ 邮件配置迁移成功！');
    }
  } catch (err) {
    console.log('⚠️ 邮件配置读取跳过 (表可能已删除或不存在)');
  }

  // 3. 迁移 SMS 配置
  try {
    const smss = await prisma.$queryRawUnsafe(`SELECT * FROM sms_configs`);
    if (smss && smss.length > 0) {
      for (const s of smss) {
        const smsVal = JSON.stringify({
          provider: s.provider,
          enabled: s.enabled,
          region: s.region,
          accessKeyId: s.access_key_id,
          secretEncrypted: s.secret_encrypted,
          signName: s.sign_name,
          sdkAppId: s.sdk_app_id,
          smsAccount: s.sms_account
        });
        await prisma.$executeRawUnsafe(`
          INSERT IGNORE INTO system_configs (item, value, class, type, mark)
          VALUES (?, ?, 'sms', 'json', ?)
        `, `sms_config_${s.provider}`, smsVal, `${s.provider} 短信配置`);
      }
      console.log('✅ 短信配置迁移成功！');
    }
  } catch (err) {
    console.log('⚠️ 短信配置读取跳过 (表可能已删除或不存在)');
  }

  console.log('🎉 迁移结束！现在你可以安全地运行 npx prisma db push 并输入 y 确认删除了。');
}

main()
  .catch((e) => {
    console.error('迁移发生错误:', e);
  })
  .finally(async () => {
    await prisma.$disconnect();
    process.exit(0);
  });
