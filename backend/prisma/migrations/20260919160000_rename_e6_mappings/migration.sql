-- AlterTable
RENAME TABLE `e6_operator_mappings` TO `e6_user_mappings`;

-- AlterTable
ALTER TABLE `e6_user_mappings` RENAME COLUMN `e6_operator_name` TO `e6_user_code`;
ALTER TABLE `e6_user_mappings` RENAME COLUMN `operator_name` TO `user_name`;

-- AlterTable
ALTER TABLE `e6_imports` RENAME COLUMN `e6_doctor_code` TO `salesperson_code`;
