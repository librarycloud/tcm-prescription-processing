CREATE TABLE `e6_pharmacy_products` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `e6_product_id` INTEGER NOT NULL,
    `product_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `category` VARCHAR(100) NULL,
    `category_code` VARCHAR(64) NULL,
    `barcode` VARCHAR(64) NULL,
    `specification` VARCHAR(120) NULL,
    `dosage_form` VARCHAR(64) NULL,
    `manufacturer` VARCHAR(200) NULL,
    `category_attribute` VARCHAR(100) NULL,
    `e6_created_at` DATETIME(3) NULL,
    `e6_modified_at` DATETIME(3) NULL,
    `last_inventory_seen_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    UNIQUE INDEX `e6_pharmacy_products_store_id_e6_product_id_key`(`store_id`, `e6_product_id`),
    INDEX `e6_pharmacy_products_store_id_product_code_idx`(`store_id`, `product_code`),
    INDEX `e6_pharmacy_products_store_id_barcode_idx`(`store_id`, `barcode`),
    INDEX `e6_pharmacy_products_store_id_e6_modified_at_idx`(`store_id`, `e6_modified_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `e6_pharmacy_inventory_batches` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `product_id` INTEGER NOT NULL,
    `batch_no` VARCHAR(100) NOT NULL DEFAULT '',
    `production_date` DATE NULL,
    `expiry_date` DATE NULL,
    `quantity` DECIMAL(14,3) NOT NULL,
    `amount` DECIMAL(14,2) NOT NULL,
    `received_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    UNIQUE INDEX `e6_pharmacy_inventory_batches_store_id_product_id_batch_no_key`(`store_id`, `product_id`, `batch_no`),
    INDEX `e6_pharmacy_inventory_batches_store_id_product_id_idx`(`store_id`, `product_id`),
    INDEX `e6_pharmacy_inventory_batches_store_id_expiry_date_idx`(`store_id`, `expiry_date`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `e6_pharmacy_products`
    ADD CONSTRAINT `e6_pharmacy_products_store_id_fkey`
    FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE `e6_pharmacy_inventory_batches`
    ADD CONSTRAINT `e6_pharmacy_inventory_batches_store_id_fkey`
    FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT `e6_pharmacy_inventory_batches_product_id_fkey`
    FOREIGN KEY (`product_id`) REFERENCES `e6_pharmacy_products`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
