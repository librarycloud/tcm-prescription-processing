-- 商品资料由总部统一维护：以商品编号作为全局唯一键，移除 E6 内部商品 ID。
-- 迁移前可能存在同一商品编号的重复记录，先保留最早创建的记录。
CREATE TEMPORARY TABLE `e6_pharmacy_product_map` AS
SELECT p.`id` AS `old_id`, MIN(c.`id`) AS `canonical_id`
FROM `e6_pharmacy_products` p
JOIN `e6_pharmacy_products` c
  ON c.`product_code` = p.`product_code`
GROUP BY p.`id`;

-- 合并重复商品可能造成库存唯一键冲突，先按门店、商品、批号、货位合并库存。
CREATE TEMPORARY TABLE `e6_pharmacy_inventory_code_merged` AS
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
FROM `e6_pharmacy_inventory_code_merged`;

DELETE p
FROM `e6_pharmacy_products` p
JOIN `e6_pharmacy_product_map` m ON m.`old_id` = p.`id`
WHERE m.`old_id` <> m.`canonical_id`;

ALTER TABLE `e6_pharmacy_products`
  DROP INDEX `e6_pharmacy_products_e6_product_id_key`,
  DROP INDEX `e6_pharmacy_products_product_code_idx`,
  DROP COLUMN `e6_product_id`;

CREATE UNIQUE INDEX `e6_pharmacy_products_product_code_key`
  ON `e6_pharmacy_products`(`product_code`);
