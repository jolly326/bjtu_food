-- =============================================================
-- 食在交大 种子数据脚本（重置服务器数据库用，一次性执行，非自动加载）
-- =============================================================
-- 用途：重置服务器数据库时灌入演示/基础数据（用户、食堂、档口、
--       菜品、评价、反馈、通知等），使三端有完整联调数据。
-- 本脚本自包含：自动建库并切换 USE bjtu_food（与 schema.sql 一致，库不存在时先建库）。
-- 执行前提：已按 schema.sql 建好全部表与最终字段；本脚本不建表。
-- 执行：mysql -u <user> -p -h localhost < seed_data.sql
-- 注意：本脚本部分段落（user_feedback / notification）采用先清后插，可重复执行；
--       其余段落（user / dish 等）重复执行会重复插入，重置时请先清库再运行。
-- 金额字段单位：分（如 1600 = 16.00 元）
-- images 置 NULL，由前端占位图（emoji）优雅降级，避免小程序外链域名限制。
-- =============================================================

-- 自包含建库选库：避免在未选中库时 INSERT 落入默认库触发 1044 权限错误
CREATE DATABASE IF NOT EXISTS `bjtu_food` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `bjtu_food`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 用户（评价/通知/反馈等均依赖） --------------------
-- 【2026-09-16 用户拍板「零消费即删除」口径】
--   · user.password / user.unionid 两列已退役删除（password 零读、unionid 只写不读），
--     列清单已同步移除（否则新库报 Unknown column）；存量库由 schema.sql 末尾
--     drop_zero_consumer_columns 幂等段清理。
--   · user.role / user.last_login_at 已于 2026-09-15 用户拍板退役（角色收敛恒 student、登录时间只写不读），
--     列清单与各行值已同步移除（否则新库报 Unknown column）。
INSERT INTO `user` (username, email, nickname, avatar, status) VALUES
('2024001',  '2024001@bjtu.edu.cn', '交大干饭王',  NULL, 'active'),
('2024002',  '2024002@bjtu.edu.cn', '食堂常客',    NULL, 'active'),
('2024003',  '2024003@bjtu.edu.cn', '深夜放毒',    NULL, 'active'),
('2024004',  '2024004@bjtu.edu.cn', '奶茶三分甜',  NULL, 'active'),
('admin',    'admin@bjtu.edu.cn',   '管理员',      NULL, 'active');

-- 注：菜品品类 category 表与 dish.category_id 列已整链退役（2026-09-15 用户拍板），
--     本脚本不再灌入品类种子数据（否则新库报 Unknown column / Unknown table）。

-- -------------------- 食堂（共 7 个；id=1 为学一食堂，档口/菜品 canteen_id 引用以此对齐） --------------------
-- 注：食堂已去实体化（2026-09-14 §7.14），status/audit_status/reject_reason/created_by 列已下线（2026-09-15 阶段4 追加 created_by），不再写入（否则新库报 Unknown column）
-- 注（2026-09-20 拍板）：canteen.latitude / canteen.longitude 坐标列已整链下线（位置表达收敛为 食堂 · 楼层 · 档口名），
--     列清单与该两列取值已同步移除（否则新库报 Unknown column）；存量库由 schema.sql 末尾 drop_canteen_coordinates 幂等段清理。
INSERT INTO canteen (name, location, description, sort_order) VALUES
('学一食堂', '学苑区',     '综合食堂，家常风味', 1),
('学二食堂', '学苑区一栋', '明亮整洁，家常味道', 2),
('学三食堂', '学苑区二栋', '品类丰富，平价美味', 3),
('明湖餐厅', '明湖旁',     '湖景餐厅，聚餐首选', 4),
('嘉园餐厅', '嘉园公寓',   '夜宵与小吃天堂',     5),
('清真食堂', '学苑区',     '清真风味，干净卫生', 6),
('留园餐厅', '留园区',     '精致小炒与面点',     7);

