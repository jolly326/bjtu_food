-- ============================================================
-- 知行食记 · 数据库结构变更（对应 docs/schema/ 文档）
-- 执行环境：本地开发库 + 远程腾讯云 TDSQL-C
-- 执行前请先备份数据库！
-- ============================================================

USE bjtu_food;

-- 1. canteen：新增 activate 字段
ALTER TABLE canteen
  ADD COLUMN activate BOOLEAN NOT NULL DEFAULT TRUE AFTER name;

-- 2. stall：新增 activate 字段
ALTER TABLE stall
  ADD COLUMN activate BOOLEAN NOT NULL DEFAULT TRUE AFTER name;

-- 3. dish：status → activate；stall_id 加外键 RESTRICT；索引变更
ALTER TABLE dish
  DROP INDEX idx_dish_status;

ALTER TABLE dish
  CHANGE COLUMN status activate BOOLEAN NOT NULL DEFAULT TRUE AFTER image;

ALTER TABLE dish
  ADD INDEX idx_dish_activate (activate);

ALTER TABLE dish
  ADD CONSTRAINT fk_dish_stall FOREIGN KEY (stall_id)
    REFERENCES stall(id) ON DELETE RESTRICT;

-- 4. dish_rating：reacted_at → updated_at；索引 (dish_id) → (dish_id, liked)
ALTER TABLE dish_rating
  CHANGE COLUMN reacted_at updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE dish_rating
  DROP INDEX ix_rating_dish;

ALTER TABLE dish_rating
  ADD INDEX ix_rating_dish (dish_id, liked);

-- ============================================================
-- 变更完成。建议执行以下验证 SQL 确认结构一致
-- ============================================================

-- 验证列定义
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 验证外键
SELECT k.TABLE_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME, r.DELETE_RULE
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.TABLE_SCHEMA = DATABASE()
  AND k.REFERENCED_TABLE_NAME IS NOT NULL;

-- 验证索引
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;