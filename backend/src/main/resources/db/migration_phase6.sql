-- =============================================================
-- 六期迁移脚本：补齐评论「有用」功能缺失的表/列
-- 背景：migration_phase2 早期版本创建的 moment_comment 表缺少 useful_count 列，
--       且未创建 moment_comment_useful 表（task-12.4 评论有用点赞）。
--       走 phase2 路径建库的环境，MomentServiceImpl.toggleCommentUseful()
--       会报 Unknown column 'useful_count' / Table doesn't exist。
-- 修复：幂等补齐（information_schema 判断，可重复执行）。
-- 执行：mysql -u <user> -p <pwd> bjtu_food < migration_phase6.sql
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 补 moment_comment.useful_count 列（若缺）
DROP PROCEDURE IF EXISTS fix_mc_useful_count;
DELIMITER $$
CREATE PROCEDURE fix_mc_useful_count()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'moment_comment' AND COLUMN_NAME = 'useful_count') THEN
        ALTER TABLE `moment_comment`
            ADD COLUMN `useful_count` INT NOT NULL DEFAULT 0 COMMENT '「有用 👍」计数（一人一票）' AFTER `content`;
    END IF;
END$$
DELIMITER ;
CALL fix_mc_useful_count();
DROP PROCEDURE IF EXISTS fix_mc_useful_count;

-- 2. 建 moment_comment_useful 表（若缺）
CREATE TABLE IF NOT EXISTS `moment_comment_useful`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '标记ID',
    `user_id`    BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `comment_id` BIGINT   NOT NULL DEFAULT 0 COMMENT '评论ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_useful_user_comment` (`user_id`, `comment_id`),
    KEY `idx_useful_comment` (`comment_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='动态评论有用标记';

SET FOREIGN_KEY_CHECKS = 1;