-- -------------------- 档口（canteen_id 对应上面的食堂） --------------------
-- 注：档口已去实体化（2026-09-14 §7.14），status/audit_status/reject_reason/created_by 列已下线（2026-09-15 阶段4 追加 created_by），不再写入（否则新库报 Unknown column）
INSERT INTO stall (canteen_id, name, location, description, sort_order) VALUES
(1, '学一基本伙食', '学一食堂一层', '平价家常菜',       2),
(1, '学一面点坊',   '学一食堂一层', '现做面点与汤包',   3),
(2, '学二快餐档',   '学二食堂',     '快捷套餐',         1),
(2, '学二盖饭档',   '学二食堂',     '各式盖饭',         2),
(3, '学三麻辣烫',   '学三食堂',     '自选麻辣烫',       1),
(3, '学三粥铺',     '学三食堂',     '养生粥品',         2),
(4, '明湖小炒',     '明湖餐厅',     '现炒小菜',         1),
(4, '明湖烧烤',     '明湖餐厅',     '炭火烧烤',         2),
(5, '嘉园夜宵',     '嘉园餐厅',     '深夜食堂',         1),
(5, '嘉园奶茶',     '嘉园餐厅',     '鲜制饮品',         2),
(6, '清真拉面',     '清真食堂',     '手工拉面',         1),
(6, '清真烤串',     '清真食堂',     '清真烤串',         2),
(7, '留园小炒',     '留园餐厅',     '精致小炒',         1),
(7, '留园包点',     '留园餐厅',     '广式包点',         2);

-- -------------------- 菜品（stall_id 对应上面档口；价格单位：分） --------------------
-- 注：dish.audit_status 列已退役（2026-09-15 阶段4，无独立菜品审核、公开可见性只看 status），不再写入（否则新库报 Unknown column）
-- 注：dish.category_id 列已随品类整链退役（2026-09-15 用户拍板），列清单与各行值已同步移除（否则新库报 Unknown column）
-- 注：dish.tags（标签）列与 promo_price（促销价）列已于 2026-09-20 拍板整链下线，列清单与各行值已同步移除
--     （否则新库报 Unknown column）；四维示例值随本 INSERT 一并写入。
-- 四维机器值（§7.28，**英文机器值**，中文仅由端上展示层映射；兼容值域见下方注释）：
--   diet_type：meat=荤 / half=半荤 / veg=素 / halal=清真
--   ingredients：pork/beef/lamb/chicken/duck/fish/egg/tofu/mushroom/veg/noodle/rice（逗号分隔）
--   flavor_tags：spicy/numbing/sour/sweet/salty/umami/light/heavy（逗号分隔；辣度语义并入口味）
--   serve_temp：hot=热食 / room=常温 / ice=冰
INSERT INTO dish (stall_id, name, price, description, images, status, view_count, avg_rating, rating_count,
                  diet_type, ingredients, flavor_tags, serve_temp) VALUES
