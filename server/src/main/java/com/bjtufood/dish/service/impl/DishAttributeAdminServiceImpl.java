package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.dto.SortItem;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
import com.bjtufood.common.utils.JsonMapUtil;
import com.bjtufood.common.utils.SortReorderUtil;
import com.bjtufood.dish.dto.DishDimensionAdminVO;
import com.bjtufood.dish.dto.DishValueAdminVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishAttributeDimension;
import com.bjtufood.dish.entity.DishAttributeValue;
import com.bjtufood.dish.mapper.DishAttributeDimensionMapper;
import com.bjtufood.dish.mapper.DishAttributeValueMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishAttributeCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A4 属性维度与取值管理实现（含属性值读写口径转换）。
 * <p>
 * <b>计数策略</b>：维度 / 取值的引用计数（`dishCount`）与「被引用不可删」判据都需扫菜品行 ——
 * 描述维度的引用在 `dish.attributes`、**系统维度（菜品种类）的引用在 `dish.meal_type_id`**；
 * 以「一次拉回 id + attributes + meal_type_id 后在内存聚合」实现（管理端低频操作，口径与
 * `DishAttributeCatalog` 的候选聚合一致，避免为每个维度/取值单发 COUNT 造成 N+1）。
 */
@Service
@RequiredArgsConstructor
public class DishAttributeAdminServiceImpl implements DishAttributeAdminService {

    private static final String TYPE_SINGLE = "single";
    private static final String TYPE_MULTI = "multi";
    private static final int LABEL_MAX = 32;

    private final DishAttributeDimensionMapper dimensionMapper;
    private final DishAttributeValueMapper valueMapper;
    private final DishMapper dishMapper;
    /** 目录缓存（维度字典 + 编辑候选值）：A4 写入口必须显式失效，否则最长 10 分钟不可见 */
    private final DishAttributeCatalog attributeCatalog;

    /** 维度 / 取值发生写入后统一失效两块目录缓存 */
    private void invalidateCatalog() {
        attributeCatalog.invalidateDimensions();
        attributeCatalog.invalidateCandidates();
    }

    // ==================== 维度 ====================

    @Override
    public Long systemDimensionId() {
        DishAttributeDimension system = firstSystemDimension();
        if (system == null) {
            // 建库未完成（系统维度行缺失）：属服务端状态问题，不是调用方的错
            throw new BusinessException(500, "系统维度（菜品种类）未初始化");
        }
        return system.getId();
    }

