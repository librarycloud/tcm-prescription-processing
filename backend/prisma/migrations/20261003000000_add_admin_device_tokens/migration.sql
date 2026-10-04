-- CreateTable: admin_device_tokens
-- Stores FCM (Android) and APNs (iOS) push notification device tokens per admin.
-- One token is globally unique — the UNIQUE constraint on `token` handles reassignment.

CREATE TABLE `admin_device_tokens` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `admin_id` INTEGER NOT NULL,
    `platform` VARCHAR(10) NOT NULL,
    `token` VARCHAR(255) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `admin_device_tokens_token_key`(`token`),
    INDEX `admin_device_tokens_admin_id_idx`(`admin_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `admin_device_tokens` ADD CONSTRAINT `admin_device_tokens_admin_id_fkey` FOREIGN KEY (`admin_id`) REFERENCES `admins`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
