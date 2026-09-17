ALTER TABLE `e6_pharmacy_products`
  ADD COLUMN `retail_price` DECIMAL(14, 2) NOT NULL DEFAULT 0 AFTER `unit`;

ALTER TABLE `e6_pharmacy_inventory_batches`
  DROP COLUMN `amount`;
