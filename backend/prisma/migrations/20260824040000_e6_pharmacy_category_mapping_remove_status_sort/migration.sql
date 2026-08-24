ALTER TABLE `e6_pharmacy_category_mappings`
    DROP INDEX `e6_pharmacy_category_mappings_status_sort_idx`,
    DROP COLUMN `status`,
    DROP COLUMN `sort`;
