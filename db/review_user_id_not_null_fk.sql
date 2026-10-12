-- ============================================================================
-- review.user_id 收紧：NOT NULL + 外键
-- ============================================================================
-- 目标：
--   1) review.user_id 变为 BIGINT NOT NULL，移除 DEFAULT '0'
--   2) 增加外键 fk_review_user -> user(id)，级联策略无级联
--
-- 执行前提：
--   - 远端库为 bjtu_food
--   - 当前 review.user_id 无 0 行，否则需要先迁移/清理
--   - user.id 为 BIGINT NOT NULL AUTO_INCREMENT主键
--
-- 执行方式：
--   mysql -u root -p... bjtu_food < db/review_user_id_not_null_fk.sql
-- ============================================================================

-- 1) 移除默认值，并保证列仍然是 NOT NULL
ALTER TABLE `bjtu_food`.`review`
    MODIFY COLUMN `user_id` BIGINT NOT NULL;

-- 2) 增加外键，目标 user.id
ALTER TABLE `bjtu_food`.`review`
    ADD CONSTRAINT `fk_review_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `bjtu_food`.`user` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION;
