-- CreateTable
CREATE TABLE `system_configs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `item` VARCHAR(255) NOT NULL DEFAULT '',
    `value` TEXT NOT NULL,
    `class` VARCHAR(16) NOT NULL DEFAULT '',
    `is_public` BOOLEAN NOT NULL DEFAULT false,
    `type` VARCHAR(16) NOT NULL DEFAULT '',
    `default` TEXT NOT NULL,
    `mark` VARCHAR(255) NOT NULL DEFAULT '',

    UNIQUE INDEX `system_configs_item_key`(`item`),
    INDEX `system_configs_class_idx`(`class`),
    INDEX `system_configs_is_public_idx`(`is_public`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- DropTable
DROP TABLE `email_configs`;
DROP TABLE `sms_configs`;