    /** 系统维度行（`system = 1`；建库维护，至多一行）；未初始化返回 {@code null}。 */
    private DishAttributeDimension firstSystemDimension() {
        List<DishAttributeDimension> rows = dimensionMapper.selectList(new LambdaQueryWrapper<DishAttributeDimension>()
                .eq(DishAttributeDimension::getSystem, true)
                .orderByAsc(DishAttributeDimension::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static boolean isSystem(DishAttributeDimension dimension) {
        return dimension != null && Boolean.TRUE.equals(dimension.getSystem());
    }

    @Override
    public List<DishDimensionAdminVO> listDimensions() {
        List<DishAttributeDimension> dimensions = dimensionMapper.selectList(
                new LambdaQueryWrapper<DishAttributeDimension>().orderByAsc(DishAttributeDimension::getSortOrder));
        Usage usage = scanUsage();
        return dimensions.stream().map(d -> {
            DishDimensionAdminVO vo = new DishDimensionAdminVO();
            vo.setId(d.getId());
            vo.setName(d.getName());
            vo.setValueType(d.getValueType());
            vo.setSystem(isSystem(d));
            vo.setOrder(d.getSortOrder());
            vo.setDishCount(usage.dishCountByDimensionId.getOrDefault(d.getId(), 0L));
            vo.setValueCount(usage.valueCountByDimensionId.getOrDefault(d.getId(), 0L));
            vo.setUpdatedAt(d.getUpdatedAt());
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishDimensionAdminVO createDimension(String name, String valueType) {
        String dimensionName = requireText(name, "维度名", LABEL_MAX);
        String type = requireValueType(valueType);
        DishAttributeDimension entity = new DishAttributeDimension();
        entity.setName(dimensionName);
        entity.setValueType(type);
        entity.setSortOrder(nextDimensionOrder());
        // 新建的一律是描述维度（system 落库默认 0）：系统维度由建库维护，无新增入口
        dimensionMapper.insert(entity);
        DishAttributeDimension saved = dimensionMapper.selectById(entity.getId());
        DishDimensionAdminVO vo = new DishDimensionAdminVO();
        vo.setId(saved.getId());
        vo.setName(saved.getName());
        vo.setValueType(saved.getValueType());
        vo.setSystem(isSystem(saved));
        vo.setOrder(saved.getSortOrder());
        vo.setDishCount(0L);
        vo.setValueCount(0L);
        vo.setUpdatedAt(saved.getUpdatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDimension(Long id, String name, String valueType) {
        DishAttributeDimension current = dimensionMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(4001, "维度不存在");
        }
        String dimensionName = requireText(name, "维度名", LABEL_MAX);
        String type = requireValueType(valueType);
        boolean typeChanged = !type.equals(current.getValueType());
        // 系统维度（菜品种类）恒单值：改 valueType 等于改菜品的分类语义，一律拒绝（name 仍可改）
        if (isSystem(current) && typeChanged) {
            throw new BusinessException("系统维度（菜品种类）的取值类型不可修改");
        }
        DishAttributeDimension update = new DishAttributeDimension();
        update.setId(id);
        update.setName(dimensionName);
        update.setValueType(type);
        dimensionMapper.updateById(update);
        // 单/多选切换：同事务内迁移该维度下菜品的数据形状（标量 ↔ 数组）
        if (typeChanged) {
            migrateShape(current.getId(), type);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDimension(Long id) {
        DishAttributeDimension current = dimensionMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(4001, "维度不存在");
        }
        if (isSystem(current)) {
            // 其取值被 dish.meal_type_id 引用，删维度会留下悬空种类
            throw new BusinessException("系统维度（菜品种类）不可删除");
        }
        Usage usage = scanUsage();
        long used = usage.dishCountByDimensionId.getOrDefault(current.getId(), 0L);
        if (used > 0) {
            throw new BusinessException("仍有 " + used + " 个菜品使用该维度，不能删除（请先改菜品）");
        }
        // 维度未被引用 ⇒ 其下取值一并清理（取值无独立引用主体）
        valueMapper.delete(new LambdaQueryWrapper<DishAttributeValue>()
                .eq(DishAttributeValue::getDimensionId, id));
        dimensionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortDimensions(List<SortItem> items) {
        List<Long> existing = dimensionMapper.selectList(null).stream()
                .map(DishAttributeDimension::getId).toList();
        Map<Long, Integer> ordered = SortReorderUtil.resolve(items, existing);
        for (Map.Entry<Long, Integer> e : ordered.entrySet()) {
            DishAttributeDimension update = new DishAttributeDimension();
            update.setId(e.getKey());
            update.setSortOrder(e.getValue());
            dimensionMapper.updateById(update);
        }
    }

    // ==================== 取值 ====================

    @Override
    public List<DishValueAdminVO> listValues(Long dimensionId) {
        requireDimension(dimensionId);
        List<DishAttributeValue> values = valueMapper.selectList(new LambdaQueryWrapper<DishAttributeValue>()
                .eq(DishAttributeValue::getDimensionId, dimensionId)
                .orderByAsc(DishAttributeValue::getSortOrder));
        Usage usage = scanUsage();
        return values.stream().map(v -> toValueVO(v, usage)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishValueAdminVO createValue(Long dimensionId, String label) {
        requireDimension(dimensionId);
        String text = requireText(label, "取值名", LABEL_MAX);
        requireLabelUnique(dimensionId, text, null);
        DishAttributeValue entity = new DishAttributeValue();
        entity.setDimensionId(dimensionId);
        entity.setLabel(text);
        entity.setSortOrder(nextValueOrder(dimensionId));
        valueMapper.insert(entity);
        return toValueVO(valueMapper.selectById(entity.getId()), scanUsage());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateValue(Long dimensionId, Long valueId, String label) {
        DishAttributeValue current = requireValue(dimensionId, valueId);
        String text = requireText(label, "取值名", LABEL_MAX);
        requireLabelUnique(dimensionId, text, current.getId());
        DishAttributeValue update = new DishAttributeValue();
        update.setId(valueId);
        update.setLabel(text);
        // 改名免费：菜品存的是 ID，无需迁移任何菜品数据
        valueMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteValue(Long dimensionId, Long valueId) {
        requireValue(dimensionId, valueId);
        Usage usage = scanUsage();
        long used = usage.dishCountByValueId.getOrDefault(valueId, 0L);
        if (used > 0) {
            throw new BusinessException("仍有 " + used + " 个菜品引用该取值，不能删除");
        }
        valueMapper.deleteById(valueId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortValues(Long dimensionId, List<SortItem> items) {
        requireDimension(dimensionId);
        List<Long> existing = valueMapper.selectList(new LambdaQueryWrapper<DishAttributeValue>()
                        .eq(DishAttributeValue::getDimensionId, dimensionId))
                .stream().map(DishAttributeValue::getId).toList();
        Map<Long, Integer> ordered = SortReorderUtil.resolve(items, existing);
        for (Map.Entry<Long, Integer> e : ordered.entrySet()) {
            DishAttributeValue update = new DishAttributeValue();
            update.setId(e.getKey());
            update.setSortOrder(e.getValue());
            valueMapper.updateById(update);
        }
    }

    // ==================== 属性值读写口径转换 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resolveForWrite(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        raw.forEach((key, value) -> {
            DishAttributeDimension dimension = dimensionById(key);
            if (dimension == null) {
                // 维度 ID 不在白名单（= 维度表）→ 400
                throw new BusinessException("未知的属性维度：" + key);
            }
            if (isSystem(dimension)) {
                // 菜品种类只走 dish.meal_type_id：同一事实不进两处
                throw new BusinessException("菜品种类不是描述属性，请用 mealTypeId 提交：" + key);
            }
            String dimensionKey = String.valueOf(dimension.getId());
            boolean multi = TYPE_MULTI.equals(dimension.getValueType());
            if (value == null) {
                return; // null ⇒ 不落该维度（清空语义）
            }
            if (value instanceof Collection<?> items) {
                if (items.isEmpty()) {
                    return; // 空数组 ⇒ 清空该维度（不落键）
                }
                if (!multi) {
                    // 单值维度收到数组：取首项（宽容），避免前端形态差异导致 400
                    Object first = items.iterator().next();
                    result.put(dimensionKey, resolveSingle(dimension, first));
                    return;
                }
                List<Object> ids = new ArrayList<>(items.size());
                for (Object item : items) {
                    ids.add(resolveSingle(dimension, item));
                }
                result.put(dimensionKey, ids);
                return;
            }
            result.put(dimensionKey, multi ? List.of(resolveSingle(dimension, value)) : resolveSingle(dimension, value));
        });
        return result;
    }

    /** 键（字符串形态的维度 ID）→ 维度；非数字或维度不存在返回 null */
    private DishAttributeDimension dimensionById(Object key) {
        if (key == null) {
            return null;
        }
        String text = String.valueOf(key).trim();
        if (!text.matches("\\d+")) {
            return null;
        }
        return dimensionMapper.selectById(Long.valueOf(text));
    }

    /** 单个值解析：数字 ⇒ 校验 ID 存在；文本 ⇒ 查字典，未命中自动登记 */
    private Object resolveSingle(DishAttributeDimension dimension, Object value) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("属性取值不能为空");
        }
        DishAttributeValue existing;
        if (text.matches("\\d+")) {
            // 传 ID：必须属于本维度（防跨维度串值）
            existing = valueMapper.selectOne(new LambdaQueryWrapper<DishAttributeValue>()
                    .eq(DishAttributeValue::getId, Long.valueOf(text))
                    .eq(DishAttributeValue::getDimensionId, dimension.getId())
                    .last("LIMIT 1"));
            if (existing == null) {
                throw new BusinessException("属性取值 ID 不存在：" + text);
            }
        } else {
            existing = valueMapper.selectOne(new LambdaQueryWrapper<DishAttributeValue>()
                    .eq(DishAttributeValue::getDimensionId, dimension.getId())
                    .eq(DishAttributeValue::getLabel, text)
                    .last("LIMIT 1"));
            if (existing == null) {
                // 未命中 ⇒ 自动登记（管理端录入新值 / 纠错采纳共用同一逻辑）
                if (text.length() > LABEL_MAX) {
                    throw new BusinessException("属性取值不能超过 " + LABEL_MAX + " 字");
                }
                DishAttributeValue created = new DishAttributeValue();
                created.setDimensionId(dimension.getId());
                created.setLabel(text);
                created.setSortOrder(nextValueOrder(dimension.getId()));
                valueMapper.insert(created);
                existing = created;
            }
        }
        return existing.getId();
    }

    @Override
    public Map<String, Object> translateForRead(String attributesJson) {
        Map<String, Object> parsed = JsonMapUtil.parseObject(attributesJson);
        if (parsed.isEmpty()) {
            return new LinkedHashMap<>();
        }
        // 全量字典一次拉回：id → label（取值规模极小，几十行）
        Map<Long, String> labelById = new HashMap<>();
        for (DishAttributeValue v : valueMapper.selectList(null)) {
            labelById.put(v.getId(), v.getLabel());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        parsed.forEach((dimensionKey, value) -> {
            if (value instanceof Collection<?> items) {
                List<String> labels = new ArrayList<>(items.size());
                for (Object item : items) {
                    labels.add(toLabel(labelById, item));
                }
                out.put(dimensionKey, labels);
            } else {
                out.put(dimensionKey, toLabel(labelById, value));
            }
        });
        return out;
    }

    /** 取值 ID → 中文；悬空 ID 原样保留，不丢数据 */
    private static String toLabel(Map<Long, String> labelById, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.matches("\\d+")) {
            String label = labelById.get(Long.valueOf(text));
            return label != null ? label : text;
        }
        return text;
    }

    @Override
    public Map<Long, String> labelByIdForDimension(Long dimensionId) {
        Map<Long, String> map = new HashMap<>();
        for (DishAttributeValue v : valueMapper.selectList(new LambdaQueryWrapper<DishAttributeValue>()
                .eq(DishAttributeValue::getDimensionId, dimensionId))) {
            map.put(v.getId(), v.getLabel());
        }
        return map;
    }

    // ==================== 内部：形状迁移与聚合 ====================

    /**
     * 单/多选切换时的数据形状迁移：`single` ⇄ `multi`（标量 ↔ 单元素数组）。
     * 逐行读取 + 回写（仅影响该维度键），规模可控且**幂等**（已是目标形状则跳过）。
     */
    private void migrateShape(Long dimensionId, String targetType) {
        String key = String.valueOf(dimensionId);
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .select(Dish::getId, Dish::getAttributes)
                .isNotNull(Dish::getAttributes));
        boolean toMulti = TYPE_MULTI.equals(targetType);
        for (Dish dish : dishes) {
            Map<String, Object> parsed = JsonMapUtil.parseObject(dish.getAttributes());
            if (!parsed.containsKey(key)) {
                continue;
            }
            Object current = parsed.get(key);
            if (toMulti && !(current instanceof Collection<?>)) {
                parsed.put(key, current == null ? List.of() : List.of(current));
            } else if (!toMulti && current instanceof Collection<?> items) {
                parsed.put(key, items.isEmpty() ? null : items.iterator().next());
            } else {
                continue;
            }
            Dish update = new Dish();
            update.setId(dish.getId());
            update.setAttributes(JsonMapUtil.toJson(parsed));
            dishMapper.updateById(update);
        }
    }

    /** 一次扫描聚合：维度被引用菜品数 / 取值被引用菜品数 / 各维度取值数 */
    private Usage scanUsage() {
        Usage usage = new Usage();
        for (DishAttributeValue v : valueMapper.selectList(null)) {
            usage.valueCountByDimensionId.merge(v.getDimensionId(), 1L, Long::sum);
        }
        DishAttributeDimension system = firstSystemDimension();
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .select(Dish::getId, Dish::getAttributes, Dish::getMealTypeId));
        for (Dish dish : dishes) {
            // 系统维度（菜品种类）：引用在独立列 dish.meal_type_id，不在 attributes 里
            if (system != null && dish.getMealTypeId() != null) {
                usage.dishCountByDimensionId.merge(system.getId(), 1L, Long::sum);
                usage.dishCountByValueId.merge(dish.getMealTypeId(), 1L, Long::sum);
            }
            Map<String, Object> parsed = JsonMapUtil.parseObject(dish.getAttributes());
            parsed.forEach((dimensionKey, value) -> {
                if (value == null || (value instanceof Collection<?> c && c.isEmpty())) {
                    return;
                }
                if (dimensionKey != null && dimensionKey.matches("\\d+")) {
                    usage.dishCountByDimensionId.merge(Long.valueOf(dimensionKey), 1L, Long::sum);
                }
                for (Object item : flatten(value)) {
                    String text = item == null ? null : String.valueOf(item).trim();
                    if (text != null && text.matches("\\d+")) {
                        usage.dishCountByValueId.merge(Long.valueOf(text), 1L, Long::sum);
                    }
                }
            });
        }
        return usage;
    }

    private static List<Object> flatten(Object value) {
        if (value instanceof Collection<?> items) {
            return new ArrayList<>(items);
        }
        return List.of(value);
    }

    /** 聚合结果容器（内部） */
    private static final class Usage {
        final Map<Long, Long> dishCountByDimensionId = new HashMap<>();
        final Map<Long, Long> dishCountByValueId = new HashMap<>();
        final Map<Long, Long> valueCountByDimensionId = new HashMap<>();
    }

    private DishAttributeDimension requireDimension(Long id) {
        DishAttributeDimension dimension = id == null ? null : dimensionMapper.selectById(id);
        if (dimension == null) {
            throw new BusinessException(4001, "维度不存在");
        }
        return dimension;
    }

    private DishAttributeValue requireValue(Long dimensionId, Long valueId) {
        requireDimension(dimensionId);
        DishAttributeValue value = valueId == null ? null : valueMapper.selectById(valueId);
        if (value == null || !dimensionId.equals(value.getDimensionId())) {
            throw new BusinessException(4001, "取值不存在");
        }
        return value;
    }

    private void requireLabelUnique(Long dimensionId, String label, Long excludeId) {
        LambdaQueryWrapper<DishAttributeValue> wrapper = new LambdaQueryWrapper<DishAttributeValue>()
                .eq(DishAttributeValue::getDimensionId, dimensionId)
                .eq(DishAttributeValue::getLabel, label);
        if (excludeId != null) {
            wrapper.ne(DishAttributeValue::getId, excludeId);
        }
        DuplicateGuard.assertUnique(valueMapper, wrapper, "该取值已存在");
    }

    private DishValueAdminVO toValueVO(DishAttributeValue v, Usage usage) {
        DishValueAdminVO vo = new DishValueAdminVO();
        vo.setId(v.getId());
        vo.setDimensionId(v.getDimensionId());
        vo.setLabel(v.getLabel());
        vo.setOrder(v.getSortOrder());
        vo.setDishCount(usage.dishCountByValueId.getOrDefault(v.getId(), 0L));
        vo.setUpdatedAt(v.getUpdatedAt());
        return vo;
    }

    private int nextDimensionOrder() {
        List<DishAttributeDimension> all = dimensionMapper.selectList(
                new LambdaQueryWrapper<DishAttributeDimension>().orderByDesc(DishAttributeDimension::getSortOrder));
        return all.isEmpty() || all.get(0).getSortOrder() == null ? 1 : all.get(0).getSortOrder() + 1;
    }

    private int nextValueOrder(Long dimensionId) {
        List<DishAttributeValue> all = valueMapper.selectList(new LambdaQueryWrapper<DishAttributeValue>()
                .eq(DishAttributeValue::getDimensionId, dimensionId)
                .orderByDesc(DishAttributeValue::getSortOrder));
        return all.isEmpty() || all.get(0).getSortOrder() == null ? 1 : all.get(0).getSortOrder() + 1;
    }

    private static String requireText(String text, String what, int max) {
        String v = text == null ? null : text.trim();
        if (!StringUtils.hasText(v)) {
            throw new BusinessException(what + "不能为空");
        }
        if (v.length() > max) {
            throw new BusinessException(what + "不能超过 " + max + " 字");
        }
        return v;
    }

    private static String requireValueType(String valueType) {
        String type = valueType == null ? null : valueType.trim();
        if (!TYPE_SINGLE.equals(type) && !TYPE_MULTI.equals(type)) {
            throw new BusinessException("取值类型非法（仅 single / multi）");
        }
        return type;
    }
}
