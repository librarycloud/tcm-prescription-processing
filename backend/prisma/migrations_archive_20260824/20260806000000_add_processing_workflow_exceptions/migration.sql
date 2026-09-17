ALTER TABLE `processing_equipment_usages`
  ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1,
  ADD COLUMN `source` TINYINT NOT NULL DEFAULT 1,
  ADD COLUMN `request_id` VARCHAR(64) NULL,
  ADD COLUMN `voided_at` DATETIME(3) NULL,
  ADD COLUMN `voided_by` INTEGER NULL,
  ADD COLUMN `void_reason` VARCHAR(255) NULL,
  ADD COLUMN `transferred_from_usage_id` INTEGER NULL;

UPDATE `processing_equipment_usages`
SET `status` = CASE WHEN `ended_at` IS NULL THEN 1 ELSE 2 END;

ALTER TABLE `processing_equipment_usages`
  ADD UNIQUE INDEX `processing_equipment_usages_request_id_key`(`request_id`),
  ADD INDEX `processing_equipment_usages_equipment_id_status_started_at_idx`(`equipment_id`, `status`, `started_at`),
  ADD INDEX `processing_equipment_usages_transferred_from_usage_id_idx`(`transferred_from_usage_id`);

DROP INDEX `processing_equipment_usages_equipment_id_ended_at_idx`
  ON `processing_equipment_usages`;

CREATE TABLE `processing_workflow_exceptions` (
  `id` INTEGER NOT NULL AUTO_INCREMENT,
  `processing_plan_id` INTEGER NOT NULL,
  `usage_id` INTEGER NULL,
  `related_usage_id` INTEGER NULL,
  `type` TINYINT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 2,
  `reason` VARCHAR(255) NOT NULL,
  `details` JSON NULL,
  `created_by` INTEGER NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX `processing_workflow_exceptions_processing_plan_id_created_at_idx`(`processing_plan_id`, `created_at`),
  INDEX `processing_workflow_exceptions_usage_id_idx`(`usage_id`),
  INDEX `processing_workflow_exceptions_type_status_idx`(`type`, `status`),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `processing_workflow_exceptions`
  ADD CONSTRAINT `processing_workflow_exceptions_processing_plan_id_fkey`
  FOREIGN KEY (`processing_plan_id`) REFERENCES `processing_plans`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `processing_workflow_exceptions_usage_id_fkey`
  FOREIGN KEY (`usage_id`) REFERENCES `processing_equipment_usages`(`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `processing_workflow_exceptions_created_by_fkey`
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