(1,  '宫保鸡丁',   1600, '酸甜微辣，下饭神器',           NULL, 'on', 560, 4.7, 120, 'meat',  'chicken,veg',  'spicy,sour',    'hot'),
(1,  '水煮牛肉',   2800, '麻辣鲜香，分量十足',           NULL, 'on', 720, 4.8,  98, 'meat',  'beef,veg',     'spicy,numbing', 'hot'),
(1,  '回锅肉',     1800, '肥而不腻，川味经典',           NULL, 'on', 430, 4.6,  76, 'meat',  'pork,veg',     'spicy',         'hot'),
(1,  '番茄炒蛋',    900, '家常味道，酸甜可口',           NULL, 'on', 610, 4.5, 150, 'half',  'egg',          'sour,sweet',    'hot'),
(1,  '土豆烧牛肉', 2200, '软烂入味，暖心暖胃',           NULL, 'on', 380, 4.4,  64, 'meat',  'beef',         'salty',         'hot'),
(11, '牛肉拉面',   1500, '筋道爽滑，汤头浓郁',           NULL, 'on', 880, 4.7, 200, 'halal', 'beef,noodle',  'salty',         'hot'),
(2,  '鲜肉小笼',   1200, '皮薄汁多，一口爆汁',           NULL, 'on', 760, 4.8, 180, 'meat',  'pork,noodle',  'salty',         'hot'),
(4,  '黄焖鸡米饭', 1800, '酱香浓郁，鸡肉嫩滑',           NULL, 'on', 690, 4.6, 140, 'meat',  'chicken,rice', 'salty',         'hot'),
(4,  '香辣虾',     3200, '鲜香麻辣，弹牙爽口',           NULL, 'on', 320, 4.5,  55, 'meat',  'fish',         'spicy',         'hot'),
(4,  '招牌烤肉饭', 2000, '肉香四溢，粒粒分明',           NULL, 'on', 700, 4.7, 130, 'meat',  'pork,rice',    'salty',         'hot'),
(4,  '咖喱鸡排饭', 1900, '咖喱醇厚，外酥里嫩',           NULL, 'on', 410, 4.4,  88, 'meat',  'chicken,rice', 'salty',         'hot'),
(5,  '骨汤麻辣烫', 1700, '自选食材，麻辣鲜香',           NULL, 'on', 820, 4.6, 160, 'meat',  'noodle,veg',   'spicy,numbing', 'hot'),
(5,  '冒脑花',     1500, '嫩滑入味，辣得过瘾',           NULL, 'on', 260, 4.3,  42, 'meat',  'pork',         'spicy,numbing', 'hot'),
(6,  '皮蛋瘦肉粥',  800, '绵密温润，暖胃首选',           NULL, 'on', 520, 4.5, 110, 'half',  'egg,rice',     'light',         'hot'),
(6,  '广式肠粉',   1000, '晶莹剔透，酱香清爽',           NULL, 'on', 470, 4.6,  95, 'half',  'rice',         'light',         'hot'),
(7,  '干锅花菜',   1600, '爽脆下饭，锅气十足',           NULL, 'on', 390, 4.5,  70, 'veg',   'veg',          'spicy',         'hot'),
(7,  '糖醋里脊',   2100, '外酥里嫩，酸甜开胃',           NULL, 'on', 640, 4.7, 120, 'meat',  'pork',         'sour,sweet',    'hot'),
(8,  '烤五花肉',   2500, '滋滋冒油，焦香四溢',           NULL, 'on', 780, 4.8, 140, 'meat',  'pork',         'salty',         'hot'),
(8,  '烤茄子',     1200, '蒜香浓郁，软糯鲜甜',           NULL, 'on', 300, 4.4,  60, 'veg',   'veg',          'salty',         'hot'),
(9,  '炒粉',       1300, '镬气十足，宵夜之王',           NULL, 'on', 700, 4.6, 150, 'meat',  'noodle,veg',   'salty',         'hot'),
(9,  '烤冷面',     1100, '酸甜筋道，东北风味',           NULL, 'on', 560, 4.5, 130, 'half',  'noodle,egg',   'sour,sweet',    'hot'),
(10, '珍珠奶茶',   1000, 'Q弹珍珠，奶香醇厚',            NULL, 'on', 980, 4.7, 220, 'veg',   'rice',         'sweet',         'ice'),
(10, '杨枝甘露',   1400, '芒果西米，清甜解腻',           NULL, 'on', 840, 4.8, 190, 'veg',   'rice',         'sweet',         'ice'),
(11, '兰州牛肉面', 1500, '一清二白，汤鲜面劲',           NULL, 'on', 900, 4.8, 210, 'halal', 'beef,noodle',  'salty',         'hot'),
(11, '羊肉泡馍',   2000, '馍香肉烂，汤浓味厚',           NULL, 'on', 460, 4.6,  80, 'halal', 'lamb,noodle',  'salty',         'hot'),
(12, '羊肉串',     2000, '孜然飘香，外焦里嫩',           NULL, 'on', 720, 4.7, 160, 'halal', 'lamb',         'spicy',         'hot'),
(12, '烤馕',        900, '金黄酥脆，麦香十足',           NULL, 'on', 320, 4.5,  70, 'halal', 'noodle',       'salty',         'hot'),
(13, '鱼香茄子',   1400, '咸鲜微甜，超级下饭',           NULL, 'on', 500, 4.5,  90, 'veg',   'veg',          'spicy,sour',    'hot'),
(13, '宫保虾球',   3000, '荔枝口型，弹嫩鲜香',           NULL, 'on', 360, 4.6,  60, 'meat',  'fish',         'spicy,sour',    'hot'),
(14, '鲜虾烧卖',   1300, '皮薄馅大，鲜香多汁',           NULL, 'on', 580, 4.7, 110, 'meat',  'fish,noodle',  'salty',         'hot'),
(14, '叉烧包',     1000, '松软甜香，广式经典',           NULL, 'on', 520, 4.6, 100, 'meat',  'pork,noodle',  'sweet',         'hot');

