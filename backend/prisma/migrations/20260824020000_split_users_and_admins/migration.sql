-- Split customer accounts from back-office administrator accounts.
CREATE TABLE `admins` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    `openid` VARCHAR(64) NULL,
    `unionid` VARCHAR(64) NULL,
    `wechat_bound_at` DATETIME(3) NULL,
    `role` TINYINT NOT NULL DEFAULT 2,
    `status` TINYINT NOT NULL DEFAULT 1,
    `store_id` INTEGER NULL,
    `nickname` VARCHAR(64) NULL,
    `name` VARCHAR(64) NULL,
    `email` VARCHAR(191) NULL,
    `email_verified_at` DATETIME(3) NULL,
    `remark` VARCHAR(500) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    UNIQUE INDEX `admins_phone_key`(`phone`),
    UNIQUE INDEX `admins_openid_key`(`openid`),
    UNIQUE INDEX `admins_unionid_key`(`unionid`),
    UNIQUE INDEX `admins_email_key`(`email`),
    INDEX `admins_role_idx`(`role`),
    INDEX `admins_status_idx`(`status`),
    INDEX `admins_store_id_idx`(`store_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Keep the existing administrator IDs so all existing audit data remains valid.
INSERT INTO `admins` (
  `id`, `username`, `password`, `phone`, `openid`, `unionid`, `wechat_bound_at`, `role`, `status`, `store_id`, `nickname`,
  `name`, `email`, `email_verified_at`, `remark`, `created_at`, `updated_at`, `created_by`, `updated_by`
)
SELECT
  `id`, `username`, `password`, `phone`, `openid`, `unionid`, `wechat_bound_at`, `role`, `status`, `store_id`, `nickname`,
  `name`, `email`, `email_verified_at`, `remark`, `created_at`, `updated_at`, `created_by`, `updated_by`
FROM `users`
WHERE `role` IN (0, 2);

ALTER TABLE `login_logs` ADD COLUMN `account_type` VARCHAR(10) NOT NULL DEFAULT 'user' AFTER `user_id`;
UPDATE `login_logs` AS `log`
JOIN `users` AS `user` ON `user`.`id` = `log`.`user_id`
SET `log`.`account_type` = IF(`user`.`role` IN (0, 2), 'admin', 'user');

ALTER TABLE `operation_logs` ADD COLUMN `actor_type` VARCHAR(10) NULL AFTER `actor_id`;
UPDATE `operation_logs` AS `log`
JOIN `users` AS `user` ON `user`.`id` = `log`.`actor_id`
SET `log`.`actor_type` = IF(`user`.`role` IN (0, 2), 'admin', 'user');

ALTER TABLE `users` DROP FOREIGN KEY `users_store_id_fkey`;
ALTER TABLE `products_diff_logs` DROP FOREIGN KEY `products_diff_logs_created_by_fkey`;
ALTER TABLE `packages` DROP FOREIGN KEY `packages_created_by_fkey`;
ALTER TABLE `packages` DROP FOREIGN KEY `packages_verified_by_fkey`;
ALTER TABLE `packages` DROP FOREIGN KEY `packages_modified_by_fkey`;
ALTER TABLE `prescriptions` DROP FOREIGN KEY `prescriptions_created_by_fkey`;
ALTER TABLE `processing_plans` DROP FOREIGN KEY `processing_plans_created_by_fkey`;
ALTER TABLE `prescription_attachments` DROP FOREIGN KEY `prescription_attachments_created_by_fkey`;
ALTER TABLE `processing_photos` DROP FOREIGN KEY `processing_photos_created_by_fkey`;
ALTER TABLE `processing_workflow_exceptions` DROP FOREIGN KEY `processing_workflow_exceptions_created_by_fkey`;
ALTER TABLE `robot_configs` DROP FOREIGN KEY `robot_configs_created_by_fkey`;
ALTER TABLE `robot_configs` DROP FOREIGN KEY `robot_configs_updated_by_fkey`;
ALTER TABLE `robot_event_configs` DROP FOREIGN KEY `robot_event_configs_created_by_fkey`;
ALTER TABLE `robot_event_configs` DROP FOREIGN KEY `robot_event_configs_updated_by_fkey`;
ALTER TABLE `robot_notification_events` DROP FOREIGN KEY `robot_notification_events_operator_id_fkey`;
ALTER TABLE `print_templates` DROP FOREIGN KEY `print_templates_created_by_fkey`;
ALTER TABLE `store_transfers` DROP FOREIGN KEY `store_transfers_created_by_fkey`;
ALTER TABLE `store_transfers` DROP FOREIGN KEY `store_transfers_outbound_confirmed_by_fkey`;
ALTER TABLE `store_transfer_returns` DROP FOREIGN KEY `store_transfer_returns_operator_id_fkey`;
ALTER TABLE `store_transfer_returns` DROP FOREIGN KEY `store_transfer_returns_confirmed_by_fkey`;

DELETE FROM `users` WHERE `role` IN (0, 2);

ALTER TABLE `users`
  DROP INDEX `users_role_idx`,
  DROP INDEX `users_store_id_idx`,
  DROP COLUMN `role`,
  DROP COLUMN `store_id`;

ALTER TABLE `admins` ADD CONSTRAINT `admins_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `products_diff_logs` ADD CONSTRAINT `products_diff_logs_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `packages` ADD CONSTRAINT `packages_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `packages` ADD CONSTRAINT `packages_verified_by_fkey` FOREIGN KEY (`verified_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `packages` ADD CONSTRAINT `packages_modified_by_fkey` FOREIGN KEY (`modified_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `prescriptions` ADD CONSTRAINT `prescriptions_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `processing_plans` ADD CONSTRAINT `processing_plans_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `prescription_attachments` ADD CONSTRAINT `prescription_attachments_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `processing_photos` ADD CONSTRAINT `processing_photos_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `processing_workflow_exceptions` ADD CONSTRAINT `processing_workflow_exceptions_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `robot_configs` ADD CONSTRAINT `robot_configs_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `robot_configs` ADD CONSTRAINT `robot_configs_updated_by_fkey` FOREIGN KEY (`updated_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `robot_event_configs` ADD CONSTRAINT `robot_event_configs_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `robot_event_configs` ADD CONSTRAINT `robot_event_configs_updated_by_fkey` FOREIGN KEY (`updated_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `robot_notification_events` ADD CONSTRAINT `robot_notification_events_operator_id_fkey` FOREIGN KEY (`operator_id`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `print_templates` ADD CONSTRAINT `print_templates_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `store_transfers` ADD CONSTRAINT `store_transfers_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `store_transfers` ADD CONSTRAINT `store_transfers_outbound_confirmed_by_fkey` FOREIGN KEY (`outbound_confirmed_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE `store_transfer_returns` ADD CONSTRAINT `store_transfer_returns_operator_id_fkey` FOREIGN KEY (`operator_id`) REFERENCES `admins`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `store_transfer_returns` ADD CONSTRAINT `store_transfer_returns_confirmed_by_fkey` FOREIGN KEY (`confirmed_by`) REFERENCES `admins`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
