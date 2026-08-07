-- =============================================================
-- 五期迁移脚本：BCNF 清理（删除收藏/孤岛冗余字段）
-- 背景：
--   1. dish.favorite_count   收藏模块已整体移除（task-12.12），字段无写入源且恒 0，真冗余 → 删列
--   2. stall.avg_rating      孤岛字段（无维护源），档口评分已改实时聚合 → 删列
--   3. banner.type           与 target_type 冗余并存，契约统一 target_type → 删列
-- 幂等：通过 information_schema 判断列是否存在，可重复执行。
-- 执行：mysql -u <user> -p <pwd> bjtu_food < migration_phase5.sql
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP PROCEDURE IF EXISTS bcnf_cleanup;
DELIMITER $$
CREATE PROCEDURE bcnf_cleanup()
BEGIN
    -- 1. dish.favorite_count
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'favorite_count') THEN
        ALTER TABLE `dish` DROP COLUMN `favorite_count`;
    END IF;

    -- 2. stall.avg_rating
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'avg_rating') THEN
        ALTER TABLE `stall` DROP COLUMN `avg_rating`;
    END IF;

    -- 3. banner.type
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banner' AND COLUMN_NAME = 'type') THEN
        ALTER TABLE `banner` DROP COLUMN `type`;
    END IF;
END$$
DELIMITER ;

CALL bcnf_cleanup();
DROP PROCEDURE IF EXISTS bcnf_cleanup;

SET FOREIGN_KEY_CHECKS = 1;
