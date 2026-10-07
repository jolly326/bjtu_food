package com.bjtufood.dish.service.impl;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishCategoryAdminVO;
import com.bjtufood.dish.dto.DishValueAdminVO;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishCategoryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * A6 菜品种类字典管理实现 —— **系统维度取值的别名面**。
 * <p>
 * 不持有任何字典表：取值能力（登记 / 改名 / 排序 / 引用计数 / 系统维度保护）全部由
 * {@link DishAttributeAdminService} 承担，本类只做「定位系统维度 + 字段收敛」两件事，
 * 保证 `/admin/dish-categories` 与 A4 的取值端点**读写同一批行**、无第二实现。
 */
@Service
@RequiredArgsConstructor
public class DishCategoryAdminServiceImpl implements DishCategoryAdminService {

    private final DishAttributeAdminService attributeAdminService;

    @Override
    public List<DishCategoryAdminVO> listAll() {
        return attributeAdminService.listValues(systemDimensionId()).stream().map(DishCategoryAdminServiceImpl::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishCategoryAdminVO create(String label) {
        return toVO(attributeAdminService.createValue(systemDimensionId(), label));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String label) {
        // 改名免费：dish.meal_type_id 存的是取值 id，零菜品迁移
        attributeAdminService.updateValue(systemDimensionId(), id, label);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<SortItem> items) {
        attributeAdminService.sortValues(systemDimensionId(), items);
    }

    @Override
    public void requireExists(Long valueId) {
        if (valueId == null) {
            throw new BusinessException("分类值不存在：" + valueId);
        }
        // 只查该维度下的取值（轻量），判定「属于系统维度」而非「ID 存在」
        Map<Long, String> labelById = attributeAdminService.labelByIdForDimension(systemDimensionId());
        if (!labelById.containsKey(valueId)) {
            throw new BusinessException("分类值不存在：" + valueId);
        }
    }

    // ==================== 内部工具 ====================

    private Long systemDimensionId() {
        return attributeAdminService.systemDimensionId();
    }

    private static DishCategoryAdminVO toVO(DishValueAdminVO value) {
        DishCategoryAdminVO vo = new DishCategoryAdminVO();
        vo.setId(value.getId());
        vo.setLabel(value.getLabel());
        vo.setOrder(value.getOrder());
        vo.setDishCount(value.getDishCount());
        vo.setUpdatedAt(value.getUpdatedAt());
        return vo;
    }
}
