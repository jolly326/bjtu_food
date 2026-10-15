-- ============================================================================
-- dish_attribute_value 唯一约束：一个维度一个名称不能重复
-- ============================================================================
-- 业界/本项目标准：维度（维度项）下可选名称唯一，即
-- `UNIQUE (dimension_id, name)`。应用场景：菜品属性维度（口味/份量等）下
-- 对应该维度的可选项名称唯一，避免同一维度重复注册同名选项。
--
-- 数据风险评估：生产库 `dish_attribute_value` 当前 21 行，按
-- `GROUP BY dimension_id, name HAVING COUNT(*)>1` 核验无重复，导入约束安全。
--
-- 执行方式：
--   mysql -u root -p... bjtu_food < db/dish_attribute_value_unique.sql
-- ============================================================================

ALTER TABLE `bjtu_food`.`dish_attribute_value`
    ADD UNIQUE KEY `uk_dimension_id_name` (`dimension_id`, `name`)
    COMMENT '一个维度同一名称只能存在一条记录';
