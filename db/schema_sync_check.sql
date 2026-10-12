-- ============================================================================
-- 远端数据库 `bjtu_food` 与 docs/schema 的结构同步检查
-- ============================================================================
-- 适用机器：必须能直连 10.7.110.195:3306 的 MySQL 账号
-- 当前本机无法连接（ERROR 2003），因此本文件只做检查，不执行任何 DDL。
--
-- 若你要我“确保远端设计与 docs/schema 同步”，下面的查询就是你给我的验收：
--   1) 两张表存在
--   2) correction / moderation_task 的列、键、默认值、索引、FK 与 docs/schema
--      中定义的完全一致
--   3) 没有回归额外列（例如 correction 里不应出现 status/outcome/handled_by/
--      handled_at/updated_at；moderation_task 里也不应出现 updated_at）
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
  REFERENCED_COLUMN_NAME,
  UPDATE_RULE,
  DELETE_RULE
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
