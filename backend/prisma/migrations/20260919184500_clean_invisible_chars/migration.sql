-- Delete ghost locations that contain zero-width spaces (u200B)
DELETE FROM `e6_pharmacy_locations` WHERE `code` LIKE CONCAT('%', CHAR(0xE2, 0x80, 0x8B), '%');

-- Clean up any inventory batches that might have been synced with the invisible character
UPDATE `e6_pharmacy_inventory_batches` SET `location_code` = REPLACE(`location_code`, CHAR(0xE2, 0x80, 0x8B), '');
