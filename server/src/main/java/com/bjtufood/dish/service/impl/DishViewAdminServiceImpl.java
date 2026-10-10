package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewCreateReq;
import com.bjtufood.dish.dto.DishViewUpdateReq;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.service.DishCategoryAdminService;
import com.bjtufood.dish.service.DishViewAdminService;
import com.bjtufood.dish.service.DishViewCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * A6 视图管理服务实现。
 * <p>
 * 视图的**文案 / 启停 / 归属种类**全由本类写入（归属种类的存在性校验收口在
 * {@link DishCategoryAdminService#requireExists}）；写入口（新建 / 修改 / 删除）在事务内调用
 * {@link DishViewCatalog#invalidateViews()} —— 把 `GET /dishes/views` 的 TTL 换成
 * **保存即生效**（契约见 A6 的「客户端可见性规则」）。
 */
@Service
@RequiredArgsConstructor
public class DishViewAdminServiceImpl implements DishViewAdminService {

    private static final int NAME_MAX = 32;

    private final DishFilterViewMapper viewMapper;
    private final DishViewCatalog viewCatalog;
    private final DishCategoryAdminService categoryAdminService;

    @Override
    public List<DishViewAdminVO> listAll() {
        return viewCatalog.all().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishViewAdminVO create(DishViewCreateReq req) {
        DishFilterView entity = new DishFilterView();
        entity.setName(normalizeName(req.getName()));
        applyCategory(entity, req.getCategoryId());
        // 新建即启用：否则「新建」在端上不可见，运营会误以为没生效
        entity.setEnabled(true);
        viewMapper.insert(entity);
        viewCatalog.invalidateViews();
        return toVO(viewMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DishViewUpdateReq req) {
        DishFilterView current = requireExists(id);
        String name = normalizeName(req.getName());
        // 「不允许停用最后一个启用的视图」：仅当本次把一行从启用改为停用时才校验（避免下发规则自相矛盾）
        if (Boolean.TRUE.equals(current.getEnabled()) && Boolean.FALSE.equals(req.getEnabled())) {
            requireOtherEnabledExists(id, "不允许停用最后一个启用的视图");
        }
        DishFilterView update = new DishFilterView();
        update.setId(id);
        update.setName(name);
        update.setEnabled(req.getEnabled());
        applyCategory(update, req.getCategoryId());
        viewMapper.updateById(update);
        viewCatalog.invalidateViews();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DishFilterView current = requireExists(id);
        // 删除与停用同一道护栏：删掉最后一个启用视图，端上筛选栏同样会空
        if (Boolean.TRUE.equals(current.getEnabled())) {
            requireOtherEnabledExists(id, "不允许删除最后一个启用的视图");
        }
        viewMapper.deleteById(id);
        viewCatalog.invalidateViews();
    }

    // ==================== 内部工具 ====================

    private DishFilterView requireExists(Long id) {
        DishFilterView view = id == null ? null : viewMapper.selectById(id);
        if (view == null) {
            throw new BusinessException(4001, "视图不存在");
        }
        return view;
    }

    /** 除该 id 外是否还有启用视图；没有则以给定文案抛 `400`。 */
    private void requireOtherEnabledExists(Long id, String message) {
        long otherEnabled = viewMapper.selectCount(new LambdaQueryWrapper<DishFilterView>()
                .eq(DishFilterView::getEnabled, true)
                .ne(DishFilterView::getId, id));
        if (otherEnabled == 0) {
            throw new BusinessException(message);
        }
    }

    /** 归属种类写入：null = 不按种类过滤；非 null 必须是种类字典内的行（不存在 → 400）。 */
    private void applyCategory(DishFilterView entity, Long categoryId) {
        if (categoryId != null) {
            categoryAdminService.requireExists(categoryId);
        }
        entity.setCategoryId(categoryId);
    }

    private static String normalizeName(String name) {
        String v = name == null ? null : name.trim();
        if (!StringUtils.hasText(v)) {
            throw new BusinessException("tab 文案不能为空");
        }
        if (v.length() > NAME_MAX) {
            throw new BusinessException("tab 文案不能超过 " + NAME_MAX + " 字");
        }
        return v;
    }

    private DishViewAdminVO toVO(DishFilterView entity) {
        DishViewAdminVO vo = new DishViewAdminVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setCategoryId(entity.getCategoryId());
        vo.setEnabled(entity.getEnabled());
        vo.setMatchedCount(viewCatalog.matchedCount(entity));
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
