-- 食堂 / 档口封面图（仅运营端管理使用；学生端不消费，client 不变）
-- 项目无 alembic（手工建表）；本脚本**幂等**：列已存在时跳过，可安全重复执行。
-- 执行前请备份数据库。

SET @add_canteen_image = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteen' AND COLUMN_NAME = 'image') = 0,
    'ALTER TABLE canteen ADD COLUMN image VARCHAR(255) NULL AFTER activate',
    'DO 0'
  )
);
PREPARE stmt1 FROM @add_canteen_image;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @add_stall_image = (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'image') = 0,
    'ALTER TABLE stall ADD COLUMN image VARCHAR(255) NULL AFTER window_no',
    'DO 0'
  )
);
PREPARE stmt2 FROM @add_stall_image;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
