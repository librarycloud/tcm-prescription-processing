UPDATE `users`
SET `username` = NULL
WHERE `username` IS NOT NULL
  AND (`username` = `phone` OR `username` NOT REGEXP '[A-Za-z]');

UPDATE `admins`
SET `username` = NULL
WHERE `username` IS NOT NULL
  AND (`username` = `phone` OR `username` NOT REGEXP '[A-Za-z]');

ALTER TABLE `users`
  MODIFY `username` VARCHAR(64) NULL,
  ADD UNIQUE INDEX `users_username_key`(`username`);

ALTER TABLE `admins`
  MODIFY `username` VARCHAR(64) NULL,
  ADD UNIQUE INDEX `admins_username_key`(`username`);
