package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
import com.bjtufood.dish.dto.DishCategoryAdminVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishCategoryValue;
import com.bjtufood.dish.mapper.DishCategoryValueMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishCategoryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A6 分类值字典管理实现。
 * <p>
 * 分类值由**自由输入产生**（A3 菜品录入），后台只保留**重命名**；
 * 数据锚在 `key`（`dish.meal_type` 存的就是它）⇒ 改名免费、零菜品迁移。
 */
@Service
@RequiredArgsConstructor
public class DishCategoryAdminServiceImpl implements DishCategoryAdminService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9-]{1,20}$");
    private static final int LABEL_MAX = 32;
    /** 自动登记（A3 自由输入）时新分类的默认排序：排在种子项之后 */
    private static final int AUTO_REGISTER_ORDER = 99;

    private final DishCategoryValueMapper categoryMapper;
    private final DishMapper dishMapper;
    // 注：分类值**自身无缓存**（值域只在写入校验与下拉里读），故本类不做缓存失效；
    // 视图侧目录缓存（`GET /dishes/views` 的 @Cacheable）由 DishViewCatalog 写侧显式失效。

    @Override
    public List<DishCategoryAdminVO> listAll() {
        Map<String, Long> countByKey = dishCountByKey();
        return categoryMapper.selectList(new LambdaQueryWrapper<DishCategoryValue>()
                        .orderByAsc(DishCategoryValue::getSortOrder)
                        .orderByAsc(DishCategoryValue::getId))
                .stream()
                .map(c -> toVO(c, countByKey))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishCategoryAdminVO create(String key, String label) {
        String normalizedKey = normalizeKey(key);
        String normalizedLabel = normalizeLabel(label);
        if (existsByKey(normalizedKey)) {
            throw new BusinessException("分类键已存在");
        }
        requireLabelUnique(normalizedLabel, null);
        DishCategoryValue entity = new DishCategoryValue();
        entity.setKey(normalizedKey);
        entity.setLabel(normalizedLabel);
        entity.setSortOrder(nextOrder());
        categoryMapper.insert(entity);
        return toVO(categoryMapper.selectById(entity.getId()), dishCountByKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String label) {
        requireExists(id);
        String normalizedLabel = normalizeLabel(label);
        requireLabelUnique(normalizedLabel, id);
        DishCategoryValue update = new DishCategoryValue();
        update.setId(id);
        update.setLabel(normalizedLabel);
        // 改名免费：dish.meal_type 存的是 key，零菜品迁移
        categoryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resolveOrRegister(String key) {
        String normalizedKey = normalizeKey(key);
        if (existsByKey(normalizedKey)) {
            return normalizedKey;
        }
        // 自由输入产生的新分类：以键为初名登记（管理员可改名）
        DishCategoryValue entity = new DishCategoryValue();
        entity.setKey(normalizedKey);
        entity.setLabel(normalizedKey);
        entity.setSortOrder(AUTO_REGISTER_ORDER);
        categoryMapper.insert(entity);
        return normalizedKey;
    }

    // ==================== 内部工具 ====================

    private boolean existsByKey(String key) {
        return categoryMapper.selectCount(new LambdaQueryWrapper<DishCategoryValue>()
                .eq(DishCategoryValue::getKey, key)) > 0;
    }

    private void requireExists(Long id) {
        if (id == null || categoryMapper.selectById(id) == null) {
            throw new BusinessException(4001, "分类值不存在");
        }
    }

    private void requireLabelUnique(String label, Long excludeId) {
        LambdaQueryWrapper<DishCategoryValue> wrapper = new LambdaQueryWrapper<DishCategoryValue>()
                .eq(DishCategoryValue::getLabel, label);
        if (excludeId != null) {
            wrapper.ne(DishCategoryValue::getId, excludeId);
        }
        DuplicateGuard.assertUnique(categoryMapper, wrapper, "分类名已存在");
    }

    /**
     * 各分类的引用菜品数。
     * <p>
     * 一次按 `meal_type` 分组统计的实现代价高于「取全部非空 meal_type 后内存归并」（分类值量级极小、
     * 菜品量级百至千），故沿用与 A4/A7 一致的「一次拉回后在内存聚合」口径，避免为每个分类单发 COUNT。
     */
    private Map<String, Long> dishCountByKey() {
        Map<String, Long> counts = new HashMap<>();
        for (Dish dish : dishMapper.selectList(new LambdaQueryWrapper<Dish>().select(Dish::getId, Dish::getMealType))) {
            if (StringUtils.hasText(dish.getMealType())) {
                counts.merge(dish.getMealType(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private int nextOrder() {
        List<DishCategoryValue> all = categoryMapper.selectList(new LambdaQueryWrapper<DishCategoryValue>()
                .orderByDesc(DishCategoryValue::getSortOrder));
        return all.isEmpty() || all.get(0).getSortOrder() == null ? 1 : all.get(0).getSortOrder() + 1;
    }

    private static String normalizeKey(String key) {
        String v = key == null ? null : key.trim();
        if (v == null || !KEY_PATTERN.matcher(v).matches()) {
            throw new BusinessException("分类键只能包含小写字母、数字与 -，长度 1~20");
        }
        return v;
    }

    private static String normalizeLabel(String label) {
        String v = label == null ? null : label.trim();
        if (!StringUtils.hasText(v)) {
            throw new BusinessException("分类名不能为空");
        }
        if (v.length() > LABEL_MAX) {
            throw new BusinessException("分类名不能超过 " + LABEL_MAX + " 字");
        }
        return v;
    }

    private DishCategoryAdminVO toVO(DishCategoryValue entity, Map<String, Long> countByKey) {
        DishCategoryAdminVO vo = new DishCategoryAdminVO();
        vo.setId(entity.getId());
        vo.setKey(entity.getKey());
        vo.setLabel(entity.getLabel());
        vo.setOrder(entity.getSortOrder());
        vo.setDishCount(countByKey.getOrDefault(entity.getKey(), 0L));
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
