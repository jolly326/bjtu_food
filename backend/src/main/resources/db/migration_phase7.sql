-- =============================================================
-- 七期迁移脚本：对齐 schema 与实体字段（幂等，可重复执行）
-- 背景：Dish 实体含 original_price / promo_price / spice_level / portion /
--       serve_period / limited，Stall 实体含 floor / window_no /
--       business_hours，但早期 schema.sql 的 dish/stall 建表语句缺少这些列，
--       导致 MyBatis-Plus 全列查询报 Unknown column。
--       本脚本幂等补齐，覆盖「已按旧 schema 建库」的环境。
-- 执行：mysql -u <user> -p <pwd> bjtu_food < migration_phase7.sql
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP PROCEDURE IF EXISTS align_dish_cols;
DELIMITER $$
CREATE PROCEDURE align_dish_cols()
BEGIN
    -- original_price（折扣前原价，单位：分）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'original_price') THEN
        ALTER TABLE `dish` ADD COLUMN `original_price` INT NULL DEFAULT NULL
            COMMENT '原价（折扣前，单位：分）；promo_price 非空视为有折扣' AFTER `price`;
    END IF;

    -- promo_price（促销价，单位：分）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'promo_price') THEN
        ALTER TABLE `dish` ADD COLUMN `promo_price` INT NULL DEFAULT NULL
            COMMENT '促销价（单位：分，可空）；非空视为有折扣' AFTER `original_price`;
    END IF;

    -- spice_level（辣度枚举）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'spice_level') THEN
        ALTER TABLE `dish` ADD COLUMN `spice_level` INT NOT NULL DEFAULT 0
            COMMENT '辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣' AFTER `tags`;
    END IF;

    -- portion（分量枚举）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'portion') THEN
        ALTER TABLE `dish` ADD COLUMN `portion` INT NOT NULL DEFAULT 1
            COMMENT '分量枚举：0=小 1=中 2=大' AFTER `spice_level`;
    END IF;

    -- serve_period（供应时段）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'serve_period') THEN
        ALTER TABLE `dish` ADD COLUMN `serve_period` VARCHAR(64) NULL DEFAULT NULL
            COMMENT '供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight' AFTER `portion`;
    END IF;

    -- limited（是否限量）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'limited') THEN
        ALTER TABLE `dish` ADD COLUMN `limited` INT NOT NULL DEFAULT 0
            COMMENT '是否限量（0=否 1=是）' AFTER `serve_period`;
    END IF;
END$$
DELIMITER ;
CALL align_dish_cols();
DROP PROCEDURE IF EXISTS align_dish_cols;

-- -------------------- 档口 stall：补齐实体字段 --------------------
DROP PROCEDURE IF EXISTS align_stall_cols;
DELIMITER $$
CREATE PROCEDURE align_stall_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'floor') THEN
        ALTER TABLE `stall` ADD COLUMN `floor` VARCHAR(16) NULL DEFAULT NULL
            COMMENT '楼层（如 1F/2F）' AFTER `location`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'window_no') THEN
        ALTER TABLE `stall` ADD COLUMN `window_no` VARCHAR(32) NULL DEFAULT NULL
            COMMENT '窗口号（如 3号窗口）' AFTER `floor`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'business_hours') THEN
        ALTER TABLE `stall` ADD COLUMN `business_hours` VARCHAR(64) NULL DEFAULT NULL
            COMMENT '营业时间，如 10:00-20:00' AFTER `window_no`;
    END IF;
END$$
DELIMITER ;
CALL align_stall_cols();
DROP PROCEDURE IF EXISTS align_stall_cols;

SET FOREIGN_KEY_CHECKS = 1;
