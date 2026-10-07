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

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A6 分类值字典管理实现。
 * <p>
 * 数据锚在 `id`（`dish.meal_type` 存的就是它）⇒ 改名免费、零菜品迁移；
 * `key` 只服务**代码**（内置视图常量按 `key` 引用分类），新建时选填、缺省自动生成（`cat-` + 8 位小写十六进制）。
 */
@Service
@RequiredArgsConstructor
public class DishCategoryAdminServiceImpl implements DishCategoryAdminService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9-]{1,20}$");
    private static final int LABEL_MAX = 32;
    /** 自动生成的分类键前缀（与手填的语义键形态区分，一眼可辨） */
    private static final String GENERATED_KEY_PREFIX = "cat-";
    private static final int GENERATED_KEY_RANDOM_LEN = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DishCategoryValueMapper categoryMapper;
    private final DishMapper dishMapper;
    // 注：分类值**自身无缓存**（值域只在写入校验与下拉里读），故本类不做缓存失效；
    // 视图侧目录缓存（`GET /dishes/views` 的 @Cacheable）由 DishViewCatalog 写侧显式失效。

    @Override
    public List<DishCategoryAdminVO> listAll() {
        Map<Long, Long> countById = dishCountById();
        return categoryMapper.selectList(new LambdaQueryWrapper<DishCategoryValue>()
                        .orderByAsc(DishCategoryValue::getSortOrder)
                        .orderByAsc(DishCategoryValue::getId))
                .stream()
                .map(c -> toVO(c, countById))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishCategoryAdminVO create(String key, String label) {
        String normalizedKey = StringUtils.hasText(key) ? normalizeKey(key) : generateKey();
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
        return toVO(categoryMapper.selectById(entity.getId()), dishCountById());
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
        // 改名免费：dish.meal_type 存的是 id，零菜品迁移
        categoryMapper.updateById(update);
    }

    @Override
    public void requireExists(Long categoryId) {
        if (categoryId == null || categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("分类值不存在：" + categoryId);
        }
    }

    // ==================== 内部工具 ====================

    private boolean existsByKey(String key) {
        return categoryMapper.selectCount(new LambdaQueryWrapper<DishCategoryValue>()
                .eq(DishCategoryValue::getKey, key)) > 0;
    }

    /**
     * 自动生成唯一分类键：`cat-` + 8 位小写十六进制。
     * 唯一索引（`uk_category_key`）兜底，生成侧仍先查重（16^8 空间内碰撞概率可忽略，循环仅为防御）。
     */
    private String generateKey() {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder sb = new StringBuilder(GENERATED_KEY_PREFIX);
            for (int i = 0; i < GENERATED_KEY_RANDOM_LEN; i++) {
                sb.append(Integer.toHexString(RANDOM.nextInt(0x10)));
            }
            String candidate = sb.toString();
            if (!existsByKey(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("分类键生成失败，请重试或手动指定分类键");
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
     * 各分类的引用菜品数（按 `meal_type` = 分类 ID 统计）。
     * <p>
     * 「取全部非空 meal_type 后内存归并」（分类值量级极小、菜品量级百至千），
     * 沿用与 A4/A7 一致的「一次拉回后在内存聚合」口径，避免为每个分类单发 COUNT。
     */
    private Map<Long, Long> dishCountById() {
        Map<Long, Long> counts = new HashMap<>();
        for (Dish dish : dishMapper.selectList(new LambdaQueryWrapper<Dish>().select(Dish::getId, Dish::getMealType))) {
            if (dish.getMealType() != null) {
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

    private DishCategoryAdminVO toVO(DishCategoryValue entity, Map<Long, Long> countById) {
        DishCategoryAdminVO vo = new DishCategoryAdminVO();
        vo.setId(entity.getId());
        vo.setKey(entity.getKey());
        vo.setLabel(entity.getLabel());
        vo.setOrder(entity.getSortOrder());
        vo.setDishCount(countById.getOrDefault(entity.getId(), 0L));
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
