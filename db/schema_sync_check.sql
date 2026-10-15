-- ============================================================================
-- 远端数据库 `bjtu_food` 与 docs/schema 的结构同步检查
-- ============================================================================
-- 适用机器：TencentCDB sh-cynosdbmysql-grp-bc1u6r82.sql.tencentcdb.com:27926 (bjtu_food, root)
-- 2026-10-15 已同步：远端库与当前 docs/schema（唯一真源）对齐
-- （1) email_verification_code、dish_attribute_value 已移除 docs 未注册的唯一索引
-- 2) moderation_task idx_task_handled_by 保留为 fk_task_handled_by 依赖的外键索引）
--
-- 本文件用于同步前检查与同步后验收：20 张表的列、键、默认值、索引、FK 与
-- docs/schema 定义对照；查询清零超出 docs 定义的对象即视为文档 → 代码 →
-- 生产库一致。
-- ============================================================================

-- 1) 确认必要表存在
SELECT
  TABLE_SCHEMA,
  TABLE_NAME,
  TABLE_ROWS,
  CREATE_OPTIONS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'bjtu_food'
  AND TABLE_NAME IN ('correction', 'moderation_task');

-- 2) correction 当前列结构
SELECT
  COLUMN_NAME,
  COLUMN_TYPE,
  IS_NULLABLE,
  COLUMN_DEFAULT,
  COLUMN_KEY,
  EXTRA,
  CHARACTER_SET_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'bjtu_food'
  AND TABLE_NAME = 'correction'
ORDER BY ORDINAL_POSITION;

-- 3) moderation_task 当前列结构
SELECT
  COLUMN_NAME,
  COLUMN_TYPE,
  IS_NULLABLE,
  COLUMN_DEFAULT,
  COLUMN_KEY,
  EXTRA,
  CHARACTER_SET_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'bjtu_food'
  AND TABLE_NAME = 'moderation_task'
ORDER BY ORDINAL_POSITION;

-- 4) correction 当前索引
SELECT
  INDEX_NAME,
  COLUMN_NAME,
  SEQ_IN_INDEX,
  NON_UNIQUE,
  INDEX_TYPE,
  COMMENT
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'bjtu_food'
  AND TABLE_NAME = 'correction'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 5) moderation_task 当前索引
SELECT
  INDEX_NAME,
  COLUMN_NAME,
  SEQ_IN_INDEX,
  NON_UNIQUE,
  INDEX_TYPE,
  COMMENT
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'bjtu_food'
  AND TABLE_NAME = 'moderation_task'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 6) 当前外键
SELECT
  CONSTRAINT_NAME,
  TABLE_NAME,
  COLUMN_NAME,
  REFERENCED_TABLE_NAME,
  REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'bjtu_food'
  AND REFERENCED_TABLE_NAME IS NOT NULL
  AND TABLE_NAME IN ('correction', 'moderation_task')
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- 7) 反模式检查：不应存在的列
SELECT
  'correction' AS TABLE_NAME,
  COLUMN_NAME AS BAD_COLUMN
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'bjtu_food'
  AND TABLE_NAME = 'correction'
  AND COLUMN_NAME IN ('status', 'outcome', 'handled_by', 'handled_at', 'updated_at');

SELECT
  'moderation_task' AS TABLE_NAME,
  COLUMN_NAME AS BAD_COLUMN
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'bjtu_food'
  AND TABLE_NAME = 'moderation_task'
  AND COLUMN_NAME IN ('updated_at');
-- 10) 2026-10-15 同步后确认：docs 索引表未注册的对象已移除（远端库已对齐）
--     email_verification_code 索引（期待：PRIMARY / idx_evc_email / idx_evc_expires）
SELECT INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='bjtu_food' AND TABLE_NAME='email_verification_code' ORDER BY INDEX_NAME;
--     dish_attribute_value 索引（期待：PRIMARY / idx_value_dimension）
SELECT INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='bjtu_food' AND TABLE_NAME='dish_attribute_value' ORDER BY INDEX_NAME;
--     moderation_task 索引（期待：PRIMARY / uk_task_type_target / idx_task_status_created；fk_task_handled_by 依赖索引 idx_task_handled_by 必须保留）
SELECT INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='bjtu_food' AND TABLE_NAME='moderation_task' ORDER BY INDEX_NAME;
--     结论：以上三张表均与 docs/schema 索引表一致。
