ALTER TABLE `prescription_attachments`
  ADD COLUMN `storage_path` VARCHAR(500) NULL,
  MODIFY COLUMN `data` LONGBLOB NULL;

ALTER TABLE `processing_photos`
  ADD COLUMN `storage_path` VARCHAR(500) NULL,
  MODIFY COLUMN `data` LONGBLOB NULL;
