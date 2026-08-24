UPDATE `e6_pharmacy_inventory_batches`
SET `location_name` = ''
WHERE `location_name` IS NULL;

ALTER TABLE `e6_pharmacy_inventory_batches`
    MODIFY COLUMN `location_name` VARCHAR(120) NOT NULL DEFAULT '';

DROP INDEX `e6_pharmacy_inventory_batches_store_id_product_id_batch_no_key`
    ON `e6_pharmacy_inventory_batches`;

CREATE UNIQUE INDEX `e6_pharmacy_inventory_store_product_batch_location_key`
    ON `e6_pharmacy_inventory_batches`(`store_id`, `product_id`, `batch_no`, `location_name`);
