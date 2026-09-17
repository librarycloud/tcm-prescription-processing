-- E6商品资料在各门店相同，商品主数据改为全局共享；库存仍按门店隔离。
CREATE TEMPORARY TABLE `e6_pharmacy_product_map` AS
SELECT p.`id` AS `old_id`, MIN(c.`id`) AS `canonical_id`
FROM `e6_pharmacy_products` p
JOIN `e6_pharmacy_products` c
  ON c.`e6_product_id` = p.`e6_product_id`
GROUP BY p.`id`;

-- 同一门店、商品、批号、货位只保留一条库存；数量合并，金额保留原单价值。
CREATE TEMPORARY TABLE `e6_pharmacy_inventory_merged` AS
SELECT
  i.`store_id`,
  m.`canonical_id` AS `product_id`,
  i.`batch_no`,
  MAX(i.`production_date`) AS `production_date`,
  MAX(i.`expiry_date`) AS `expiry_date`,
  MAX(i.`inbound_date`) AS `inbound_date`,
  i.`location_name`,
  SUM(i.`quantity`) AS `quantity`,
  MAX(i.`amount`) AS `amount`,
  MAX(i.`received_at`) AS `received_at`,
  MAX(i.`updated_at`) AS `updated_at`
FROM `e6_pharmacy_inventory_batches` i
JOIN `e6_pharmacy_product_map` m ON m.`old_id` = i.`product_id`
GROUP BY i.`store_id`, m.`canonical_id`, i.`batch_no`, i.`location_name`;

UPDATE `yd_goods_check_item` item
JOIN `e6_pharmacy_product_map` m ON m.`old_id` = item.`product_id`
SET item.`product_id` = m.`canonical_id`;

DELETE FROM `e6_pharmacy_inventory_batches`;
INSERT INTO `e6_pharmacy_inventory_batches`
  (`store_id`, `product_id`, `batch_no`, `production_date`, `expiry_date`, `inbound_date`, `location_name`, `quantity`, `amount`, `received_at`, `updated_at`)
SELECT
  `store_id`, `product_id`, `batch_no`, `production_date`, `expiry_date`, `inbound_date`, `location_name`, `quantity`, `amount`, `received_at`, `updated_at`
FROM `e6_pharmacy_inventory_merged`;

DELETE p
FROM `e6_pharmacy_products` p
JOIN `e6_pharmacy_product_map` m ON m.`old_id` = p.`id`
WHERE m.`old_id` <> m.`canonical_id`;

ALTER TABLE `e6_pharmacy_products`
  DROP FOREIGN KEY `e6_pharmacy_products_store_id_fkey`,
  DROP INDEX `e6_pharmacy_products_store_id_e6_product_id_key`,
  DROP INDEX `e6_pharmacy_products_store_id_product_code_idx`,
  DROP INDEX `e6_pharmacy_products_store_id_barcode_idx`,
  DROP INDEX `e6_pharmacy_products_store_id_e6_modified_at_idx`,
  DROP COLUMN `store_id`;

CREATE UNIQUE INDEX `e6_pharmacy_products_e6_product_id_key`
  ON `e6_pharmacy_products`(`e6_product_id`);
CREATE INDEX `e6_pharmacy_products_product_code_idx`
  ON `e6_pharmacy_products`(`product_code`);
CREATE INDEX `e6_pharmacy_products_barcode_idx`
  ON `e6_pharmacy_products`(`barcode`);
CREATE INDEX `e6_pharmacy_products_e6_modified_at_idx`
  ON `e6_pharmacy_products`(`e6_modified_at`);