-- -------------------- 评价（为部分菜品填充评价，丰富详情页；与 dish.avg_rating/rating_count 大致对应） --------------------
INSERT INTO review (user_id, dish_id, rating, content, is_hidden) VALUES
(1, 1,  5, '宫保鸡丁真的绝，下饭神器！',               0),
(1, 2,  5, '水煮牛肉麻辣鲜香，分量很足',               0),
(1, 6,  4, '牛肉拉面汤头浓郁，就是稍微有点咸',         0),
(1, 18, 5, '烤五花肉滋滋冒油，太香了',                 0),
(1, 22, 5, '珍珠奶茶奶香十足，珍珠很Q',                0),
(1, 24, 5, '兰州牛肉面一清二白，地道！',               0),
(2, 1,  4, '分量足，性价比高',                         0),
(2, 3,  5, '回锅肉肥而不腻，川味正',                   0),
(2, 12, 5, '骨汤麻辣烫自选很爽，汤底好喝',             0),
(2, 16, 5, '干锅花菜锅气十足，下饭',                   0),
(2, 26, 5, '羊肉串外焦里嫩，孜然味足',                 0),
(3, 4,  5, '番茄炒蛋家常味，酸甜可口',                 0),
(3, 7,  5, '皮蛋瘦肉粥绵密温润，暖胃',                 0),
(3, 8,  5, '干锅花菜朋友都夸',                         0),
(3, 14, 5, '皮蛋瘦肉粥配油条绝配',                     0),
(3, 29, 5, '鲜虾烧卖皮薄馅大，好吃',                   0),
(4, 9,  5, '香辣虾弹牙爽口，够味',                     0),
(4, 19, 4, '炒粉镬气足，宵夜首选',                     0),
(4, 23, 5, '杨枝甘露清甜解腻',                         0),
(4, 30, 5, '叉烧包松软甜香，广式经典',                 0);

-- 注（2026-09-20 拍板）：「评价有用」全链下线——review_useful 表与 review.useful_count 列已整链退役，
--     本脚本不再灌入该表种子数据、不再回填 useful_count（否则新库报 Unknown table / Unknown column）；
--     存量库由 schema.sql 末尾 drop_review_useful_chain 幂等段清理（表基线 10 → 9）。

