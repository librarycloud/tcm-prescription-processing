-- AlterTable
ALTER TABLE `yd_goods_check_item` ADD COLUMN `system_location_code` VARCHAR(120) NOT NULL DEFAULT '',
ADD COLUMN `count_location_code` VARCHAR(120) NULL;
