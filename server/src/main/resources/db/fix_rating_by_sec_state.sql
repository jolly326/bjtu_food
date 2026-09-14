-- =============================================================
-- 食在交大 · 评分历史数据一次性重算脚本（手工执行）
-- 文件：server/src/main/resources/db/fix_rating_by_sec_state.sql
-- =============================================================
-- 用途：
--   按现行评分统计口径，对 dish.avg_rating / dish.rating_count 做一次**全量重算**，
--   修正历史存量数据因口径变更而产生的漂移。
--
-- 适用场景（背景）：
--   评分计入口径已改为「只计入 is_hidden = 0 且 sec_state = 'pass' 的评价」。
--   在此口径生效之前，被内容安检拦下（sec_state='review'）或人工复核不通过
--   （sec_state='rejected'）的评价，曾一度被计入 dish.avg_rating / dish.rating_count，
--   导致存量数据偏大、与端上可见评价数背离。本脚本按新口径对所有菜品重算一次。
--
-- 幂等性：
--   **可重复执行，结果一致**。本脚本为全量覆盖式 UPDATE（子查询 AVG/COUNT 整体写回），
--   不依赖前次状态、不做增量累加，重复执行不会二次偏移。
--
-- 执行时机：
--   部署本次变更后，**手工执行一次**即可。
--   ⚠️ 本脚本**不得**并入 schema.sql 的自动执行路径（避免每次启动全表重算）。
--
-- 执行前建议：
--   **先备份 dish 表**（至少备份 id / avg_rating / rating_count 三列），以便必要时回滚：
--     CREATE TABLE dish_rating_backup_20260914 AS
--       SELECT id, avg_rating, rating_count FROM dish;
--
-- 口径说明（与代码保持一致，勿单独改动本脚本口径）：
--   计入口径真源：dish/service/impl/DishServiceImpl#recalcRating 调用
--   DishMapper.xml 的 recalcRatingBySubquery（增量路径）；
--   以及 DishMapper.xml 的 selectRatingDistribution（分布统计）。三者必须同口径。
--   注：review.sec_state 列为 NOT NULL DEFAULT 'pass'，无 NULL 分支。
--
-- 本脚本只做数据修正：不含任何 DROP / ALTER 等结构变更。
-- =============================================================

-- 自包含选库（与 schema.sql / seed_data.sql 风格一致，避免误落到默认库）
USE `bjtu_food`;

-- 按新口径全量重算 dish.avg_rating / dish.rating_count。
-- 无匹配评价的菜品：avg_rating 落 0、rating_count 落 0（与代码 IFNULL(...,0) 兜底一致）。
UPDATE `dish` d
SET d.`avg_rating` = IFNULL(
        (SELECT ROUND(AVG(r.`rating`), 1)
         FROM `review` r
         WHERE r.`dish_id` = d.`id`
           AND r.`is_hidden` = 0
           AND r.`sec_state` = 'pass'),
        0),
    d.`rating_count` = (SELECT COUNT(*)
                        FROM `review` r
                        WHERE r.`dish_id` = d.`id`
                          AND r.`is_hidden` = 0
                          AND r.`sec_state` = 'pass');
