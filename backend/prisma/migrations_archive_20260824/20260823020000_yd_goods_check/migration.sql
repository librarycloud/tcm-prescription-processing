CREATE TABLE `yd_goods_check` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `check_name` VARCHAR(150) NOT NULL,
    `check_type` TINYINT NOT NULL DEFAULT 1,
    `status` TINYINT NOT NULL DEFAULT 0,
    `created_by` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `started_at` DATETIME(3) NULL,
    `finished_at` DATETIME(3) NULL,
    `updated_at` DATETIME(3) NOT NULL,
    INDEX `yd_goods_check_store_id_status_idx`(`store_id`, `status`),
    INDEX `yd_goods_check_store_id_created_at_idx`(`store_id`, `created_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `yd_goods_check_item` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `check_id` INTEGER NOT NULL,
    `store_id` INTEGER NOT NULL,
    `product_id` INTEGER NOT NULL,
    `batch_no` VARCHAR(100) NOT NULL DEFAULT '',
    `system_location_name` VARCHAR(120) NOT NULL DEFAULT '',
    `count_location_name` VARCHAR(120) NULL,
    `system_qty` DECIMAL(14,3) NOT NULL DEFAULT 0,
    `first_count_qty` DECIMAL(14,3) NULL,
    `first_counted_at` DATETIME(3) NULL,
    `first_counted_by` INTEGER NULL,
    `recount_qty` DECIMAL(14,3) NULL,
    `recount_system_qty` DECIMAL(14,3) NULL,
    `recounted_at` DATETIME(3) NULL,
    `recounted_by` INTEGER NULL,
    `location_status` TINYINT NOT NULL DEFAULT 0,
    `check_status` TINYINT NOT NULL DEFAULT 0,
    `review_status` TINYINT NOT NULL DEFAULT 0,
    `reviewed_by` INTEGER NULL,
    `reviewed_at` DATETIME(3) NULL,
    `remark` VARCHAR(500) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    INDEX `yd_goods_check_item_store_id_check_id_idx`(`store_id`, `check_id`),
    INDEX `yd_goods_check_item_store_id_check_status_idx`(`store_id`, `check_status`),
    INDEX `yd_goods_check_item_store_id_product_id_batch_no_idx`(`store_id`, `product_id`, `batch_no`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `yd_goods_check`
    ADD CONSTRAINT `yd_goods_check_store_id_fkey`
    FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE `yd_goods_check_item`
    ADD CONSTRAINT `yd_goods_check_item_check_id_fkey`
    FOREIGN KEY (`check_id`) REFERENCES `yd_goods_check`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT `yd_goods_check_item_store_id_fkey`
    FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT `yd_goods_check_item_product_id_fkey`
    FOREIGN KEY (`product_id`) REFERENCES `e6_pharmacy_products`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
