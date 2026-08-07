-- =============================================================
-- 食在交大 - 修复脚本 (idempotent / safe to re-run)
-- 1) 创建缺失的 broadcast 表
-- 2) 插入文档约定的测试账号 (BCrypt hash of "123456", strength 10)
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 首页广播通知条 (A.14) --------------------
CREATE TABLE IF NOT EXISTS `broadcast`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '广播ID',
    `title`          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '广播标题',
    `content`        VARCHAR(512) NOT NULL DEFAULT '' COMMENT '广播正文（首页 ticker 展示文本）',
    `broadcast_type` VARCHAR(32)  NOT NULL DEFAULT 'NOTICE' COMMENT '广播类型：NOTICE/ACTIVITY/DISH/URL/NONE（首页按类型分发跳转）',
    `target_id`      BIGINT       NULL     DEFAULT NULL COMMENT '跳转目标ID（broadcast_type=DISH 时填菜品ID）',
    `target_url`     VARCHAR(512) NULL     DEFAULT NULL COMMENT '跳转目标URL（broadcast_type=URL 时填外链）',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序权重（越小越靠前）',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled / disabled',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_broadcast_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='首页广播通知条';

-- -------------------- 测试账号 (README 约定: 123456) --------------------
-- BCrypt hash of "123456" generated with org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder (strength 10)
-- INSERT IGNORE so re-running is safe (unique key on username)
INSERT IGNORE INTO `user`
    (`username`, `email`, `password`, `nickname`, `role`, `status`)
VALUES
    ('20240001', '20240001@bjtu.edu.cn', '$2a$10$jTvjqa/h7GZhFm2A5i1An.S0wFWl3yJ51M9RcKEcL2OqU7on6DpfS', '测试学生', 'student', 'active'),
    ('admin001', 'admin001@bjtu.edu.cn', '$2a$10$jTvjqa/h7GZhFm2A5i1An.S0wFWl3yJ51M9RcKEcL2OqU7on6DpfS', '测试管理员', 'admin', 'active');

SET FOREIGN_KEY_CHECKS = 1;
