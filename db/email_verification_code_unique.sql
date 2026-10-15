-- ============================================================================
-- email_verification_code 唯一性约束：行业标准设计
-- ============================================================================
-- 业界标准：
--   1) 短信/邮件验证码**哈希存储**（本库已用 `code_hash VARCHAR(128)`），不存明文。
--   2) 唯一约束在"户 + 码"组合上：`UNIQUE (email, code_hash)`。
--      含义 = 同一邮箱的同码在库内不允许出现两条（防止并发下两次请求拿到
--      同一激活码；历史码可长期保留，便审计）。每发新码 = 新插入一行，旧码
--      通过 `used_at`/`expires_at` 标记过期，不硬删（审计与幂等）。
--   3) 按 (email) 查时再靠索引：`idx_evc_email` 已存在；清理过期码用
--      `idx_evc_expires`（`expires_at` 单列索引），不在唯一键上带 `expires_at`。
--      若业务改成"同一邮箱仅保留最新一码"，应改为 `UNIQUE (email)` 并在
--      发送路径先 `DELETE ... WHERE email=? AND code_hash<>? OR used_at IS NOT NULL`。
--
-- 数据风险评估：生产库 `email_verification_code` 当前 0 行，导入约束安全。
--
-- 执行方式：
--   mysql -u root -p... bjtu_food < db/email_verification_code_unique.sql
-- ============================================================================

ALTER TABLE `bjtu_food`.`email_verification_code`
    ADD UNIQUE KEY `uk_email_code_hash` (`email`, `code_hash`)
    COMMENT '同一邮箱的同一验证码仅允许存在一条（含历史无效行），配合 used_at/expires_at 单次使用';
