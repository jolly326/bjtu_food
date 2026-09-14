-- =============================================================
-- 存量 dish.audit_status 归一为 approved（一次性数据修正脚本）
-- =============================================================
-- 背景与授权：2026-09-15 用户拍板「项目框架与蓝图 v1」（project_spec.md §7.23 第 4 条）——
--   **菜品无独立审核**：管理员录入 / 编辑即写 approved 并直接生效；
--   dish.audit_status / dish.reject_reason 退役为历史列（列保留、不再扩展、后台与端上均无审核入口）。
--   历史存量中仍可能存在 pending / rejected 的菜品行，而小程序端仅展示 approved，
--   会形成「数据存在但端上不可见」的契约断裂。本脚本即为该断裂的一次性消除动作。
--
-- 执行方式（由用户执行，不由 agent 代跑）：
--   mysql -u root -p bjtu_food < server/src/main/resources/db/normalize_dish_audit_status.sql
--
-- 安全约定（务必遵守）：
--   1. **只改数据（UPDATE），不含 DROP / ALTER / CREATE**，不改动任何表结构；
--   2. **不并入 schema.sql 自动执行路径**（与 fix_rating_by_sec_state.sql 同一口径，见 spec §8）；
--   3. **幂等**：WHERE 仅命中非 approved 的行，重复执行第二次影响 0 行，可安全重跑；
--   4. **执行前先备份** dish 表（至少 id / audit_status 两列）：
--        CREATE TABLE dish_bak_audit_status AS SELECT id, audit_status FROM dish;
--
-- 关联文档：project_spec.md §7.23 第 4 条、§8「待运维执行」；docs/database.md §3.4 dish。
-- =============================================================

USE `bjtu_food`;

-- ① 执行前体检：列出将被修改的行（可先单独执行本段确认影响面）
SELECT `id`, `name`, `audit_status`, `reject_reason`
FROM `dish`
WHERE `audit_status` IS NULL
   OR `audit_status` = ''
   OR `audit_status` <> 'approved';

-- ② 归一化：把所有非 approved（含 NULL / 空串 / rejected）的存量菜品置为 approved
UPDATE `dish`
SET `audit_status` = 'approved'
WHERE `audit_status` IS NULL
   OR `audit_status` = ''
   OR `audit_status` <> 'approved';

-- ③ 可选（默认注释，需用户另行确认后再放开）：随菜品审核语义退役，清空历史 reject_reason。
--    注意：该列已无展示入口（Web 菜品详情不再回显），清空仅为消除误读；属数据清理，故默认不执行。
-- UPDATE `dish`
-- SET `reject_reason` = NULL
-- WHERE `reject_reason` IS NOT NULL;

-- ④ 执行后校验：应返回 0 行（全部已为 approved）
SELECT `id`, `name`, `audit_status`
FROM `dish`
WHERE `audit_status` IS NULL
   OR `audit_status` = ''
   OR `audit_status` <> 'approved';
