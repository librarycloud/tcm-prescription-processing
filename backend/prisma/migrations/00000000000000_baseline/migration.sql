-- CreateTable
CREATE TABLE `users` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    `openid` VARCHAR(64) NULL,
    `unionid` VARCHAR(64) NULL,
    `wechat_bound_at` DATETIME(3) NULL,
    `role` TINYINT NOT NULL DEFAULT 1,
    `status` TINYINT NOT NULL DEFAULT 1,
    `store_id` INTEGER NULL,
    `nickname` VARCHAR(64) NULL,
    `name` VARCHAR(64) NULL,
    `email` VARCHAR(191) NULL,
    `email_verified_at` DATETIME(3) NULL,
    `remark` VARCHAR(500) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,

    UNIQUE INDEX `users_phone_key`(`phone`),
    UNIQUE INDEX `users_openid_key`(`openid`),
    UNIQUE INDEX `users_unionid_key`(`unionid`),
    UNIQUE INDEX `users_email_key`(`email`),
    INDEX `users_role_idx`(`role`),
    INDEX `users_status_idx`(`status`),
    INDEX `users_store_id_idx`(`store_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `stores` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `code` VARCHAR(50) NOT NULL,
    `address` VARCHAR(255) NULL,
    `phone` VARCHAR(20) NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` INTEGER NULL,
    `drawer_layer_count` TINYINT NOT NULL DEFAULT 8,
    `drawer_unit_count` TINYINT NOT NULL DEFAULT 5,
    `drawer_layer_columns` VARCHAR(255) NOT NULL DEFAULT '[6,6,6,6,6,6,6,3]',
    `drawer_top_column_count` TINYINT NOT NULL DEFAULT 6,
    `big_cabinet_unit_count` TINYINT NOT NULL DEFAULT 5,
    `big_cabinet_layer_count` TINYINT NOT NULL DEFAULT 3,
    `e6_enabled` TINYINT NOT NULL DEFAULT 0,
    `e6_api_key_hash` VARCHAR(64) NULL,
    `e6_api_key_hint` VARCHAR(16) NULL,
    `e6_last_used_at` DATETIME(3) NULL,
    `e6_rotated_at` DATETIME(3) NULL,

    UNIQUE INDEX `stores_name_key`(`name`),
    UNIQUE INDEX `stores_code_key`(`code`),
    INDEX `stores_status_idx`(`status`),
    INDEX `stores_e6_enabled_idx`(`e6_enabled`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `herbs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `code` VARCHAR(64) NULL,
    `name` VARCHAR(100) NOT NULL,
    `specification` VARCHAR(100) NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,

    INDEX `herbs_store_id_name_idx`(`store_id`, `name`),
    INDEX `herbs_store_id_code_idx`(`store_id`, `code`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `products` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `product_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `specification` VARCHAR(120) NULL,
    `unit` VARCHAR(20) NOT NULL,
    `retail_price` DECIMAL(14, 2) NOT NULL DEFAULT 0,
    `diff_quantity` DECIMAL(12, 3) NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` INTEGER NULL,

    INDEX `products_store_id_name_idx`(`store_id`, `name`),
    INDEX `products_store_id_status_idx`(`store_id`, `status`),
    INDEX `products_store_id_diff_quantity_idx`(`store_id`, `diff_quantity`),
    UNIQUE INDEX `products_store_id_product_code_key`(`store_id`, `product_code`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `products_diff_logs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `operation_no` VARCHAR(32) NOT NULL,
    `store_id` INTEGER NOT NULL,
    `product_id` INTEGER NOT NULL,
    `operation_type` VARCHAR(32) NOT NULL,
    `change_quantity` DECIMAL(12, 3) NOT NULL,
    `balance_after` DECIMAL(12, 3) NOT NULL,
    `business_date` DATE NOT NULL,
    `batch_note` VARCHAR(120) NULL,
    `borrower_name` VARCHAR(100) NULL,
    `supplier_name` VARCHAR(120) NULL,
    `related_log_id` INTEGER NULL,
    `system_document_no` VARCHAR(100) NULL,
    `remark` VARCHAR(500) NULL,
    `created_by` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `products_diff_logs_store_id_business_date_idx`(`store_id`, `business_date`),
    INDEX `products_diff_logs_product_id_created_at_idx`(`product_id`, `created_at`),
    INDEX `products_diff_logs_operation_no_idx`(`operation_no`),
    INDEX `products_diff_logs_operation_type_business_date_idx`(`operation_type`, `business_date`),
    INDEX `products_diff_logs_related_log_id_idx`(`related_log_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `herb_locations` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `location_code` VARCHAR(20) NOT NULL,
    `location_type` CHAR(1) NOT NULL,
    `unit_no` TINYINT NOT NULL,
    `layer_no` TINYINT NOT NULL,
    `column_no` TINYINT NULL,
    `medicine_capacity` TINYINT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,

    INDEX `herb_location_scope_idx`(`store_id`, `location_type`, `unit_no`, `layer_no`, `column_no`),
    UNIQUE INDEX `herb_locations_store_id_location_code_key`(`store_id`, `location_code`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `herb_location_assignments` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `location_id` INTEGER NOT NULL,
    `herb_id` INTEGER NOT NULL,
    `slot_no` TINYINT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` INTEGER NULL,

    INDEX `herb_location_assignments_herb_id_idx`(`herb_id`),
    UNIQUE INDEX `herb_location_assignments_location_id_herb_id_key`(`location_id`, `herb_id`),
    UNIQUE INDEX `herb_location_assignments_location_id_slot_no_key`(`location_id`, `slot_no`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `packages` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `pickup_code` VARCHAR(6) NOT NULL,
    `item_name` VARCHAR(120) NOT NULL,
    `item_info` VARCHAR(500) NULL,
    `receiver_name` VARCHAR(64) NOT NULL,
    `receiver_phone` VARCHAR(20) NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `pickup_method` TINYINT NULL,
    `express_tracking_no` VARCHAR(100) NULL,
    `express_address` VARCHAR(500) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `picked_at` DATETIME(3) NULL,
    `modified_at` DATETIME(3) NULL,
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NOT NULL,
    `verified_by` INTEGER NULL,
    `modified_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` INTEGER NULL,
    `notification_status` TINYINT NOT NULL DEFAULT 0,
    `notification_count` INTEGER NOT NULL DEFAULT 0,
    `last_notification_at` DATETIME(3) NULL,
    `processing_plan_id` INTEGER NULL,

    UNIQUE INDEX `packages_pickup_code_key`(`pickup_code`),
    UNIQUE INDEX `packages_processing_plan_id_key`(`processing_plan_id`),
    INDEX `packages_store_id_deleted_at_status_created_at_idx`(`store_id`, `deleted_at`, `status`, `created_at`),
    INDEX `packages_receiver_phone_idx`(`receiver_phone`),
    INDEX `packages_status_idx`(`status`),
    INDEX `packages_created_at_idx`(`created_at`),
    INDEX `packages_picked_at_idx`(`picked_at`),
    INDEX `packages_modified_at_idx`(`modified_at`),
    INDEX `packages_modified_by_idx`(`modified_by`),
    INDEX `packages_notification_status_idx`(`notification_status`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `dictionaries` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `type` VARCHAR(50) NOT NULL,
    `code` VARCHAR(50) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `sort` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` INTEGER NULL,

    INDEX `dictionaries_type_status_sort_idx`(`type`, `status`, `sort`),
    UNIQUE INDEX `dictionaries_type_code_key`(`type`, `code`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `doctors` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `sort` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` INTEGER NULL,

    INDEX `doctors_status_sort_idx`(`status`, `sort`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `prescriptions` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `prescription_no` VARCHAR(24) NOT NULL,
    `customer_name` VARCHAR(64) NOT NULL,
    `phone` VARCHAR(20) NULL,
    `doctor_id` INTEGER NOT NULL,
    `source_id` INTEGER NOT NULL,
    `is_external` TINYINT NOT NULL DEFAULT 0,
    `external_hospital` VARCHAR(150) NULL,
    `external_doctor` VARCHAR(100) NULL,
    `external_remark` VARCHAR(500) NULL,
    `remark` VARCHAR(500) NULL,
    `total_price` DECIMAL(14, 2) NULL,
    `store_id` INTEGER NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `created_by` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `updated_by` INTEGER NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` INTEGER NULL,

    UNIQUE INDEX `prescriptions_prescription_no_key`(`prescription_no`),
    INDEX `prescriptions_phone_idx`(`phone`),
    INDEX `prescriptions_doctor_id_idx`(`doctor_id`),
    INDEX `prescriptions_source_id_idx`(`source_id`),
    INDEX `prescriptions_store_id_status_created_at_idx`(`store_id`, `status`, `created_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `processing_plans` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `prescription_id` INTEGER NOT NULL,
    `batch_no` INTEGER NOT NULL,
    `process_type_id` INTEGER NOT NULL,
    `total_dose` INTEGER NOT NULL,
    `bag_count` INTEGER NULL,
    `volume_ml` INTEGER NULL,
    `usage_method` VARCHAR(200) NULL,
    `taken_dose` INTEGER NOT NULL DEFAULT 0,
    `remaining_dose` INTEGER NOT NULL,
    `schedule_type` TINYINT NOT NULL,
    `process_date` DATETIME(3) NULL,
    `start_date` DATETIME(3) NULL,
    `finish_date` DATETIME(3) NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `priority` TINYINT NOT NULL DEFAULT 0,
    `notify_type` INTEGER NOT NULL,
    `notify_status` TINYINT NOT NULL DEFAULT 0,
    `notify_time` DATETIME(3) NULL,
    `process_remark` VARCHAR(500) NULL,
    `payment_status` TINYINT NOT NULL DEFAULT 1,
    `pickup_method` TINYINT NOT NULL DEFAULT 0,
    `pickup_code` VARCHAR(6) NULL,
    `express_address` VARCHAR(500) NULL,
    `queue_order` INTEGER NULL,
    `remark` VARCHAR(500) NULL,
    `created_by` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `updated_by` INTEGER NULL,
    `deleted_at` DATETIME(3) NULL,
    `deleted_by` INTEGER NULL,
    `store_id` INTEGER NOT NULL,

    UNIQUE INDEX `processing_plans_pickup_code_key`(`pickup_code`),
    INDEX `processing_plans_store_id_status_schedule_type_process_date_idx`(`store_id`, `status`, `schedule_type`, `process_date`),
    INDEX `processing_plans_priority_process_date_queue_order_idx`(`priority`, `process_date`, `queue_order`),
    INDEX `processing_plans_process_type_id_idx`(`process_type_id`),
    UNIQUE INDEX `processing_plans_prescription_id_batch_no_key`(`prescription_id`, `batch_no`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `e6_doctor_mappings` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `e6_doctor_code` VARCHAR(100) NOT NULL,
    `doctor_id` INTEGER NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `e6_doctor_mappings_doctor_id_idx`(`doctor_id`),
    INDEX `e6_doctor_mappings_store_id_status_idx`(`store_id`, `status`),
    UNIQUE INDEX `e6_doctor_mappings_store_id_e6_doctor_code_key`(`store_id`, `e6_doctor_code`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `e6_imports` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `external_order_no` VARCHAR(100) NOT NULL,
    `customer_name` VARCHAR(64) NOT NULL,
    `phone` VARCHAR(20) NULL,
    `e6_doctor_code` VARCHAR(100) NOT NULL,
    `total_price` DECIMAL(14, 2) NOT NULL,
    `dose_count` INTEGER NOT NULL,
    `remark` VARCHAR(500) NULL,
    `raw_payload` LONGTEXT NOT NULL,
    `payload_hash` CHAR(64) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `error_code` VARCHAR(50) NULL,
    `error_message` VARCHAR(500) NULL,
    `source_created_at` DATETIME(3) NULL,
    `source_updated_at` DATETIME(3) NULL,
    `synced_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `last_synced_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `sync_count` INTEGER NOT NULL DEFAULT 1,
    `prescription_id` INTEGER NULL,
    `processing_plan_id` INTEGER NULL,
    `confirmed_by` INTEGER NULL,
    `confirmed_at` DATETIME(3) NULL,
    `rejected_by` INTEGER NULL,
    `rejected_at` DATETIME(3) NULL,
    `reject_reason` VARCHAR(500) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `e6_imports_prescription_id_key`(`prescription_id`),
    UNIQUE INDEX `e6_imports_processing_plan_id_key`(`processing_plan_id`),
    INDEX `e6_imports_store_id_status_synced_at_idx`(`store_id`, `status`, `synced_at`),
    INDEX `e6_imports_e6_doctor_code_idx`(`e6_doctor_code`),
    INDEX `e6_imports_last_synced_at_idx`(`last_synced_at`),
    UNIQUE INDEX `e6_imports_store_id_external_order_no_key`(`store_id`, `external_order_no`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `sms_configs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `provider` VARCHAR(20) NOT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 0,
    `access_key_id` VARCHAR(255) NULL,
    `secret_encrypted` TEXT NULL,
    `sign_name` VARCHAR(100) NULL,
    `sdk_app_id` VARCHAR(64) NULL,
    `sms_account` VARCHAR(128) NULL,
    `region` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `updated_by` INTEGER NULL,

    UNIQUE INDEX `sms_configs_provider_key`(`provider`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `sms_templates` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `provider` VARCHAR(20) NOT NULL,
    `pickup_method` TINYINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `template_code` VARCHAR(128) NULL,
    `content_preview` VARCHAR(500) NULL,
    `variable_mapping` TEXT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `updated_by` INTEGER NULL,

    UNIQUE INDEX `sms_templates_provider_pickup_method_key`(`provider`, `pickup_method`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `notification_logs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `package_id` INTEGER NULL,
    `channel` VARCHAR(20) NOT NULL,
    `provider` VARCHAR(20) NULL,
    `recipient` VARCHAR(191) NOT NULL,
    `template_id` INTEGER NULL,
    `template_code` VARCHAR(128) NULL,
    `template_params` TEXT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `request_id` VARCHAR(64) NOT NULL,
    `provider_request_id` VARCHAR(128) NULL,
    `provider_message` VARCHAR(500) NULL,
    `error_code` VARCHAR(100) NULL,
    `error_message` VARCHAR(500) NULL,
    `operator_id` INTEGER NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `sent_at` DATETIME(3) NULL,

    UNIQUE INDEX `notification_logs_request_id_key`(`request_id`),
    INDEX `notification_logs_package_id_created_at_idx`(`package_id`, `created_at`),
    INDEX `notification_logs_recipient_idx`(`recipient`),
    INDEX `notification_logs_status_idx`(`status`),
    INDEX `notification_logs_created_at_idx`(`created_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `login_logs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `user_id` INTEGER NULL,
    `phone` VARCHAR(20) NULL,
    `login_type` VARCHAR(20) NOT NULL,
    `success` TINYINT NOT NULL,
    `ip` VARCHAR(45) NOT NULL,
    `user_agent` VARCHAR(500) NULL,
    `message` VARCHAR(255) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `store_id` INTEGER NULL,

    INDEX `login_logs_user_id_idx`(`user_id`),
    INDEX `login_logs_phone_idx`(`phone`),
    INDEX `login_logs_ip_idx`(`ip`),
    INDEX `login_logs_success_idx`(`success`),
    INDEX `login_logs_login_type_idx`(`login_type`),
    INDEX `login_logs_created_at_idx`(`created_at`),
    INDEX `login_logs_store_id_created_at_idx`(`store_id`, `created_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `email_configs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `config_key` VARCHAR(32) NOT NULL DEFAULT 'default',
    `host` VARCHAR(255) NULL,
    `port` INTEGER NOT NULL DEFAULT 465,
    `secure` TINYINT NOT NULL DEFAULT 1,
    `username` VARCHAR(255) NULL,
    `password_encrypted` TEXT NULL,
    `from_name` VARCHAR(100) NULL,
    `from_email` VARCHAR(191) NULL,
    `enabled` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `updated_by` INTEGER NULL,

    UNIQUE INDEX `email_configs_config_key_key`(`config_key`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `email_templates` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `scene` VARCHAR(32) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `subject` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,
    `updated_by` INTEGER NULL,

    UNIQUE INDEX `email_templates_scene_key`(`scene`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `email_verification_codes` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `user_id` INTEGER NOT NULL,
    `email` VARCHAR(191) NOT NULL,
    `code_hash` VARCHAR(255) NOT NULL,
    `attempts` INTEGER NOT NULL DEFAULT 0,
    `expires_at` DATETIME(3) NOT NULL,
    `used_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `email_verification_codes_user_id_email_created_at_idx`(`user_id`, `email`, `created_at`),
    INDEX `email_verification_codes_expires_at_idx`(`expires_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `robot_configs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `platform` VARCHAR(20) NOT NULL,
    `scope_type` VARCHAR(20) NOT NULL,
    `store_id` INTEGER NULL,
    `webhook_encrypted` TEXT NOT NULL,
    `secret_encrypted` TEXT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 0,
    `remark` VARCHAR(500) NULL,
    `deleted_at` DATETIME(3) NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `robot_configs_scope_type_store_id_enabled_idx`(`scope_type`, `store_id`, `enabled`),
    INDEX `robot_configs_deleted_at_idx`(`deleted_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `robot_event_configs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `robot_id` INTEGER NOT NULL,
    `event_code` VARCHAR(64) NOT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 0,
    `template_content` TEXT NOT NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `robot_event_configs_event_code_enabled_idx`(`event_code`, `enabled`),
    UNIQUE INDEX `robot_event_configs_robot_id_event_code_key`(`robot_id`, `event_code`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `robot_notification_events` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `event_key` VARCHAR(160) NOT NULL,
    `event_code` VARCHAR(64) NOT NULL,
    `business_type` VARCHAR(32) NOT NULL,
    `business_id` INTEGER NOT NULL,
    `primary_store_id` INTEGER NULL,
    `related_store_ids` JSON NULL,
    `variables` JSON NOT NULL,
    `operator_id` INTEGER NULL,
    `occurred_at` DATETIME(3) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `robot_notification_events_event_key_key`(`event_key`),
    INDEX `robot_notification_events_event_code_occurred_at_idx`(`event_code`, `occurred_at`),
    INDEX `robot_notification_events_business_type_business_id_idx`(`business_type`, `business_id`),
    INDEX `robot_notification_events_primary_store_id_occurred_at_idx`(`primary_store_id`, `occurred_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `robot_delivery_logs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `event_id` INTEGER NOT NULL,
    `robot_id` INTEGER NOT NULL,
    `platform` VARCHAR(20) NOT NULL,
    `template_content` TEXT NOT NULL,
    `rendered_content` TEXT NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `attempt_count` INTEGER NOT NULL DEFAULT 0,
    `next_retry_at` DATETIME(3) NULL,
    `provider_request_id` VARCHAR(128) NULL,
    `provider_response` TEXT NULL,
    `error_code` VARCHAR(100) NULL,
    `error_message` VARCHAR(500) NULL,
    `sent_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `robot_delivery_logs_status_next_retry_at_idx`(`status`, `next_retry_at`),
    INDEX `robot_delivery_logs_robot_id_created_at_idx`(`robot_id`, `created_at`),
    UNIQUE INDEX `robot_delivery_logs_event_id_robot_id_key`(`event_id`, `robot_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `print_templates` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `store_id` INTEGER NOT NULL,
    `template_type` VARCHAR(32) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `width_mm` DECIMAL(8, 2) NOT NULL,
    `height_mm` DECIMAL(8, 2) NOT NULL,
    `layout_json` LONGTEXT NOT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `is_default` TINYINT NOT NULL DEFAULT 0,
    `default_scope` VARCHAR(64) NULL,
    `created_by` INTEGER NULL,
    `updated_by` INTEGER NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `print_templates_one_default_per_scope`(`default_scope`),
    INDEX `print_templates_store_id_template_type_enabled_idx`(`store_id`, `template_type`, `enabled`),
    INDEX `print_templates_store_id_template_type_is_default_idx`(`store_id`, `template_type`, `is_default`),
    UNIQUE INDEX `print_templates_store_id_template_type_name_key`(`store_id`, `template_type`, `name`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `prescription_daily_sequences` (
    `sequence_date` DATE NOT NULL,
    `current_value` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`sequence_date`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `store_transfers` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `transfer_no` VARCHAR(18) NOT NULL,
    `from_store_id` INTEGER NOT NULL,
    `to_store_id` INTEGER NOT NULL,
    `transfer_date` DATE NOT NULL,
    `expected_return_date` DATE NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `outbound_status` TINYINT NOT NULL DEFAULT 0,
    `outbound_confirmed_at` DATETIME(3) NULL,
    `outbound_confirmed_by` INTEGER NULL,
    `remark` VARCHAR(500) NULL,
    `cancelled_at` DATETIME(3) NULL,
    `cancelled_by` INTEGER NULL,
    `cancel_reason` VARCHAR(500) NULL,
    `created_by` INTEGER NOT NULL,
    `updated_by` INTEGER NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `store_transfers_transfer_no_key`(`transfer_no`),
    INDEX `store_transfers_from_store_id_status_transfer_date_idx`(`from_store_id`, `status`, `transfer_date`),
    INDEX `store_transfers_to_store_id_status_transfer_date_idx`(`to_store_id`, `status`, `transfer_date`),
    INDEX `store_transfers_expected_return_date_status_idx`(`expected_return_date`, `status`),
    INDEX `store_transfers_outbound_status_idx`(`outbound_status`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `store_transfer_items` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `transfer_id` INTEGER NOT NULL,
    `item_name` VARCHAR(120) NOT NULL,
    `specification` VARCHAR(120) NULL,
    `batch_no` VARCHAR(100) NULL,
    `quantity` DECIMAL(12, 3) NOT NULL,
    `unit` VARCHAR(20) NOT NULL,
    `remark` VARCHAR(500) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `store_transfer_items_transfer_id_idx`(`transfer_id`),
    INDEX `store_transfer_items_item_name_idx`(`item_name`),
    INDEX `store_transfer_items_batch_no_idx`(`batch_no`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `store_transfer_returns` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `transfer_item_id` INTEGER NOT NULL,
    `quantity` DECIMAL(12, 3) NOT NULL,
    `return_date` DATE NOT NULL,
    `operator_id` INTEGER NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `confirmed_at` DATETIME(3) NULL,
    `confirmed_by` INTEGER NULL,
    `remark` VARCHAR(500) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `store_transfer_returns_transfer_item_id_created_at_idx`(`transfer_item_id`, `created_at`),
    INDEX `store_transfer_returns_status_idx`(`status`),
    INDEX `store_transfer_returns_return_date_idx`(`return_date`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `store_transfer_daily_sequences` (
    `sequence_date` DATE NOT NULL,
    `current_value` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`sequence_date`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `operation_logs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `actor_id` INTEGER NULL,
    `actor_role` INTEGER NULL,
    `actor_name` VARCHAR(100) NULL,
    `store_id` INTEGER NULL,
    `module` VARCHAR(50) NOT NULL,
    `action` VARCHAR(50) NOT NULL,
    `target_id` INTEGER NULL,
    `description` VARCHAR(500) NOT NULL,
    `ip` VARCHAR(45) NULL,
    `user_agent` VARCHAR(500) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `operation_logs_store_id_created_at_idx`(`store_id`, `created_at`),
    INDEX `operation_logs_actor_id_created_at_idx`(`actor_id`, `created_at`),
    INDEX `operation_logs_module_action_created_at_idx`(`module`, `action`, `created_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `users` ADD CONSTRAINT `users_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `herbs` ADD CONSTRAINT `herbs_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `products` ADD CONSTRAINT `products_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `products_diff_logs` ADD CONSTRAINT `products_diff_logs_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `products_diff_logs` ADD CONSTRAINT `products_diff_logs_product_id_fkey` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `products_diff_logs` ADD CONSTRAINT `products_diff_logs_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `products_diff_logs` ADD CONSTRAINT `products_diff_logs_related_log_id_fkey` FOREIGN KEY (`related_log_id`) REFERENCES `products_diff_logs`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `herb_locations` ADD CONSTRAINT `herb_locations_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `herb_location_assignments` ADD CONSTRAINT `herb_location_assignments_location_id_fkey` FOREIGN KEY (`location_id`) REFERENCES `herb_locations`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `herb_location_assignments` ADD CONSTRAINT `herb_location_assignments_herb_id_fkey` FOREIGN KEY (`herb_id`) REFERENCES `herbs`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `packages` ADD CONSTRAINT `packages_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `packages` ADD CONSTRAINT `packages_verified_by_fkey` FOREIGN KEY (`verified_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `packages` ADD CONSTRAINT `packages_modified_by_fkey` FOREIGN KEY (`modified_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `packages` ADD CONSTRAINT `packages_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `packages` ADD CONSTRAINT `packages_processing_plan_id_fkey` FOREIGN KEY (`processing_plan_id`) REFERENCES `processing_plans`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `prescriptions` ADD CONSTRAINT `prescriptions_doctor_id_fkey` FOREIGN KEY (`doctor_id`) REFERENCES `doctors`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `prescriptions` ADD CONSTRAINT `prescriptions_source_id_fkey` FOREIGN KEY (`source_id`) REFERENCES `dictionaries`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `prescriptions` ADD CONSTRAINT `prescriptions_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `prescriptions` ADD CONSTRAINT `prescriptions_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `processing_plans` ADD CONSTRAINT `processing_plans_prescription_id_fkey` FOREIGN KEY (`prescription_id`) REFERENCES `prescriptions`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `processing_plans` ADD CONSTRAINT `processing_plans_process_type_id_fkey` FOREIGN KEY (`process_type_id`) REFERENCES `dictionaries`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `processing_plans` ADD CONSTRAINT `processing_plans_notify_type_fkey` FOREIGN KEY (`notify_type`) REFERENCES `dictionaries`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `processing_plans` ADD CONSTRAINT `processing_plans_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `processing_plans` ADD CONSTRAINT `processing_plans_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `e6_doctor_mappings` ADD CONSTRAINT `e6_doctor_mappings_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `e6_doctor_mappings` ADD CONSTRAINT `e6_doctor_mappings_doctor_id_fkey` FOREIGN KEY (`doctor_id`) REFERENCES `doctors`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `e6_imports` ADD CONSTRAINT `e6_imports_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `e6_imports` ADD CONSTRAINT `e6_imports_prescription_id_fkey` FOREIGN KEY (`prescription_id`) REFERENCES `prescriptions`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `e6_imports` ADD CONSTRAINT `e6_imports_processing_plan_id_fkey` FOREIGN KEY (`processing_plan_id`) REFERENCES `processing_plans`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `login_logs` ADD CONSTRAINT `login_logs_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `email_verification_codes` ADD CONSTRAINT `email_verification_codes_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_configs` ADD CONSTRAINT `robot_configs_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_configs` ADD CONSTRAINT `robot_configs_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_configs` ADD CONSTRAINT `robot_configs_updated_by_fkey` FOREIGN KEY (`updated_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_event_configs` ADD CONSTRAINT `robot_event_configs_robot_id_fkey` FOREIGN KEY (`robot_id`) REFERENCES `robot_configs`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_event_configs` ADD CONSTRAINT `robot_event_configs_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_event_configs` ADD CONSTRAINT `robot_event_configs_updated_by_fkey` FOREIGN KEY (`updated_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_notification_events` ADD CONSTRAINT `robot_notification_events_operator_id_fkey` FOREIGN KEY (`operator_id`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_delivery_logs` ADD CONSTRAINT `robot_delivery_logs_event_id_fkey` FOREIGN KEY (`event_id`) REFERENCES `robot_notification_events`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `robot_delivery_logs` ADD CONSTRAINT `robot_delivery_logs_robot_id_fkey` FOREIGN KEY (`robot_id`) REFERENCES `robot_configs`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `print_templates` ADD CONSTRAINT `print_templates_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `print_templates` ADD CONSTRAINT `print_templates_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `store_transfers` ADD CONSTRAINT `store_transfers_from_store_id_fkey` FOREIGN KEY (`from_store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `store_transfers` ADD CONSTRAINT `store_transfers_to_store_id_fkey` FOREIGN KEY (`to_store_id`) REFERENCES `stores`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `store_transfers` ADD CONSTRAINT `store_transfers_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `store_transfers` ADD CONSTRAINT `store_transfers_outbound_confirmed_by_fkey` FOREIGN KEY (`outbound_confirmed_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `store_transfer_items` ADD CONSTRAINT `store_transfer_items_transfer_id_fkey` FOREIGN KEY (`transfer_id`) REFERENCES `store_transfers`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `store_transfer_returns` ADD CONSTRAINT `store_transfer_returns_transfer_item_id_fkey` FOREIGN KEY (`transfer_item_id`) REFERENCES `store_transfer_items`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `store_transfer_returns` ADD CONSTRAINT `store_transfer_returns_operator_id_fkey` FOREIGN KEY (`operator_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `store_transfer_returns` ADD CONSTRAINT `store_transfer_returns_confirmed_by_fkey` FOREIGN KEY (`confirmed_by`) REFERENCES `users`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `operation_logs` ADD CONSTRAINT `operation_logs_store_id_fkey` FOREIGN KEY (`store_id`) REFERENCES `stores`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
