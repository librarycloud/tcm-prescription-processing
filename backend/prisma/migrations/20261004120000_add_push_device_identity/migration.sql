ALTER TABLE `admin_device_tokens`
    ADD COLUMN `device_id` VARCHAR(64) NULL,
    ADD INDEX `admin_device_tokens_admin_id_device_id_idx` (`admin_id`, `device_id`);
