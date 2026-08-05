ALTER TABLE `processing_plans`
  ADD COLUMN `plan_code` VARCHAR(24) NULL,
  ADD COLUMN `scan_token` VARCHAR(64) NULL,
  ADD COLUMN `workflow_version` TINYINT NOT NULL DEFAULT 1,
  ADD COLUMN `current_stage` VARCHAR(32) NULL,
  ADD COLUMN `dispensing_completed_at` DATETIME(3) NULL,
  ADD COLUMN `dispensing_completed_by` INTEGER NULL;

CREATE UNIQUE INDEX `processing_plans_plan_code_key` ON `processing_plans`(`plan_code`);
CREATE UNIQUE INDEX `processing_plans_scan_token_key` ON `processing_plans`(`scan_token`);
CREATE INDEX `processing_plans_store_id_current_stage_idx` ON `processing_plans`(`store_id`, `current_stage`);

CREATE TABLE `processing_photos` (
  `id` INTEGER NOT NULL AUTO_INCREMENT,
  `processing_plan_id` INTEGER NOT NULL,
  `kind` VARCHAR(32) NOT NULL,
  `original_name` VARCHAR(255) NOT NULL,
  `mime_type` VARCHAR(100) NOT NULL,
  `file_size` INTEGER NOT NULL,
  `data` LONGBLOB NOT NULL,
  `created_by` INTEGER NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL,
  INDEX `processing_photos_processing_plan_id_kind_deleted_at_idx`(`processing_plan_id`, `kind`, `deleted_at`),
  INDEX `processing_photos_created_by_idx`(`created_by`),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `processing_equipment` (
  `id` INTEGER NOT NULL AUTO_INCREMENT,
  `store_id` INTEGER NOT NULL,
  `equipment_no` VARCHAR(32) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `type` VARCHAR(32) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `scan_token` VARCHAR(64) NOT NULL,
  `current_usage_id` INTEGER NULL,
  `remark` VARCHAR(500) NULL,
  `created_by` INTEGER NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` INTEGER NULL,
  `updated_at` DATETIME(3) NOT NULL,
  `deleted_at` DATETIME(3) NULL,
  `deleted_by` INTEGER NULL,
  UNIQUE INDEX `processing_equipment_scan_token_key`(`scan_token`),
  UNIQUE INDEX `processing_equipment_current_usage_id_key`(`current_usage_id`),
  UNIQUE INDEX `processing_equipment_store_id_equipment_no_key`(`store_id`, `equipment_no`),
  INDEX `processing_equipment_store_id_type_status_deleted_at_idx`(`store_id`, `type`, `status`, `deleted_at`),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `processing_equipment_usages` (
  `id` INTEGER NOT NULL AUTO_INCREMENT,
  `processing_plan_id` INTEGER NOT NULL,
  `equipment_id` INTEGER NOT NULL,
  `stage` VARCHAR(32) NOT NULL,
  `portion_no` INTEGER NOT NULL,
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `ended_at` DATETIME(3) NULL,
  `started_by` INTEGER NOT NULL,
  `ended_by` INTEGER NULL,
  `end_reason` VARCHAR(255) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL,
  INDEX `proc_equip_usage_plan_stage_portion_idx`(`processing_plan_id`, `stage`, `portion_no`),
  INDEX `processing_equipment_usages_equipment_id_ended_at_idx`(`equipment_id`, `ended_at`),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `processing_photos` ADD CONSTRAINT `processing_photos_processing_plan_id_fkey` FOREIGN KEY (`processing_plan_id`) REFERENCES `processing_plans`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE `processing_photos` ADD CONSTRAINT `processing_photos_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `processing_equipment` ADD CONSTRAINT `processing_equipment_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `processing_equipment_usages` ADD CONSTRAINT `processing_equipment_usages_processing_plan_id_fkey` FOREIGN KEY (`processing_plan_id`) REFERENCES `processing_plans`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE `processing_equipment_usages` ADD CONSTRAINT `processing_equipment_usages_equipment_id_fkey` FOREIGN KEY (`equipment_id`) REFERENCES `processing_equipment`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `processing_equipment` ADD CONSTRAINT `processing_equipment_current_usage_id_fkey` FOREIGN KEY (`current_usage_id`) REFERENCES `processing_equipment_usages`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

UPDATE `print_templates`
SET
  `name` = '加工标签（70×50扫码版）',
  `width_mm` = 70,
  `height_mm` = 50,
  `layout_json` = '{"version":1,"fields":[{"id":"qrcode","x":2.5,"y":2.5,"width":22,"height":22,"fontSize":3,"align":"center","bold":false,"wrap":false,"visible":true},{"id":"planCode","x":2.5,"y":25.2,"width":22,"height":3.5,"fontSize":2.65,"align":"center","bold":true,"wrap":false,"visible":true},{"id":"processType","x":26.5,"y":2.7,"width":12,"height":4,"fontSize":3.1,"align":"center","bold":true,"wrap":false,"visible":true},{"id":"batchNo","x":53,"y":2.7,"width":14.5,"height":4,"fontSize":3.1,"align":"right","bold":true,"wrap":false,"visible":true},{"id":"customerName","x":26.5,"y":7.5,"width":41,"height":7.4,"fontSize":6.2,"align":"left","bold":true,"wrap":false,"visible":true},{"id":"totalDose","x":26.5,"y":15.5,"width":10,"height":5.4,"fontSize":4.15,"align":"left","bold":true,"wrap":false,"visible":true},{"id":"bagCount","x":37,"y":15.5,"width":11,"height":5.4,"fontSize":4.15,"align":"left","bold":true,"wrap":false,"visible":true},{"id":"volumeMl","x":49,"y":15.5,"width":18.5,"height":5.4,"fontSize":4.15,"align":"left","bold":true,"wrap":false,"visible":true},{"id":"processDate","x":26.5,"y":22,"width":41,"height":4,"fontSize":2.9,"align":"left","bold":true,"wrap":false,"visible":true},{"id":"prescriptionNo","x":2.5,"y":31,"width":44,"height":3.8,"fontSize":2.75,"align":"left","bold":true,"wrap":false,"visible":true},{"id":"doctorName","x":49,"y":31,"width":18.5,"height":3.8,"fontSize":2.75,"align":"right","bold":true,"wrap":false,"visible":true},{"id":"processRemark","x":2.5,"y":36,"width":65,"height":11.5,"fontSize":3.05,"align":"left","bold":true,"wrap":true,"visible":true}]}'
WHERE `template_type` = 'PROCESSING' AND `name` = '加工标签（标准）';
