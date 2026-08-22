ALTER TABLE `e6_pharmacy_products`
    ADD COLUMN `unit` VARCHAR(30) NULL;

ALTER TABLE `e6_pharmacy_inventory_batches`
    ADD COLUMN `inbound_date` DATE NULL,
    ADD COLUMN `location_name` VARCHAR(120) NULL;
