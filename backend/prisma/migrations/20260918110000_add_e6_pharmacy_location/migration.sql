-- AlterTable
ALTER TABLE `e6_pharmacy_inventory_batches` ADD COLUMN `location_code` VARCHAR(120) NOT NULL DEFAULT '';

-- CreateTable
CREATE TABLE `e6_pharmacy_locations` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `code` VARCHAR(100) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `is_disabled` BOOLEAN NOT NULL DEFAULT false,
    `received_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `e6_pharmacy_locations_store_id_code_key`(`store_id`, `code`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `e6_pharmacy_locations` ADD CONSTRAINT `e6_pharmacy_locations_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