-- -------------------- 用户反馈（含建议/纠错/举报，测试反馈处理流；无唯一键，先清后插保证可重复执行） --------------------
-- sub（二级分类，DEV-01）仅 type=suggestion 有效：示例数据给 suggestion 行补 'idea' 便于联调可见，
-- 其余类型（error/report/other/bug/add）该列按 NULL 写入（严格模式下其他类型传 sub 会被后端 400 拒绝）。
-- user_feedback.contact 已于 2026-09-16 用户拍板退役（产品定型「不收集联系方式」），列清单已移除。
DELETE FROM user_feedback;
INSERT INTO user_feedback (user_id, type, sub, content, status, related_type, related_id, created_at) VALUES
(1, 'suggestion', 'idea', '希望菜品详情页能标注过敏原信息，方便有忌口的同学选择', 'pending', NULL, NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(2, 'error',      NULL,   '明湖烧烤的营业时间写的是 10:00-22:00，实际下午才开门，麻烦修正一下', 'pending', NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3, 'report',     NULL,   '有评价内容疑似广告引流，建议管理员审核处理', 'pending', 'review', 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(4, 'other',      NULL,   '账号无法收到登录验证码，邮箱没有新邮件，求帮助', 'pending', NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 'bug',        NULL,   '首页瀑布流下拉刷新偶发卡死，需要杀掉小程序重进才恢复', 'pending', NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(1, 'add',        NULL,   '【新增菜品】香煎鸡排饭\n位置：二食堂二楼 3 号窗口\n特色：外酥里嫩，配时蔬', 'pending', 'dish', NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR));

-- =============================================================
-- 一期扩展字段补充（新增列后回填；基于默认值的幂等 UPDATE，可重复执行）
-- 来源：tasks/ARCH_DECISIONS_PHASE1.md §1.2
-- 仅用于演示/联调，给档口填位置链路、给菜品填属性标签。
-- =============================================================

-- 档口：楼层 / 窗口号（营业时间 business_hours 已于 2026-09-14 §7.14 D 随列下线一并删除）
UPDATE stall SET floor='1F',  window_no='1号窗口'  WHERE id=1;
UPDATE stall SET floor='1F',  window_no='2号窗口'  WHERE id=2;
UPDATE stall SET floor='1F',  window_no='3号窗口'  WHERE id=3;
UPDATE stall SET floor='1F',  window_no='4号窗口'  WHERE id=4;
UPDATE stall SET floor='2F',  window_no='5号窗口'  WHERE id=5;
UPDATE stall SET floor='2F',  window_no='6号窗口'  WHERE id=6;
UPDATE stall SET floor='1F',  window_no='7号窗口'  WHERE id=7;
UPDATE stall SET floor='1F',  window_no='8号窗口'  WHERE id=8;
UPDATE stall SET floor='B1',  window_no='9号窗口'  WHERE id=9;
UPDATE stall SET floor='B1',  window_no='10号窗口' WHERE id=10;
UPDATE stall SET floor='1F',  window_no='11号窗口' WHERE id=11;
UPDATE stall SET floor='1F',  window_no='12号窗口' WHERE id=12;
UPDATE stall SET floor='2F',  window_no='13号窗口' WHERE id=13;
UPDATE stall SET floor='2F',  window_no='14号窗口' WHERE id=14;

-- 菜品：描述四维（diet_type / ingredients / flavor_tags / serve_temp）示例值已在上方 `INSERT INTO dish` 内
-- 直接写入（英文机器值，§7.28），此处不再以 UPDATE 二次赋值，避免双处维护漂移。
-- 注（2026-09-20 拍板）：原 spice_level（辣度）与 region（风味/菜系）两列已整链下线，本脚本不再引用
--     （否则新库报 Unknown column）；存量库由 schema.sql 末尾 drop_dish_description_dimensions 幂等段清理。
-- 注：餐段 serve_period 与限量 limited 两列已于 2026-09-14 §7.9 整体下线（drop_dish_unused_fields）；
--     分量 portion 列已于 2026-09-14 §7.14（Q-114）整体下线（drop_dish_portion），本脚本自始未对其赋值。

-- -------------------- 菜品划线价（现价 price + 原价 original_price；判据 original_price > price；幂等 UPDATE 可重复执行） --------------------
-- 注（2026-09-20 拍板 §7.26）：promo_price（促销价）列已下线，其值按迁移口径并入 price（唯一数据源=现价）。
UPDATE dish SET price=1600, original_price=2000 WHERE id=1;   -- 宫保鸡丁 原价 20.00 / 现价 16.00
UPDATE dish SET price=2800, original_price=3200 WHERE id=2;   -- 水煮牛肉 原价 32.00 / 现价 28.00
UPDATE dish SET price=2000, original_price=2500 WHERE id=18;  -- 烤五花肉 原价 25.00 / 现价 20.00
UPDATE dish SET price=2000, original_price=2400 WHERE id=10;  -- 招牌烤肉饭 原价 24.00 / 现价 20.00
UPDATE dish SET price=1100, original_price=1300 WHERE id=21;  -- 烤冷面 原价 13.00 / 现价 11.00
UPDATE dish SET price=1000, original_price=1200 WHERE id=22;  -- 珍珠奶茶 原价 12.00 / 现价 10.00
UPDATE dish SET price=2000, original_price=2400 WHERE id=26;  -- 羊肉串 原价 24.00 / 现价 20.00

-- -------------------- 消息通知（演示个人中心红点与通知列表；无唯一键，先清后插保证可重复执行） --------------------
-- 类型 dish_audit / feedback_handle 与后端 NotificationConst 一致；related_id 指向真实菜品 / 反馈 ID。
DELETE FROM notification;
INSERT INTO notification (user_id, type, title, content, related_id, is_read, created_at) VALUES
(2, 'dish_audit', '菜品审核通过', '您提交的菜品「牛肉拉面」已通过审核，可以在对应档口查看。', 6, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(4, 'dish_audit', '菜品审核通过', '您提交的菜品「珍珠奶茶」已通过审核，可以在对应档口查看。', 22, 1, DATE_SUB(NOW(), INTERVAL 2 DAY));

SET FOREIGN_KEY_CHECKS = 1;
