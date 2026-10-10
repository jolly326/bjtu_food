package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
import com.bjtufood.dish.dto.DishCategoryAdminVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishCategory;
import com.bjtufood.dish.mapper.DishCategoryMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishCategoryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A6 菜品种类字典管理实现（字典真源 {@code dish_category}）。
 * <p>
 * <b>计数策略</b>：种类的引用计数（`dishCount`）需数 {@code dish} 行 —— 以「一次拉回
 * `category_id` 后在内存聚合」实现（管理端低频操作，避免为每个种类单发 COUNT 造成 N+1）。
 */
@Service
@RequiredArgsConstructor
public class DishCategoryAdminServiceImpl implements DishCategoryAdminService {

    private static final int NAME_MAX = 32;

    private final DishCategoryMapper categoryMapper;
    private final DishMapper dishMapper;

    @Override
    public List<DishCategoryAdminVO> listAll() {
        Map<Long, Long> counts = dishCountByCategoryId();
        return categoryMapper.selectList(new LambdaQueryWrapper<DishCategory>()
                        .orderByAsc(DishCategory::getId))
                .stream()
                .map(category -> toVO(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishCategoryAdminVO create(String name) {
        String text = requireText(name);
        requireNameUnique(text, null);
        DishCategory entity = new DishCategory();
        entity.setName(text);
        categoryMapper.insert(entity);
        // 时间列以回查为准；新建的种类无菜品引用
        return toVO(categoryMapper.selectById(entity.getId()), 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String name) {
        requireRow(id);
        String text = requireText(name);
        requireNameUnique(text, id);
        DishCategory update = new DishCategory();
        update.setId(id);
        update.setName(text);
        // 改名免费：菜品存的是种类 ID，无需迁移任何菜品数据
        categoryMapper.updateById(update);
    }

    @Override
    public void requireExists(Long categoryId) {
        if (categoryId == null || categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("分类不存在：" + categoryId);
        }
    }

    // ==================== 内部工具 ====================

    /** 目标行存在性校验（管理端按 id 操作自身资源）：不存在 → `4001` */
    private DishCategory requireRow(Long id) {
        DishCategory row = id == null ? null : categoryMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(4001, "分类不存在");
        }
        return row;
    }

    /** 一次扫描聚合：种类 ID → 引用该种类的菜品数（`dish.category_id` 为空的行不计入任何种类） */
    private Map<Long, Long> dishCountByCategoryId() {
        Map<Long, Long> counts = new HashMap<>();
        for (Dish dish : dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .select(Dish::getId, Dish::getCategoryId))) {
            if (dish.getCategoryId() != null) {
                counts.merge(dish.getCategoryId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private void requireNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<DishCategory> wrapper = new LambdaQueryWrapper<DishCategory>()
                .eq(DishCategory::getName, name);
        if (excludeId != null) {
            wrapper.ne(DishCategory::getId, excludeId);
        }
        DuplicateGuard.assertUnique(categoryMapper, wrapper, "该分类已存在");
    }

    private static String requireText(String name) {
        String text = name == null ? null : name.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("分类名不能为空");
        }
        if (text.length() > NAME_MAX) {
            throw new BusinessException("分类名不能超过 " + NAME_MAX + " 字");
        }
        return text;
    }

    private static DishCategoryAdminVO toVO(DishCategory entity, long dishCount) {
        DishCategoryAdminVO vo = new DishCategoryAdminVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDishCount(dishCount);
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
