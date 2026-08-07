-- =============================================================
-- 四期迁移脚本：补齐 canteen 表经纬度列
-- 背景：schema.sql 的 canteen 定义含 latitude / longitude（距离排序用），
--       但早期建库的 canteen 表缺少这两列，导致 MyBatis-Plus selectList
--       全列查询报 Unknown column 'latitude' → 食堂列表接口 500。
-- 幂等：通过存储过程仅在列不存在时添加，可重复执行。
-- 执行：mysql -u <user> -p <pwd> bjtu_food < migration_phase4.sql
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP PROCEDURE IF EXISTS add_canteen_cols;
DELIMITER $$
CREATE PROCEDURE add_canteen_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'canteen'
                     AND COLUMN_NAME = 'latitude') THEN
        ALTER TABLE `canteen`
            ADD COLUMN `latitude` DECIMAL(10,6) NULL DEFAULT NULL COMMENT '纬度（GCJ-02，距离排序用）';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'canteen'
                     AND COLUMN_NAME = 'longitude') THEN
        ALTER TABLE `canteen`
            ADD COLUMN `longitude` DECIMAL(10,6) NULL DEFAULT NULL COMMENT '经度（GCJ-02，距离排序用）';
    END IF;
END$$
DELIMITER ;

CALL add_canteen_cols();
DROP PROCEDURE IF EXISTS add_canteen_cols;

-- 回填已有食堂坐标（与 seed_data.sql 的 INSERT 值一致，幂等 UPDATE）
UPDATE `canteen` SET `latitude` = 39.953800, `longitude` = 116.335400 WHERE `id` = 1 AND `latitude` IS NULL;
UPDATE `canteen` SET `latitude` = 39.954200, `longitude` = 116.335800 WHERE `id` = 2 AND `latitude` IS NULL;
UPDATE `canteen` SET `latitude` = 39.954600, `longitude` = 116.336200 WHERE `id` = 3 AND `latitude` IS NULL;
UPDATE `canteen` SET `latitude` = 39.955800, `longitude` = 116.331500 WHERE `id` = 4 AND `latitude` IS NULL;
UPDATE `canteen` SET `latitude` = 39.953000, `longitude` = 116.339000 WHERE `id` = 5 AND `latitude` IS NULL;
UPDATE `canteen` SET `latitude` = 39.954800, `longitude` = 116.335000 WHERE `id` = 6 AND `latitude` IS NULL;
UPDATE `canteen` SET `latitude` = 39.957000, `longitude` = 116.338000 WHERE `id` = 7 AND `latitude` IS NULL;

SET FOREIGN_KEY_CHECKS = 1;
