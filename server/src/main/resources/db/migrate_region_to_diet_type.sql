-- =============================================================
-- 食在交大 · 存量数据转换脚本（dish 描述维度：spice_level / region → 四维）
-- =============================================================
-- 背景（2026-09-20 用户拍板 §7.28 / dish-detail-remediation D7）：
--   菜品描述维度由「辣度 spice_level + 风味/菜系 region」替换为四维：
--     diet_type（荤素/饮食属性）/ ingredients（主料）/ flavor_tags（口味）/ serve_temp（冷热）。
--   四维机器值（英文，中文仅由端上展示层映射）：
--     diet_type:    meat / half / veg / halal
--     ingredients:  pork / beef / lamb / chicken / duck / fish / egg / tofu / mushroom / veg / noodle / rice（逗号分隔）
--     flavor_tags:  spicy / numbing / sour / sweet / salty / umami / light / heavy（逗号分隔）
--     serve_temp:   hot / room / ice
--
-- ⚠️ 本脚本**只交付、不由 agent 代跑**；是否执行、何时执行由用户在部署前自行决定（PR-04 留痕）。
-- ⚠️ 结构与列的新增/删除的唯一权威脚本是 `schema.sql`（幂等段 add/drop 四维与旧列）。
--    本脚本**只做数据转换，不 DROP 任何列**；旧列 spice_level / region 的 DROP 由 schema.sql 负责。
--
-- ───────────── 转换取舍说明（重要）─────────────
-- 1) region='清真' → diet_type='halal'【本脚本默认自动转换】
--    依据：spec §7.28 明确「原 region='清真' 为饮食约束、非口味 → 迁入 diet_type」，
--    语义映射唯一且无歧义，故本脚本自动执行（幂等，只覆盖 diet_type 为空的行）。
--    其余 region 值（川湘 / 粤式 / 东北 / 西北 等）为「菜系」，已确认放弃，不迁入任何新列。
-- 2) spice_level（0/1/2/3 = 不辣/微辣/中辣/重辣）→ flavor_tags 是否补 'spicy'【本脚本**默认不转换**，由用户决定】
--    依据与取舍：spec §7.28 规定「辣」语义由 flavor_tags 的 'spicy' 承载，方向上有依据；
--    但该映射**不无损**——旧 4 档辣度会坍缩为单值 'spicy'（微辣/中辣/重辣不再可区分），
--    且「不加辣(0) 是否要显式落 'light'」「微辣是否算辣」属产品判断，脚本无法替用户决定。
--    故**默认不自动转换**；如需转换，请自行取消下方【可选块】注释后执行。
--    执行前置：该块要求 spice_level 列仍存在，即须在运行 `schema.sql`（会 DROP 该列）**之前**执行。
-- 3) ingredients（主料）/ serve_temp（冷热）：无任何旧列可推断，**不自动转换**，由管理员在 Web 后台补录。
--
-- ───────────── 执行时机（两种都安全，脚本自身幂等）─────────────
--   · 推荐：在 `schema.sql` **之前**执行 —— 此时 region / spice_level 列尚在，可据其转换；
--     脚本会在四维列缺失时先补齐列，故可独立运行（也便于执行下方【可选块】）。
--   · 也可在 `schema.sql` **之后**执行 —— 此时 region / spice_level 已被 DROP，
--     脚本对这两列的转换自动跳过（无列可读），不会报错；四维列的 ADD 亦已由 schema.sql 完成。
--
-- 执行：mysql -u <user> -p -h localhost bjtu_food < migrate_region_to_diet_type.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS `bjtu_food` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `bjtu_food`;

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `convert_legacy_dish_dimensions`;
DELIMITER $$
CREATE PROCEDURE `convert_legacy_dish_dimensions`()
BEGIN
    -- 0) 四维列补齐（幂等）：保证本脚本可独立于 schema.sql 运行（列缺失时先建）
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'diet_type'
    ) THEN
        ALTER TABLE `dish`
            ADD COLUMN `diet_type` VARCHAR(16) NULL DEFAULT NULL COMMENT '荤素/饮食属性：meat=荤 / half=半荤 / veg=素 / halal=清真';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'ingredients'
    ) THEN
        ALTER TABLE `dish`
            ADD COLUMN `ingredients` VARCHAR(255) NULL DEFAULT NULL COMMENT '主料/食材（逗号分隔机器值）：pork/beef/lamb/chicken/duck/fish/egg/tofu/mushroom/veg/noodle/rice';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'flavor_tags'
    ) THEN
        ALTER TABLE `dish`
            ADD COLUMN `flavor_tags` VARCHAR(128) NULL DEFAULT NULL COMMENT '口味（逗号分隔机器值）：spicy/numbing/sour/sweet/salty/umami/light/heavy';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'serve_temp'
    ) THEN
        ALTER TABLE `dish`
            ADD COLUMN `serve_temp` VARCHAR(16) NULL DEFAULT NULL COMMENT '冷热：hot=热食 / room=常温 / ice=冰';
    END IF;

    -- 1) region='清真' → diet_type='halal'（仅在 region 列尚在时执行；饮食约束语义迁移，语义唯一无歧义）
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'region'
    ) THEN
        UPDATE `dish`
        SET `diet_type` = 'halal'
        WHERE `region` = '清真'
          AND (`diet_type` IS NULL OR `diet_type` = '');
    END IF;

    -- 2) spice_level → flavor_tags：**默认不做转换**（取舍见文件头说明第 2 条）。
    --    如需转换，请取消下方【可选块】注释后单独执行（须在 schema.sql 之前，spice_level 列尚存时）：
    --
    --    【可选块 · 取消注释即启用】
    --    UPDATE `dish` SET `flavor_tags` = 'spicy'
    --    WHERE `spice_level` >= 1 AND (`flavor_tags` IS NULL OR `flavor_tags` = '');
    --    【可选块结束】
END$$
DELIMITER ;

CALL `convert_legacy_dish_dimensions`();
DROP PROCEDURE IF EXISTS `convert_legacy_dish_dimensions`;

-- 说明：ingredients / serve_temp 无旧列可推断，需在 Web 管理后台补录（或按业务自行 UPDATE）。
-- 旧列 spice_level / region 的 DROP 由 `schema.sql` 末尾 drop_dish_description_dimensions 幂等段执行。
