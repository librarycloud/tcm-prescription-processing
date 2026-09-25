-- Create a regular index to satisfy the foreign key requirement
CREATE INDEX `prescription_attachments_prescription_id_fkey` ON `prescription_attachments`(`prescription_id`);

-- Drop the unique index
DROP INDEX `prescription_attachments_prescription_id_key` ON `prescription_attachments`;
