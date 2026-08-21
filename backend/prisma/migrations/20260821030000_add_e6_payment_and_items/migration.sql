ALTER TABLE `e6_imports`
  ADD COLUMN `is_paid` TINYINT NOT NULL DEFAULT 0;

ALTER TABLE `e6_imports`
  DROP FOREIGN KEY `e6_imports_prescription_id_fkey`,
  DROP FOREIGN KEY `e6_imports_processing_plan_id_fkey`;

ALTER TABLE `e6_imports`
  DROP INDEX `e6_imports_prescription_id_key`,
  DROP INDEX `e6_imports_processing_plan_id_key`;

CREATE INDEX `e6_imports_is_paid_idx` ON `e6_imports`(`is_paid`);

ALTER TABLE `e6_imports`
  ADD CONSTRAINT `e6_imports_prescription_id_fkey`
  FOREIGN KEY (`prescription_id`) REFERENCES `prescriptions`(`id`)
  ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `e6_imports_processing_plan_id_fkey`
  FOREIGN KEY (`processing_plan_id`) REFERENCES `processing_plans`(`id`)
  ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE `e6_import_items` (
  `id` INTEGER NOT NULL AUTO_INCREMENT,
  `e6_import_id` INTEGER NOT NULL,
  `sequence` INTEGER NOT NULL,
  `herb_name` VARCHAR(200) NOT NULL,
  `quantity` DECIMAL(14, 3) NOT NULL,
  `unit` VARCHAR(10) NOT NULL DEFAULT 'g',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL,
  UNIQUE INDEX `e6_import_items_e6_import_id_sequence_key`(`e6_import_id`, `sequence`),
  INDEX `e6_import_items_e6_import_id_idx`(`e6_import_id`),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `e6_import_items`
  ADD CONSTRAINT `e6_import_items_e6_import_id_fkey`
  FOREIGN KEY (`e6_import_id`) REFERENCES `e6_imports`(`id`)
  ON DELETE CASCADE ON UPDATE CASCADE;
