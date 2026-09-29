ALTER TABLE `packages` ADD COLUMN `pickup_proxy_name` VARCHAR(64) NULL;
ALTER TABLE `packages` ADD COLUMN `pickup_proxy_phone` VARCHAR(20) NULL;
ALTER TABLE `packages` ADD COLUMN `print_count` INTEGER NOT NULL DEFAULT 0;
