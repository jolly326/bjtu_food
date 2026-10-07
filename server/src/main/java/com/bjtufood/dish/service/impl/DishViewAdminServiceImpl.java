package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.dto.SortItem;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.SortReorderUtil;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewUpdateReq;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.service.DishViewAdminService;
import com.bjtufood.dish.service.DishViewCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * A6 视图管理服务实现。
 * <p>
 * 后台只维护 tab 的**文案 / 启停 / 顺序**；写入口（修改 / 排序）在事务提交后调用
 * {@link DishViewCatalog#invalidateViews()} —— 把 `GET /dishes/views` 的 2 分钟 TTL 换成
 * **保存即生效**（契约见 A6 的「客户端可见性规则」）。
 */
@Service
@RequiredArgsConstructor
public class DishViewAdminServiceImpl implements DishViewAdminService {

    private static final int LABEL_MAX = 32;

    private final DishFilterViewMapper viewMapper;
    private final DishViewCatalog viewCatalog;

    @Override
    public List<DishViewAdminVO> listAll() {
        return viewCatalog.all().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DishViewUpdateReq req) {
        DishFilterView current = requireExists(id);
        String label = normalizeLabel(req.getLabel());
        // 「不允许停用最后一个启用的视图」：仅当本次把一行从启用改为停用时才校验（避免下发规则自相矛盾）
        if (Boolean.TRUE.equals(current.getEnabled()) && Boolean.FALSE.equals(req.getEnabled())) {
            long otherEnabled = viewMapper.selectCount(new LambdaQueryWrapper<DishFilterView>()
                    .eq(DishFilterView::getEnabled, true)
                    .ne(DishFilterView::getId, id));
            if (otherEnabled == 0) {
                throw new BusinessException("不允许停用最后一个启用的视图");
            }
        }
        DishFilterView update = new DishFilterView();
        update.setId(id);
        update.setLabel(label);
        update.setEnabled(req.getEnabled());
        viewMapper.updateById(update);
        viewCatalog.invalidateViews();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<SortItem> items) {
        List<Long> existing = viewMapper.selectList(null).stream().map(DishFilterView::getId).toList();
        Map<Long, Integer> ordered = SortReorderUtil.resolve(items, existing);
        for (Map.Entry<Long, Integer> e : ordered.entrySet()) {
            DishFilterView update = new DishFilterView();
            update.setId(e.getKey());
            update.setSortOrder(e.getValue());
            viewMapper.updateById(update);
        }
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

    private static String normalizeLabel(String label) {
        String v = label == null ? null : label.trim();
        if (!StringUtils.hasText(v)) {
            throw new BusinessException("tab 文案不能为空");
        }
        if (v.length() > LABEL_MAX) {
            throw new BusinessException("tab 文案不能超过 " + LABEL_MAX + " 字");
        }
        return v;
    }

    private DishViewAdminVO toVO(DishFilterView entity) {
        DishViewAdminVO vo = new DishViewAdminVO();
        vo.setId(entity.getId());
        vo.setKey(entity.getKey());
        vo.setLabel(entity.getLabel());
        vo.setOrder(entity.getSortOrder());
        vo.setEnabled(entity.getEnabled());
        vo.setMatchedCount(viewCatalog.matchedCount(entity));
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
