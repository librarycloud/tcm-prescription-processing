CREATE TABLE `e6_operator_mappings` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `e6_operator_name` VARCHAR(100) NOT NULL,
    `operator_name` VARCHAR(100) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    UNIQUE INDEX `e6_operator_mappings_store_id_e6_operator_name_key`(`store_id`, `e6_operator_name`),
    INDEX `e6_operator_mappings_store_id_status_idx`(`store_id`, `status`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `e6_operator_mappings`
    ADD CONSTRAINT `e6_operator_mappings_store_id_fkey`
    FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
