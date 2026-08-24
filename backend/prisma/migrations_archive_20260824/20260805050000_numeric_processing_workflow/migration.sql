UPDATE `processing_plans`
SET `current_stage` = CASE
  WHEN `current_stage` IN ('DISPENSING', '1') THEN '1'
  WHEN `current_stage` IN ('DISPENSING_DONE', '2') THEN '2'
  WHEN `current_stage` IN ('SOAKING', '3') THEN '3'
  WHEN `current_stage` IN ('DECOCTING', '4') THEN '4'
  WHEN `current_stage` IN ('PACKAGING', '5') THEN '5'
  WHEN `current_stage` IN ('PACKAGING_DONE', '6') THEN '6'
  WHEN `current_stage` IN ('COMPLETED', '7') THEN '7'
  WHEN `current_stage` IS NULL AND `status` = 1 THEN '1'
  WHEN `current_stage` IS NULL AND `status` IN (2, 3, 4) THEN '7'
  ELSE NULL
END;

UPDATE `processing_equipment_usages`
SET `stage` = CASE
  WHEN `stage` IN ('SOAKING', '3') THEN '3'
  WHEN `stage` IN ('DECOCTING', '4') THEN '4'
  WHEN `stage` IN ('PACKAGING', '5') THEN '5'
  ELSE `stage`
END;

ALTER TABLE `processing_plans`
  MODIFY `current_stage` TINYINT NULL,
  DROP COLUMN `workflow_version`;

ALTER TABLE `processing_equipment_usages`
  MODIFY `stage` TINYINT NOT NULL;
