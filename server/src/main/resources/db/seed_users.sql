-- =============================================================
-- 食在交大 用户账号修复脚本（仅旧库升级时执行）
-- 背景：早期联调库用户为测试数据（admin 密码非 123456、无 super_admin、学生账号混乱），
--       执行本脚本统一重建为标准演示账号，密码均为 123456。
-- 注意：
--   * 从零建库（schema.sql + seed_data.sql）时【无需】执行本脚本——seed_data 已建立标准账号。
--   * 本脚本会 DELETE 用户表，仅用于旧库修复；执行前确认不会误删重要数据。
--   * id 显式指定，保证与旧库已有业务数据 user_id 引用尽量对齐（admin=1，学生=2~5）。
-- 执行：mysql -u <user> -p <pwd> bjtu_food < seed_users.sql
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM `user`;
INSERT INTO `user` (id, username, email, password, nickname, avatar, role, status, last_login_at) VALUES
(1, 'admin',    'admin@bjtu.edu.cn',   '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '管理员',     NULL, 'super_admin', 'active', NOW()),
(2, '2024001',  '2024001@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '交大干饭王', NULL, 'student',     'active', NOW()),
(3, '2024002',  '2024002@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '食堂常客',   NULL, 'student',     'active', NOW()),
(4, '2024003',  '2024003@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '深夜放毒',   NULL, 'student',     'active', NOW()),
(5, '2024004',  '2024004@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '奶茶三分甜', NULL, 'student',     'active', NOW());

SET FOREIGN_KEY_CHECKS = 1;
