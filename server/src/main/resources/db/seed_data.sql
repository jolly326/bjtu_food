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

-- ============================================================
-- 幂等前置清理（2026-09-23 补，change `dish-detail-contract-hardening` §4.3）
-- 本脚本此前只支持「一次性执行」：第 2 次运行会在 `user.uk_user_username` 等唯一键上
-- 报 `Duplicate entry` 中断（既有缺陷，与 tasks 4.3「可重复执行、零报错」不符）。
-- 现于各「无前置清理」的插入段之前统一清空并**重置自增** —— 下方各行以固定 id 互相引用
-- （dish.stall_id 1..14 / review.dish_id 1..29 / user_id 1..4），TRUNCATE 会一并重置自增，
-- 使重跑后的引用关系与首次执行完全一致（若改用 DELETE，自增继续增长会导致引用错位）。
-- 顺序按引用依赖倒序：review → dish → stall → canteen → user
-- （其残留会让「同一用户对同一菜品当天只计一次」的判定误判为已浏览）。
-- 尾部 `user_feedback` / `notification` / `banner` 三段本就有 DELETE，无需重复处理。
-- ============================================================
TRUNCATE TABLE `review`;
TRUNCATE TABLE `dish`;
TRUNCATE TABLE `stall`;
TRUNCATE TABLE `canteen`;
TRUNCATE TABLE `user`;
-- 描述属性维度字典：先清后插（与 review/banner 同口径，保证可重复执行）。
-- 方案 A：无取值字典表——取值就是中文文本本身（见 dish.attributes）。
TRUNCATE TABLE `dish_attribute_dimension`;

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
-- 注：dish.tags（标签）列与 promo_price（促销价）列已于 2026-09-20 拍板整链下线，列清单与各行值已同步移除。
-- 描述属性经 `attributes` JSON 一次性写入（键 = 维度 field_key，值 = **中文文本** / 数组；
-- 仅含该菜实际拥有的维度；方案 A：值即中文，端上直渲，无取值字典表）。
INSERT INTO dish (stall_id, name, price, description, images, status, view_count, avg_rating, rating_count,
                  attributes) VALUES
