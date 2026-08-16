-- =============================================================
-- 食在交大 种子数据脚本（重置服务器数据库用，一次性执行，非自动加载）
-- =============================================================
-- 用途：重置服务器数据库时灌入演示/基础数据（用户、分类、广播、食堂、档口、
--       菜品、轮播、动态、评论、评价、反馈、申请等），使三端有完整联调数据。
-- 执行前提：已按 schema.sql 建库（含全部表与最终字段）。
-- 执行：mysql -u <user> -p <pwd> -h localhost bjtu_food < seed_data.sql
-- 注意：本脚本部分段落（user_feedback / apply_action）采用先清后插，可重复执行；
--       其余段落（user / dish 等）重复执行会重复插入，重置时请先清库再运行。
-- 金额字段单位：分（如 1600 = 16.00 元）
-- images 置 NULL，由前端占位图（emoji）优雅降级，避免小程序外链域名限制。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 用户（动态/评价/通知等均依赖；密码统一为 123456，BCrypt 哈希） --------------------
-- BCrypt('123456') = $2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa
INSERT INTO `user` (username, email, password, nickname, avatar, role, status, last_login_at) VALUES
('2024001',  '2024001@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '交大干饭王',  NULL, 'student', 'active', NOW()),
('2024002',  '2024002@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '食堂常客',    NULL, 'student', 'active', NOW()),
('2024003',  '2024003@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '深夜放毒',    NULL, 'student', 'active', NOW()),
('2024004',  '2024004@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '奶茶三分甜',  NULL, 'student', 'active', NOW()),
('admin',    'admin@bjtu.edu.cn',   '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '管理员',      NULL, 'super_admin', 'active', NOW());

-- -------------------- 菜品分类（find 宫格展示） --------------------
INSERT INTO category (name, sort_order, status) VALUES
('早餐',    1, 'enabled'),
('午餐',    2, 'enabled'),
('晚餐',    3, 'enabled'),
('夜宵',    4, 'enabled'),
('面食',    5, 'enabled'),
('米饭',    6, 'enabled'),
('麻辣',    7, 'enabled'),
('清淡',    8, 'enabled');

-- -------------------- 首页广播条（status=enabled 才展示） --------------------
INSERT INTO broadcast (title, content, broadcast_type, target_id, target_url, sort_order, status) VALUES
('开餐提醒',   '学苑区各食堂午间 11:00-13:30 供餐，错峰就餐更舒适', 'NOTICE', NULL, NULL, 1, 'enabled'),
('今日特惠',   '明湖烧烤 烤五花肉 限时 8 折',                     'DISH',   18,  NULL, 2, 'enabled'),
('新生指引',   '扫码进入小程序，探索交大各食堂招牌菜',           'NONE',   NULL, NULL, 3, 'enabled');

-- -------------------- 食堂（共 7 个；id=1 为学一食堂，档口/菜品 canteen_id 引用以此对齐） --------------------
INSERT INTO canteen (name, location, description, status, sort_order, audit_status, latitude, longitude) VALUES
('学一食堂', '学苑区',     '综合食堂，家常风味', 'open', 1, 'approved', 39.953800, 116.335400),
('学二食堂', '学苑区一栋', '明亮整洁，家常味道', 'open', 2, 'approved', 39.954200, 116.335800),
('学三食堂', '学苑区二栋', '品类丰富，平价美味', 'open', 3, 'approved', 39.954600, 116.336200),
('明湖餐厅', '明湖旁',     '湖景餐厅，聚餐首选', 'open', 4, 'approved', 39.955800, 116.331500),
('嘉园餐厅', '嘉园公寓',   '夜宵与小吃天堂',     'open', 5, 'approved', 39.953000, 116.339000),
('清真食堂', '学苑区',     '清真风味，干净卫生', 'open', 6, 'approved', 39.954800, 116.335000),
('留园餐厅', '留园区',     '精致小炒与面点',     'open', 7, 'approved', 39.957000, 116.338000);

-- -------------------- 档口（canteen_id 对应上面的食堂） --------------------
INSERT INTO stall (canteen_id, name, location, description, status, sort_order, audit_status) VALUES
(1, '学一基本伙食', '学一食堂一层', '平价家常菜',       'open', 2, 'approved'),
(1, '学一面点坊',   '学一食堂一层', '现做面点与汤包',   'open', 3, 'approved'),
(2, '学二快餐档',   '学二食堂',     '快捷套餐',         'open', 1, 'approved'),
(2, '学二盖饭档',   '学二食堂',     '各式盖饭',         'open', 2, 'approved'),
(3, '学三麻辣烫',   '学三食堂',     '自选麻辣烫',       'open', 1, 'approved'),
(3, '学三粥铺',     '学三食堂',     '养生粥品',         'open', 2, 'approved'),
(4, '明湖小炒',     '明湖餐厅',     '现炒小菜',         'open', 1, 'approved'),
(4, '明湖烧烤',     '明湖餐厅',     '炭火烧烤',         'open', 2, 'approved'),
(5, '嘉园夜宵',     '嘉园餐厅',     '深夜食堂',         'open', 1, 'approved'),
(5, '嘉园奶茶',     '嘉园餐厅',     '鲜制饮品',         'open', 2, 'approved'),
(6, '清真拉面',     '清真食堂',     '手工拉面',         'open', 1, 'approved'),
(6, '清真烤串',     '清真食堂',     '清真烤串',         'open', 2, 'approved'),
(7, '留园小炒',     '留园餐厅',     '精致小炒',         'open', 1, 'approved'),
(7, '留园包点',     '留园餐厅',     '广式包点',         'open', 2, 'approved');

-- -------------------- 菜品（stall_id 对应上面档口；价格单位：分） --------------------
INSERT INTO dish (stall_id, name, price, description, images, tags, status, audit_status, view_count, avg_rating, rating_count) VALUES
(1,  '宫保鸡丁',   1600, '酸甜微辣，下饭神器',           NULL, 'recommended,signature', 'on', 'approved', 560, 4.7, 120),
(1,  '水煮牛肉',   2800, '麻辣鲜香，分量十足',           NULL, 'signature',            'on', 'approved', 720, 4.8,  98),
(1,  '回锅肉',     1800, '肥而不腻，川味经典',           NULL, 'recommended',          'on', 'approved', 430, 4.6,  76),
(2,  '番茄炒蛋',    900, '家常味道，酸甜可口',           NULL, 'recommended',          'on', 'approved', 610, 4.5, 150),
(2,  '土豆烧牛肉', 2200, '软烂入味，暖心暖胃',           NULL, '',                    'on', 'approved', 380, 4.4,  64),
(3,  '牛肉拉面',   1500, '筋道爽滑，汤头浓郁',           NULL, 'signature',            'on', 'approved', 880, 4.7, 200),
(3,  '鲜肉小笼',   1200, '皮薄汁多，一口爆汁',           NULL, 'recommended',          'on', 'approved', 760, 4.8, 180),
(4,  '黄焖鸡米饭', 1800, '酱香浓郁，鸡肉嫩滑',           NULL, 'recommended',          'on', 'approved', 690, 4.6, 140),
(4,  '香辣虾',     3200, '鲜香麻辣，弹牙爽口',           NULL, 'signature',            'on', 'approved', 320, 4.5,  55),
(5,  '招牌烤肉饭', 2000, '肉香四溢，粒粒分明',           NULL, 'recommended',          'on', 'approved', 700, 4.7, 130),
(5,  '咖喱鸡排饭', 1900, '咖喱醇厚，外酥里嫩',           NULL, '',                    'on', 'approved', 410, 4.4,  88),
(6,  '骨汤麻辣烫', 1700, '自选食材，麻辣鲜香',           NULL, 'recommended',          'on', 'approved', 820, 4.6, 160),
(6,  '冒脑花',     1500, '嫩滑入味，辣得过瘾',           NULL, 'signature',            'on', 'approved', 260, 4.3,  42),
(7,  '皮蛋瘦肉粥',  800, '绵密温润，暖胃首选',           NULL, 'recommended',          'on', 'approved', 520, 4.5, 110),
(7,  '广式肠粉',   1000, '晶莹剔透，酱香清爽',           NULL, '',                    'on', 'approved', 470, 4.6,  95),
(8,  '干锅花菜',   1600, '爽脆下饭，锅气十足',           NULL, 'recommended',          'on', 'approved', 390, 4.5,  70),
(8,  '糖醋里脊',   2100, '外酥里嫩，酸甜开胃',           NULL, 'signature',            'on', 'approved', 640, 4.7, 120),
(9,  '烤五花肉',   2500, '滋滋冒油，焦香四溢',           NULL, 'recommended',          'on', 'approved', 780, 4.8, 140),
(9,  '烤茄子',     1200, '蒜香浓郁，软糯鲜甜',           NULL, '',                    'on', 'approved', 300, 4.4,  60),
(10, '炒粉',       1300, '镬气十足，宵夜之王',           NULL, 'recommended',          'on', 'approved', 700, 4.6, 150),
(10, '烤冷面',     1100, '酸甜筋道，东北风味',           NULL, 'signature',            'on', 'approved', 560, 4.5, 130),
(11, '珍珠奶茶',   1000, 'Q弹珍珠，奶香醇厚',            NULL, 'recommended',          'on', 'approved', 980, 4.7, 220),
(11, '杨枝甘露',   1400, '芒果西米，清甜解腻',           NULL, 'signature',            'on', 'approved', 840, 4.8, 190),
(12, '兰州牛肉面', 1500, '一清二白，汤鲜面劲',           NULL, 'signature',            'on', 'approved', 900, 4.8, 210),
(12, '羊肉泡馍',   2000, '馍香肉烂，汤浓味厚',           NULL, 'recommended',          'on', 'approved', 460, 4.6,  80),
(13, '羊肉串',     2000, '孜然飘香，外焦里嫩',           NULL, 'recommended',          'on', 'approved', 720, 4.7, 160),
(13, '烤馕',        900, '金黄酥脆，麦香十足',           NULL, '',                    'on', 'approved', 320, 4.5,  70),
(14, '鱼香茄子',   1400, '咸鲜微甜，超级下饭',           NULL, 'recommended',          'on', 'approved', 500, 4.5,  90),
(14, '宫保虾球',   3000, '荔枝口型，弹嫩鲜香',           NULL, 'signature',            'on', 'approved', 360, 4.6,  60),
(14, '鲜虾烧卖',   1300, '皮薄馅大，鲜香多汁',           NULL, 'recommended',          'on', 'approved', 580, 4.7, 110),
(14, '叉烧包',     1000, '松软甜香，广式经典',           NULL, '',                    'on', 'approved', 520, 4.6, 100);

-- -------------------- 轮播图（status=enabled 才会被首页展示） --------------------
INSERT INTO banner (title, subtitle, images, target_type, target_id, status, sort_order) VALUES
('食在交大',   '校园美食一站式发现', NULL, 'NONE',     NULL, 'enabled', 1),
('今日上新',   '新鲜菜品抢先看',     NULL, 'DISH',     4,    'enabled', 2),
('限时特惠',   '超值美味看得见',      NULL, 'NONE',     NULL, 'enabled', 3),
('食堂探店',   '发现身边的好味道',   NULL, 'NONE',     NULL, 'enabled', 4);

-- -------------------- 社区动态 moment（user_id 1~4 为注册学生；related_type: dish/stall/none） --------------------
INSERT INTO moment (user_id, content, images, related_type, related_id, audit_status, useful_count, comment_count, status, created_at) VALUES
(1, '今天在学一面点坊吃到了现做的鲜肉小笼，皮薄汁多，一口下去太满足了！推荐大家来试试～', NULL, 'dish',  4, 'approved', 12, 3, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(2, '学三麻辣烫自选食材真的yyds，骨汤底绝了，人均 20 吃到撑，晚自习前干饭首选！',        NULL, 'dish',  6, 'approved', 8,  2, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(3, '明湖餐厅的烤五花肉滋滋冒油，配上一瓶冰可乐，考试周解压神器。',                     NULL, 'dish',  9, 'approved', 15, 4, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(4, '嘉园奶茶的珍珠奶茶 Q 弹顺滑，下午茶标配，甜度刚刚好。',                             NULL, 'dish', 11, 'approved', 6,  1, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, '早八人福音！学三粥铺的皮蛋瘦肉粥绵密温润，配根油条开启元气满满的一天。',           NULL, 'dish',  7, 'approved', 9,  2, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, '这家档口师傅手艺是真的好，兰州牛肉面一清二白汤鲜面劲，每次来都排长队，值得等待！', NULL, 'stall', 12, 'approved', 11, 3, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(3, '新发现的宝藏档口，清真烤串孜然飘香，晚上下课来两串太治愈了。',                     NULL, 'stall', 13, 'approved', 7,  1, 0, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(4, '食堂新装修后环境好了很多，吃饭心情都变好了，随手记录一下～',                       NULL, 'none',  NULL, 'approved', 3,  0, 0, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- -------------------- 动态评论 moment_comment（parent_id NULL=顶级，非 NULL=楼中楼回复） --------------------
INSERT INTO moment_comment (moment_id, user_id, parent_id, content, created_at) VALUES
(1, 2, NULL, '小笼包确实好吃！我也常去', DATE_SUB(NOW(), INTERVAL 100 MINUTE)),
(1, 3, NULL, '求问是学一食堂哪一层呀',      DATE_SUB(NOW(), INTERVAL 80 MINUTE)),
(1, 1, 2,    '一楼最里面那家，去晚就卖完了', DATE_SUB(NOW(), INTERVAL 70 MINUTE)),
(2, 4, NULL, '骨汤底yyds！已安利室友',      DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(2, 1, NULL, '人均20？这么实惠的吗',        DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(3, 1, NULL, '考试周就该吃点好的！',        DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(3, 4, NULL, '下次一起冲',                  DATE_SUB(NOW(), INTERVAL 18 HOUR)),
(4, 2, NULL, '三分糖党报到',               DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(5, 3, NULL, '早八人+1，粥铺yyds',         DATE_SUB(NOW(), INTERVAL 1 DAY)),
(6, 4, NULL, '这家拉面真的每次都要排队',   DATE_SUB(NOW(), INTERVAL 1 DAY)),
(6, 1, 4,    '避开饭点去会好很多',         DATE_SUB(NOW(), INTERVAL 23 HOUR)),
(7, 2, NULL, '烤串一绝，推荐羊肉串',       DATE_SUB(NOW(), INTERVAL 2 DAY));

-- -------------------- 动态「有用 👍」标记（moment_useful，一人一票） --------------------
INSERT INTO moment_useful (user_id, moment_id, created_at) VALUES
(2, 1, DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
(3, 1, DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
(4, 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(1, 2, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(3, 2, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(1, 3, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(2, 3, DATE_SUB(NOW(), INTERVAL 19 HOUR)),
(4, 3, DATE_SUB(NOW(), INTERVAL 16 HOUR)),
(1, 4, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(1, 5, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 5, DATE_SUB(NOW(), INTERVAL 23 HOUR)),
(2, 6, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(3, 6, DATE_SUB(NOW(), INTERVAL 23 HOUR)),
(4, 6, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(1, 7, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- -------------------- 评价（为部分菜品填充评价，丰富详情页；与 dish.avg_rating/rating_count 大致对应） --------------------
INSERT INTO review (user_id, dish_id, rating, content, images, is_hidden) VALUES
(1, 1,  5, '宫保鸡丁真的绝，下饭神器！',               NULL, 0),
(1, 2,  5, '水煮牛肉麻辣鲜香，分量很足',               NULL, 0),
(1, 6,  4, '牛肉拉面汤头浓郁，就是稍微有点咸',         NULL, 0),
(1, 18, 5, '烤五花肉滋滋冒油，太香了',                 NULL, 0),
(1, 22, 5, '珍珠奶茶奶香十足，珍珠很Q',                NULL, 0),
(1, 24, 5, '兰州牛肉面一清二白，地道！',               NULL, 0),
(2, 1,  4, '分量足，性价比高',                         NULL, 0),
(2, 3,  5, '回锅肉肥而不腻，川味正',                   NULL, 0),
(2, 12, 5, '骨汤麻辣烫自选很爽，汤底好喝',             NULL, 0),
(2, 16, 5, '干锅花菜锅气十足，下饭',                   NULL, 0),
(2, 26, 5, '羊肉串外焦里嫩，孜然味足',                 NULL, 0),
(3, 4,  5, '番茄炒蛋家常味，酸甜可口',                 NULL, 0),
(3, 7,  5, '皮蛋瘦肉粥绵密温润，暖胃',                 NULL, 0),
(3, 8,  5, '干锅花菜朋友都夸',                         NULL, 0),
(3, 14, 5, '皮蛋瘦肉粥配油条绝配',                     NULL, 0),
(3, 29, 5, '鲜虾烧卖皮薄馅大，好吃',                   NULL, 0),
(4, 9,  5, '香辣虾弹牙爽口，够味',                     NULL, 0),
(4, 19, 4, '炒粉镬气足，宵夜首选',                     NULL, 0),
(4, 23, 5, '杨枝甘露清甜解腻',                         NULL, 0),
(4, 30, 5, '叉烧包松软甜香，广式经典',                 NULL, 0);

-- -------------------- 评价「有用」标记（review_useful，一人一票） --------------------
INSERT INTO review_useful (user_id, review_id, created_at) VALUES
(2, 1, NOW()), (3, 1, NOW()), (4, 1, NOW()),
(1, 2, NOW()), (3, 2, NOW()),
(2, 3, NOW()), (4, 3, NOW()),
(1, 4, NOW()), (3, 4, NOW()),
(2, 5, NOW()), (4, 5, NOW()),
(1, 6, NOW()), (3, 6, NOW());

-- -------------------- 用户反馈（含建议/纠错/举报，测试反馈处理流；无唯一键，先清后插保证可重复执行） --------------------
DELETE FROM user_feedback;
INSERT INTO user_feedback (user_id, type, content, contact, status, related_type, related_id, created_at) VALUES
(1, 'suggestion', '希望菜品详情页能标注过敏原信息，方便有忌口的同学选择', '2024001@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(2, 'error',     '明湖烧烤的营业时间写的是 10:00-22:00，实际下午才开门，麻烦修正一下', '2024002@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3, 'report',    '有动态内容疑似广告引流，建议管理员审核处理', NULL, 'pending', 'moment', 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(4, 'other',     '账号无法收到登录验证码，邮箱没有新邮件，求帮助', '2024004@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- -------------------- 实体贡献申请（UGC，测试申请审核流；无唯一键，先清后插保证可重复执行） --------------------
DELETE FROM apply_action;
INSERT INTO apply_action (applicant_id, entity_type, entity_id, apply_type, status, payload, created_at) VALUES
(1, 'DISH',   NULL, 'NEW',    'pending', '{"name":"香煎鸡排饭","price":1800,"description":"外酥里嫩，配时蔬","stall_id":1,"tags":"recommended"}',     DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
(2, 'STALL',  NULL, 'NEW',    'pending', '{"name":"学二奶茶铺","location":"学二食堂一层","description":"鲜制果茶与奶茶","canteen_id":2}',            DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(3, 'CANTEEN', 2,  'CHANGE', 'pending', '{"name":"学二食堂","location":"学苑区一栋东侧","description":"新增夜宵窗口"}',                          DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(4, 'STALL',  14, 'CLOSE',   'pending', '{"name":"留园包点","reason":"档口歇业，申请关闭"}',                                                   DATE_SUB(NOW(), INTERVAL 1 DAY));

-- =============================================================
-- 一期扩展字段补充（新增列后回填；基于默认值的幂等 UPDATE，可重复执行）
-- 来源：tasks/ARCH_DECISIONS_PHASE1.md §1.2
-- 仅用于演示/联调，给档口填位置链路、给菜品填属性标签。
-- =============================================================

-- 档口：楼层 / 窗口号 / 营业时间
UPDATE stall SET floor='1F',  window_no='1号窗口',  business_hours='10:00-20:00' WHERE id=1;
UPDATE stall SET floor='1F',  window_no='2号窗口',  business_hours='07:00-14:00' WHERE id=2;
UPDATE stall SET floor='1F',  window_no='3号窗口',  business_hours='10:00-20:00' WHERE id=3;
UPDATE stall SET floor='1F',  window_no='4号窗口',  business_hours='10:00-20:00' WHERE id=4;
UPDATE stall SET floor='2F',  window_no='5号窗口',  business_hours='10:00-21:00' WHERE id=5;
UPDATE stall SET floor='2F',  window_no='6号窗口',  business_hours='06:30-14:00' WHERE id=6;
UPDATE stall SET floor='1F',  window_no='7号窗口',  business_hours='10:00-22:00' WHERE id=7;
UPDATE stall SET floor='1F',  window_no='8号窗口',  business_hours='16:00-23:00' WHERE id=8;
UPDATE stall SET floor='B1',  window_no='9号窗口',  business_hours='17:00-02:00' WHERE id=9;
UPDATE stall SET floor='B1',  window_no='10号窗口', business_hours='10:00-23:00' WHERE id=10;
UPDATE stall SET floor='1F',  window_no='11号窗口', business_hours='10:00-21:00' WHERE id=11;
UPDATE stall SET floor='1F',  window_no='12号窗口', business_hours='17:00-23:00' WHERE id=12;
UPDATE stall SET floor='2F',  window_no='13号窗口', business_hours='10:00-21:00' WHERE id=13;
UPDATE stall SET floor='2F',  window_no='14号窗口', business_hours='07:00-14:00' WHERE id=14;

-- 菜品：先给全部菜品一个基础属性，再对部分招牌/特征菜做差异化
UPDATE dish SET spice_level=1, portion=1, serve_period='lunch,dinner', limited=0
    WHERE spice_level=0 AND serve_period='';

UPDATE dish SET spice_level=2, portion=1, serve_period='lunch,dinner' WHERE id=1;   -- 宫保鸡丁
UPDATE dish SET spice_level=3, portion=2, serve_period='lunch,dinner' WHERE id=2;   -- 水煮牛肉
UPDATE dish SET spice_level=2, portion=1, serve_period='lunch,dinner' WHERE id=3;   -- 回锅肉
UPDATE dish SET spice_level=0, portion=1, serve_period='lunch,dinner' WHERE id=4;   -- 番茄炒蛋
UPDATE dish SET spice_level=1, portion=1, serve_period='lunch,dinner' WHERE id=6;   -- 牛肉拉面
UPDATE dish SET spice_level=3, portion=2, serve_period='dinner'       WHERE id=9;   -- 香辣虾
UPDATE dish SET spice_level=2, portion=2, serve_period='lunch,dinner' WHERE id=12;  -- 骨汤麻辣烫
UPDATE dish SET spice_level=3, portion=1, serve_period='dinner'       WHERE id=13;  -- 冒脑花
UPDATE dish SET spice_level=0, portion=1, serve_period='breakfast,lunch' WHERE id=14; -- 皮蛋瘦肉粥
UPDATE dish SET spice_level=0, portion=1, serve_period='breakfast,lunch' WHERE id=15; -- 广式肠粉
UPDATE dish SET spice_level=1, portion=1, serve_period='midnight'     WHERE id=19;  -- 炒粉
UPDATE dish SET spice_level=1, portion=1, serve_period='midnight'     WHERE id=20;  -- 烤冷面
UPDATE dish SET spice_level=0, portion=1, serve_period='lunch,dinner' WHERE id=22;  -- 珍珠奶茶
UPDATE dish SET spice_level=0, portion=1, serve_period='lunch,dinner' WHERE id=23;  -- 杨枝甘露
UPDATE dish SET spice_level=1, portion=1, serve_period='lunch,dinner' WHERE id=24;  -- 兰州牛肉面
UPDATE dish SET spice_level=2, portion=1, serve_period='dinner,midnight' WHERE id=26; -- 羊肉串
UPDATE dish SET spice_level=0, portion=1, serve_period='breakfast,lunch' WHERE id=29; -- 鲜虾烧卖
UPDATE dish SET spice_level=0, portion=1, serve_period='breakfast,lunch' WHERE id=30; -- 叉烧包

-- 地域（美食来源地，与食堂位置无关）：按菜品特征推断
UPDATE dish SET region='川湘'   WHERE id IN (1,2,3);      -- 宫保鸡丁/水煮牛肉/回锅肉
UPDATE dish SET region='清真'   WHERE id IN (6,24,26);    -- 牛肉拉面/兰州牛肉面/羊肉串
UPDATE dish SET region='粤式'   WHERE id IN (15,22,29,30);-- 广式肠粉/珍珠奶茶/鲜虾烧卖/叉烧包
UPDATE dish SET region='东北'   WHERE id IN (19,20);      -- 炒粉/烤冷面
UPDATE dish SET region='西北'   WHERE id=12;              -- 骨汤麻辣烫
UPDATE dish SET region='川湘'   WHERE id IN (9,13);       -- 香辣虾/冒脑花

SET FOREIGN_KEY_CHECKS = 1;
