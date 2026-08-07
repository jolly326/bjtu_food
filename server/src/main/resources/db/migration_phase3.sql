-- =============================================================
-- 三期迁移脚本：补齐 dish 表二期新增字段
-- 背景：Dish 实体在二期增加了 spice_level / portion / serve_period / limited
--       四个字段，但既有 dish 表（schema.sql 创建）未包含这些列，
--       导致 MyBatis-Plus selectList 全列查询报 Unknown column 'spice_level'。
-- 幂等：通过存储过程仅在列不存在时添加，可重复执行。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP PROCEDURE IF EXISTS add_dish_cols;
DELIMITER $$
CREATE PROCEDURE add_dish_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'dish'
                     AND COLUMN_NAME = 'spice_level') THEN
        ALTER TABLE `dish`
            ADD COLUMN `spice_level` INT NOT NULL DEFAULT 0 COMMENT '辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'dish'
                     AND COLUMN_NAME = 'portion') THEN
        ALTER TABLE `dish`
            ADD COLUMN `portion` INT NOT NULL DEFAULT 1 COMMENT '分量枚举：0=小 1=中 2=大';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'dish'
                     AND COLUMN_NAME = 'serve_period') THEN
        ALTER TABLE `dish`
            ADD COLUMN `serve_period` VARCHAR(64) NULL DEFAULT NULL COMMENT '供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'dish'
                     AND COLUMN_NAME = 'limited') THEN
        ALTER TABLE `dish`
            ADD COLUMN `limited` INT NOT NULL DEFAULT 0 COMMENT '是否限量（0=否 1=是）';
    END IF;
END$$
DELIMITER ;

CALL add_dish_cols();
DROP PROCEDURE IF EXISTS add_dish_cols;

-- -------------------- 档口 stall：补齐二期新增字段 --------------------
DROP PROCEDURE IF EXISTS add_stall_cols;
DELIMITER $$
CREATE PROCEDURE add_stall_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'stall'
                     AND COLUMN_NAME = 'floor') THEN
        ALTER TABLE `stall`
            ADD COLUMN `floor` VARCHAR(16) NULL DEFAULT NULL COMMENT '楼层（如 1F/2F）';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'stall'
                     AND COLUMN_NAME = 'window_no') THEN
        ALTER TABLE `stall`
            ADD COLUMN `window_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '窗口号（如 3号窗口）';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'stall'
                     AND COLUMN_NAME = 'business_hours') THEN
        ALTER TABLE `stall`
            ADD COLUMN `business_hours` VARCHAR(64) NULL DEFAULT NULL COMMENT '营业时间，如 10:00-20:00';
    END IF;
END$$
DELIMITER ;

CALL add_stall_cols();
DROP PROCEDURE IF EXISTS add_stall_cols;

-- -------------------- 反馈 user_feedback：补齐 updated_at --------------------
DROP PROCEDURE IF EXISTS add_feedback_cols;
DELIMITER $$
CREATE PROCEDURE add_feedback_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'user_feedback'
                     AND COLUMN_NAME = 'updated_at') THEN
        ALTER TABLE `user_feedback`
            ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
    END IF;
END$$
DELIMITER ;

CALL add_feedback_cols();
DROP PROCEDURE IF EXISTS add_feedback_cols;

SET FOREIGN_KEY_CHECKS = 1;
