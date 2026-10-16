-- ============================================================
-- 知行食记 · 库结构一致性核对
-- 真源：docs/schema/（表 / 列 / 索引 / 外键 / 种子）
--
-- 用法：
--   mysql -h <host> -P <port> -u <user> --default-character-set=utf8mb4 bjtu_food \
--         < db/schema_sync_check.sql
--
-- 期望结果：
--   ① 缺失表     -> 0 行
--   ② 多余表     -> 0 行
--   ③ 列定义     -> 与 docs/schema/*.md 的「列定义」逐列一致
--   ④ 索引       -> 与 docs/schema/*.md 的「索引」一致（含外键自动索引）
--   ⑤ 外键       -> 名称 / 引用关系 / 级联规则与文档一致
--   ⑥ 选项种子   -> 5 条
-- ============================================================

SELECT '=== (1) 缺失的表（期望 8 张；应 0 行）===' AS check_item;

SELECT e.t AS missing_table
FROM (
  SELECT 'canteen' AS t UNION ALL SELECT 'stall' UNION ALL SELECT 'dish'
  UNION ALL SELECT 'dish_record' UNION ALL SELECT 'dish_issue'
  UNION ALL SELECT 'dish_issue_option' UNION ALL SELECT 'user' UNION ALL SELECT 'admin'
) e
LEFT JOIN information_schema.TABLES x
  ON x.TABLE_SCHEMA = DATABASE() AND x.TABLE_NAME = e.t
WHERE x.TABLE_NAME IS NULL;


SELECT '=== (2) 多余的表（不在新设计内；应 0 行）===' AS check_item;

SELECT TABLE_NAME AS unexpected_table
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME NOT IN
      ('canteen', 'stall', 'dish', 'dish_record', 'dish_issue', 'dish_issue_option', 'user', 'admin');


SELECT '=== (3) 列定义（与 docs/schema 逐表比对）===' AS check_item;

SELECT TABLE_NAME  AS tbl,
       COLUMN_NAME AS col,
       COLUMN_TYPE AS type,
       IS_NULLABLE AS nullable,
       COLUMN_DEFAULT AS dflt,
       COLUMN_KEY AS col_key,
       EXTRA       AS extra
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, ORDINAL_POSITION;


SELECT '=== (4) 索引（含外键自动索引）===' AS check_item;

SELECT TABLE_NAME AS tbl,
       INDEX_NAME AS idx,
       NON_UNIQUE AS non_unique,
       SEQ_IN_INDEX AS seq,
       COLUMN_NAME AS col
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;


SELECT '=== (5) 外键与级联规则（须与文档一致）===' AS check_item;

SELECT k.TABLE_NAME      AS tbl,
       k.CONSTRAINT_NAME AS fk,
       k.COLUMN_NAME     AS col,
       k.REFERENCED_TABLE_NAME AS ref_tbl,
       k.REFERENCED_COLUMN_NAME AS ref_col,
       r.DELETE_RULE     AS on_delete,
       r.UPDATE_RULE     AS on_update
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.TABLE_SCHEMA
 AND r.CONSTRAINT_NAME   = k.CONSTRAINT_NAME
 AND r.TABLE_NAME        = k.TABLE_NAME
WHERE k.TABLE_SCHEMA = DATABASE()
  AND k.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME;


SELECT '=== (6) 纠错选项种子（期望 5 条）===' AS check_item;

SELECT id, name, updated_at
FROM dish_issue_option
ORDER BY id;