(1,  '宫保鸡丁',   1600, '酸甜微辣，下饭神器',           NULL, 'on', 560, 4.7, 120, '{"dietType":"荤","ingredients":["鸡","青菜"],"flavorTags":["辣","酸"],"serveTemp":"热食"}'),
(1,  '水煮牛肉',   2800, '麻辣鲜香，分量十足',           NULL, 'on', 720, 4.8,  98, '{"dietType":"荤","ingredients":["牛","青菜"],"flavorTags":["辣","麻"],"serveTemp":"热食"}'),
(1,  '回锅肉',     1800, '肥而不腻，川味经典',           NULL, 'on', 430, 4.6,  76, '{"dietType":"荤","ingredients":["猪","青菜"],"flavorTags":["辣"],"serveTemp":"热食"}'),
(1,  '番茄炒蛋',    900, '家常味道，酸甜可口',           NULL, 'on', 610, 4.5, 150, '{"dietType":"半荤","ingredients":["蛋"],"flavorTags":["酸","甜"],"serveTemp":"热食"}'),
(1,  '土豆烧牛肉', 2200, '软烂入味，暖心暖胃',           NULL, 'on', 380, 4.4,  64, '{"dietType":"荤","ingredients":["牛"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(11, '牛肉拉面',   1500, '筋道爽滑，汤头浓郁',           NULL, 'on', 880, 4.7, 200, '{"dietType":"清真","ingredients":["牛","面"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(2,  '鲜肉小笼',   1200, '皮薄汁多，一口爆汁',           NULL, 'on', 760, 4.8, 180, '{"dietType":"荤","ingredients":["猪","面"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(4,  '黄焖鸡米饭', 1800, '酱香浓郁，鸡肉嫩滑',           NULL, 'on', 690, 4.6, 140, '{"dietType":"荤","ingredients":["鸡","米"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(4,  '香辣虾',     3200, '鲜香麻辣，弹牙爽口',           NULL, 'on', 320, 4.5,  55, '{"dietType":"荤","ingredients":["鱼虾"],"flavorTags":["辣"],"serveTemp":"热食"}'),
(4,  '招牌烤肉饭', 2000, '肉香四溢，粒粒分明',           NULL, 'on', 700, 4.7, 130, '{"dietType":"荤","ingredients":["猪","米"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(4,  '咖喱鸡排饭', 1900, '咖喱醇厚，外酥里嫩',           NULL, 'on', 410, 4.4,  88, '{"dietType":"荤","ingredients":["鸡","米"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(5,  '骨汤麻辣烫', 1700, '自选食材，麻辣鲜香',           NULL, 'on', 820, 4.6, 160, '{"dietType":"荤","ingredients":["面","青菜"],"flavorTags":["辣","麻"],"serveTemp":"热食"}'),
(5,  '冒脑花',     1500, '嫩滑入味，辣得过瘾',           NULL, 'on', 260, 4.3,  42, '{"dietType":"荤","ingredients":["猪"],"flavorTags":["辣","麻"],"serveTemp":"热食"}'),
(6,  '皮蛋瘦肉粥',  800, '绵密温润，暖胃首选',           NULL, 'on', 520, 4.5, 110, '{"dietType":"半荤","ingredients":["蛋","米"],"flavorTags":["清淡"],"serveTemp":"热食"}'),
(6,  '广式肠粉',   1000, '晶莹剔透，酱香清爽',           NULL, 'on', 470, 4.6,  95, '{"dietType":"半荤","ingredients":["米"],"flavorTags":["清淡"],"serveTemp":"热食"}'),
(7,  '干锅花菜',   1600, '爽脆下饭，锅气十足',           NULL, 'on', 390, 4.5,  70, '{"dietType":"素","ingredients":["青菜"],"flavorTags":["辣"],"serveTemp":"热食"}'),
(7,  '糖醋里脊',   2100, '外酥里嫩，酸甜开胃',           NULL, 'on', 640, 4.7, 120, '{"dietType":"荤","ingredients":["猪"],"flavorTags":["酸","甜"],"serveTemp":"热食"}'),
(8,  '烤五花肉',   2500, '滋滋冒油，焦香四溢',           NULL, 'on', 780, 4.8, 140, '{"dietType":"荤","ingredients":["猪"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(8,  '烤茄子',     1200, '蒜香浓郁，软糯鲜甜',           NULL, 'on', 300, 4.4,  60, '{"dietType":"素","ingredients":["青菜"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(9,  '炒粉',       1300, '镬气十足，宵夜之王',           NULL, 'on', 700, 4.6, 150, '{"dietType":"荤","ingredients":["面","青菜"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(9,  '烤冷面',     1100, '酸甜筋道，东北风味',           NULL, 'on', 560, 4.5, 130, '{"dietType":"半荤","ingredients":["面","蛋"],"flavorTags":["酸","甜"],"serveTemp":"热食"}'),
(10, '珍珠奶茶',   1000, 'Q弹珍珠，奶香醇厚',            NULL, 'on', 980, 4.7, 220, '{"dietType":"素","flavorTags":["甜"],"serveTemp":"冰"}'),
(10, '杨枝甘露',   1400, '芒果西米，清甜解腻',           NULL, 'on', 840, 4.8, 190, '{"dietType":"素","flavorTags":["甜"],"serveTemp":"冰"}'),
(11, '兰州牛肉面', 1500, '一清二白，汤鲜面劲',           NULL, 'on', 900, 4.8, 210, '{"dietType":"清真","ingredients":["牛","面"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(11, '羊肉泡馍',   2000, '馍香肉烂，汤浓味厚',           NULL, 'on', 460, 4.6,  80, '{"dietType":"清真","ingredients":["羊","面"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(12, '羊肉串',     2000, '孜然飘香，外焦里嫩',           NULL, 'on', 720, 4.7, 160, '{"dietType":"清真","ingredients":["羊"],"flavorTags":["辣"],"serveTemp":"热食"}'),
(12, '烤馕',        900, '金黄酥脆，麦香十足',           NULL, 'on', 320, 4.5,  70, '{"dietType":"清真","ingredients":["面"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(13, '鱼香茄子',   1400, '咸鲜微甜，超级下饭',           NULL, 'on', 500, 4.5,  90, '{"dietType":"素","ingredients":["青菜"],"flavorTags":["辣","酸"],"serveTemp":"热食"}'),
(13, '宫保虾球',   3000, '荔枝口型，弹嫩鲜香',           NULL, 'on', 360, 4.6,  60, '{"dietType":"荤","ingredients":["鱼虾"],"flavorTags":["辣","酸"],"serveTemp":"热食"}'),
(14, '鲜虾烧卖',   1300, '皮薄馅大，鲜香多汁',           NULL, 'on', 580, 4.7, 110, '{"dietType":"荤","ingredients":["鱼虾","面"],"flavorTags":["咸"],"serveTemp":"热食"}'),
(14, '叉烧包',     1000, '松软甜香，广式经典',           NULL, 'on', 520, 4.6, 100, '{"dietType":"荤","ingredients":["猪","面"],"flavorTags":["甜"],"serveTemp":"热食"}');

-- -------------------- 菜品描述属性维度（单表驱动，值即中文） --------------------
-- 维度键 field_key 恒等于 dish.attributes JSON 的键（camelCase）；取值即中文文本，无独立取值字典表。
-- 新增取值 = 直接在菜品 attributes 里填中文（零登记）；编辑候选由「全库已用值」去重得出。
INSERT INTO dish_attribute_dimension (id, field_key, name, value_type, `order`) VALUES
(1, 'dietType',    '饮食属性', 'single', 1),
(2, 'ingredients', '食材',     'multi',  2),
(3, 'flavorTags',  '口味',     'multi',  3),
(4, 'serveTemp',   '冷热',     'single', 4);

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

-- -------------------- 用户反馈（意见反馈/举报，测试反馈处理流；无唯一键，先清后插保证可重复执行） --------------------
-- sub（二级分类）：report 行传举报原因机器值（GET /feedback/report-reasons 字典下发项）；
-- 其他类型该列按 NULL 写入。user_feedback.contact 已于 2026-09-16 用户拍板退役（产品定型「不收集联系方式」）。
DELETE FROM user_feedback;
INSERT INTO user_feedback (user_id, type, sub, content, status, related_type, related_id, created_at) VALUES
(1, 'issue',  NULL,   '希望菜品详情页能标注过敏原信息，方便有忌口的同学选择', 'pending', NULL, NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(2, 'issue',  NULL,   '明湖烧烤的营业时间写的是 10:00-22:00，实际下午才开门，麻烦修正一下', 'pending', NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3, 'report', 'spam', '有评价内容疑似广告引流，建议管理员审核处理', 'pending', 'review', 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(4, 'issue',  NULL,   '账号无法收到登录验证码，邮箱没有新邮件，求帮助', 'pending', NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 'issue',  NULL,   '首页瀑布流下拉刷新偶发卡死，需要杀掉小程序重进才恢复', 'pending', NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR));

-- -------------------- 菜品信息纠错（测试纠错采纳/拒绝流；先清后插保证可重复执行） --------------------
-- 覆盖三种处理路径：#1 档口名精确命中既有档口（可直接采纳）；#2 档口名未命中（触发两段式档口确认，
-- 候选 = 提交食堂名匹配「清真食堂」下的档口）；#3 已拒绝（留痕不采纳原因）。
DELETE FROM dish_correction;
INSERT INTO dish_correction (dish_id, user_id, name, price, canteen_name, stall_name, attributes, images, status, reply, reject_reason, created_at) VALUES
(4,  1,    '番茄炒蛋盖饭', 800,  '学一食堂', '学一基本伙食', '{"dietType":"半荤","ingredients":["蛋","米"],"flavorTags":["酸","甜"],"serveTemp":"热食"}', NULL, 'pending',  NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(11, 2,    '牛肉拉面',     1200, '清真食堂', '清真面档',     '{"dietType":"清真","ingredients":["牛","面"],"flavorTags":["咸"],"serveTemp":"热食"}',   NULL, 'pending',  NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(1,  NULL, '宫保鸡丁',     1600, '学一食堂', '学一基本伙食', '{"dietType":"荤","ingredients":["鸡","青菜"],"flavorTags":["辣","酸"],"serveTemp":"热食"}', NULL, 'rejected', NULL, '经核实价格与档口今日公示一致', DATE_SUB(NOW(), INTERVAL 1 DAY));

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

-- 菜品：描述属性（dish.attributes JSON）示例值已在上方 `INSERT INTO dish` 内直接写入，
-- 此处不再以 UPDATE 二次赋值，避免双处维护漂移。
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

-- -------------------- 菜品大类赋值（2026-09-21 §7.34 / H4 归属清单，幂等 UPDATE 按菜名） --------------------
-- 判定口径：按「菜名与做法形态」判（H3），不看主料、不看口味；31 道菜全量覆盖、无空类。
-- 幂等：按菜名 UPDATE，可重复执行；新增大类须在 DishViewConst 加一条 MEAL_TYPE 视图并发版（H1：枚举列，无字典表）。
UPDATE dish SET meal_type = 'set_meal'   WHERE name IN ('黄焖鸡米饭', '招牌烤肉饭', '咖喱鸡排饭');
UPDATE dish SET meal_type = 'stir_fry'   WHERE name IN ('宫保鸡丁', '水煮牛肉', '回锅肉', '番茄炒蛋', '土豆烧牛肉', '香辣虾', '糖醋里脊', '鱼香茄子', '宫保虾球');
UPDATE dish SET meal_type = 'noodle'     WHERE name IN ('牛肉拉面', '兰州牛肉面', '羊肉泡馍', '炒粉');
UPDATE dish SET meal_type = 'dry_pot'    WHERE name IN ('干锅花菜', '骨汤麻辣烫', '冒脑花');
UPDATE dish SET meal_type = 'snack'      WHERE name IN ('鲜肉小笼', '广式肠粉', '烤五花肉', '烤茄子', '烤冷面', '羊肉串', '烤馕', '鲜虾烧卖', '叉烧包');
UPDATE dish SET meal_type = 'soup_drink' WHERE name IN ('皮蛋瘦肉粥', '珍珠奶茶', '杨枝甘露');

-- -------------------- 首页顶部轮播图（2026-09-22 新增；公开 GET /banners） --------------------
-- 注：本段采用先清后插（可重复执行）；素材统一 **16:10**（宽高比锁定，见 docs/client/ui/client-首页菜品浏览.md §1.1）。
--     image_url 暂为空串 = 「待补正式素材」——端上会退化为「灰底 + 菜品 icon」空态（同菜品卡图片占位），
--     不会裂图；正式素材到位后替换 image_url，或增行以启用多图轮播（>1 条时端上自动轮播并显示指示点）。
DELETE FROM banner;
INSERT INTO banner (image_url, sort_order, status) VALUES
('', 1, 'on');

SET FOREIGN_KEY_CHECKS = 1;
