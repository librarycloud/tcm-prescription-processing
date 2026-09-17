CREATE TABLE `e6_pharmacy_category_mappings` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `category_code` VARCHAR(64) NOT NULL,
    `category_name` VARCHAR(100) NOT NULL,
    `sort` INTEGER NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    UNIQUE INDEX `e6_pharmacy_category_mappings_category_code_key`(`category_code`),
    INDEX `e6_pharmacy_category_mappings_status_sort_idx`(`status`, `sort`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
