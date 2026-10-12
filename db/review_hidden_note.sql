-- ============================================================================
-- review.hidden_note 补列：隐藏附注
-- ============================================================================
-- 目标：
--   1) review 增加 hidden_note VARCHAR(200) NULL（默认 NULL），置于 is_hidden 之后
--   2) 与 docs/schema/review.md 列定义、处置 UPDATE 写路径（ReviewServiceImpl）
--      对齐：管理员隐藏评价时的可选附注，覆盖式，is_hidden = 0 时为 NULL
--
-- 背景：
--   该列随 2e86fda4 进入代码与文档，但此前未在生产库执行 ALTER ——
--   2026-10-12 直连生产库核验（SHOW CREATE TABLE review）确认缺失。
--
-- 执行前提：
--   - 远端库为 bjtu_food（MySQL 5.7 / TDSQL-C）
--   - review 表当前无 hidden_note 列（重复执行会报 Duplicate column）
--
-- 执行方式：
--   mysql -u root -p... bjtu_food < db/review_hidden_note.sql
-- ============================================================================

ALTER TABLE `bjtu_food`.`review`
    ADD COLUMN `hidden_note` VARCHAR(200) DEFAULT NULL
    COMMENT '隐藏附注（管理员可选，覆盖式，随处置同批写；is_hidden = 0 时为 NULL）'
    AFTER `is_hidden`;
