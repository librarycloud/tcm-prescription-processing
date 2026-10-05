ALTER TABLE `admin_device_tokens`
    ADD COLUMN `prescription_notify` BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN `transfer_notify` BOOLEAN NOT NULL DEFAULT true;
