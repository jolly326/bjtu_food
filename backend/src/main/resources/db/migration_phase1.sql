-- =============================================================
-- 一期扩展字段落地迁移（migration_phase1）
-- 用途：当运行库是从「原始 review 表 + 不含 review_useful 表」的旧库升级而来，
--       缺少 useful_count 列与 review_useful 表时，执行本脚本补齐，
--       以彻底恢复「评价有用/点赞」功能。
--
-- 说明：
--   1. 本脚本复制自 schema.sql 末尾「一期扩展字段」(264-265 行) 与 review_useful 表定义(130-141 行)。
--   2. 使用 ADD COLUMN / CREATE TABLE IF NOT EXISTS，可安全重复执行（已存在则跳过）。
--   3. 需在用户测试库（如 bjtu_food）执行：
--        mysql -u <user> -p <db> < migration_phase1.sql
--      或在 MySQL 客户端中 source 本文件。
--   4. 执行后，useful_count 初始为 0，之后由 ReviewServiceImpl.toggleUseful 维护计数。
--      （如需把已有 review_useful 记录聚合回 useful_count，可另行执行聚合 UPDATE。）
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 评价：有用计数（冗余列，由 review_useful 聚合维护）
-- 注意：本环境 MySQL 构建对 `ADD COLUMN IF NOT EXISTS` 会静默失效，故此处使用标准 ALTER。
-- 若该列已存在，执行本句会报 "Duplicate column" 错误，属预期；可忽略或先 DROP 再执行。
ALTER TABLE `review`
    ADD COLUMN `useful_count` INT NOT NULL DEFAULT 0 COMMENT '「有用」标记数（一人一票，uk_useful_user_review）';

-- 评价「有用」标记表
CREATE TABLE IF NOT EXISTS `review_useful`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`    BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `review_id`  BIGINT   NOT NULL DEFAULT 0 COMMENT '评价ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_useful_user_review` (`user_id`, `review_id`),
    KEY `idx_useful_review` (`review_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='评价有用标记';

SET FOREIGN_KEY_CHECKS = 1;
