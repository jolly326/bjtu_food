package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.common.dto.SortItem;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.SortReorderUtil;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.dto.DishViewPreviewVO;
import com.bjtufood.dish.dto.DishViewSaveReq;
import com.bjtufood.dish.entity.DishFilterView;
import com.bjtufood.dish.mapper.DishFilterViewMapper;
import com.bjtufood.dish.service.DishViewAdminService;
import com.bjtufood.dish.service.DishViewCatalog;
import com.bjtufood.dish.view.DishViewConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * A6 视图管理服务实现。
 * <p>
 * 全部写入口（新建 / 修改 / 删除 / 排序 / 设默认）在事务提交后调用
 * {@link DishViewCatalog#invalidateViews()} —— 把 `GET /dishes/views` 的 2 分钟 TTL 换成
 * **保存即生效**（契约见 A6 的「客户端可见性规则」）。
 */
@Service
@RequiredArgsConstructor
public class DishViewAdminServiceImpl implements DishViewAdminService {

    /** 预览抽样条数：够管理员判断条件对不对，又不至于把弹层撑满 */
    private static final int PREVIEW_SAMPLE_SIZE = 10;
    private static final int LABEL_MAX = 32;

    private final DishFilterViewMapper viewMapper;
    private final DishViewCatalog viewCatalog;

    @Override
    public List<DishViewAdminVO> listAll() {
        return viewCatalog.all().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishViewAdminVO create(DishViewSaveReq req) {
        String key = normalizeKey(req.getKey());
        String label = normalizeLabel(req.getLabel());
        List<DishViewCondition> conditions = DishViewConditions.validate(req.getConditions());
        String sortKind = normalizeSortKind(req.getSortKind());
        if (viewMapper.selectCount(new LambdaQueryWrapper<DishFilterView>()
                .eq(DishFilterView::getKey, key)) > 0) {
            throw new BusinessException("视图键已存在");
        }
        DishFilterView entity = new DishFilterView();
        entity.setKey(key);
        entity.setLabel(label);
        entity.setConditions(DishViewConditions.toJson(conditions));
        entity.setSortKind(sortKind);
        entity.setEnabled(req.getEnabled());
        // 新增恒为非默认：设默认必须显式走 /{id}/default（保证「全站恰一个」不被并发新建破坏）
        entity.setIsDefault(false);
        entity.setOrder(nextOrder());
        viewMapper.insert(entity);
        viewCatalog.invalidateViews();
        return toVO(viewMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DishViewSaveReq req) {
        DishFilterView current = requireExists(id);
        // key 在用后不可改：入参带了不同的键即 400（不静默忽略，避免「以为改了其实没改」）
        if (StringUtils.hasText(req.getKey()) && !current.getKey().equals(req.getKey().trim())) {
            throw new BusinessException("视图键在用后不可修改");
        }
        String label = normalizeLabel(req.getLabel());
        List<DishViewCondition> conditions = DishViewConditions.validate(req.getConditions());
        String sortKind = normalizeSortKind(req.getSortKind());
        if (Boolean.TRUE.equals(current.getIsDefault()) && !Boolean.TRUE.equals(req.getEnabled())) {
            // 与「默认视图恒下发」「默认视图不可删除」同口径：下发规则不能自相矛盾
            throw new BusinessException("默认视图不可停用");
        }
        DishFilterView update = new DishFilterView();
        update.setId(id);
        update.setLabel(label);
        update.setConditions(DishViewConditions.toJson(conditions));
        update.setSortKind(sortKind);
        update.setEnabled(req.getEnabled());
        viewMapper.updateById(update);
        viewCatalog.invalidateViews();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DishFilterView current = requireExists(id);
        if (Boolean.TRUE.equals(current.getIsDefault())) {
            throw new BusinessException("默认视图不可删除（请先切换默认视图）");
        }
        viewMapper.deleteById(id);
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
            update.setOrder(e.getValue());
            viewMapper.updateById(update);
        }
        viewCatalog.invalidateViews();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        requireExists(id);
        // 先清旧默认再置新默认：`is_default` 全站恰一个由应用层保证（不加唯一索引）
        viewMapper.update(null, new LambdaUpdateWrapper<DishFilterView>()
                .set(DishFilterView::getIsDefault, false)
                .eq(DishFilterView::getIsDefault, true));
        DishFilterView update = new DishFilterView();
        update.setId(id);
        update.setIsDefault(true);
        viewMapper.updateById(update);
        viewCatalog.invalidateViews();
    }

    @Override
    public DishViewPreviewVO preview(List<DishViewCondition> conditions) {
        List<DishViewCondition> validated = DishViewConditions.validate(conditions);
        DishViewPreviewVO vo = new DishViewPreviewVO();
        vo.setMatchedCount(viewCatalog.matchedCount(validated));
        vo.setSampleNames(viewCatalog.sampleNames(validated, PREVIEW_SAMPLE_SIZE));
        return vo;
    }

    // ==================== 内部工具 ====================

    private DishFilterView requireExists(Long id) {
        DishFilterView view = id == null ? null : viewMapper.selectById(id);
        if (view == null) {
            throw new BusinessException(4001, "视图不存在");
        }
        return view;
    }

    private int nextOrder() {
        List<DishFilterView> all = viewMapper.selectList(new LambdaQueryWrapper<DishFilterView>()
                .orderByDesc(DishFilterView::getOrder));
        return all.isEmpty() || all.get(0).getOrder() == null ? 1 : all.get(0).getOrder() + 1;
    }

    private static String normalizeKey(String key) {
        String v = key == null ? null : key.trim();
        if (v == null || !v.matches("^[a-z0-9-]{1,32}$")) {
            throw new BusinessException("视图键只能包含小写字母、数字与 -，长度 1~32");
        }
        return v;
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

    private static String normalizeSortKind(String sortKind) {
        String v = sortKind == null ? null : sortKind.trim();
        if (v == null || !DishViewConditions.SORT_KINDS.contains(v)) {
            throw new BusinessException("排序口径不在白名单：" + sortKind);
        }
        return v;
    }

    private DishViewAdminVO toVO(DishFilterView entity) {
        DishViewAdminVO vo = new DishViewAdminVO();
        vo.setId(entity.getId());
        vo.setKey(entity.getKey());
        vo.setLabel(entity.getLabel());
        vo.setOrder(entity.getOrder());
        vo.setEnabled(entity.getEnabled());
        vo.setIsDefault(entity.getIsDefault());
        vo.setConditions(DishViewConditions.parse(entity.getConditions()));
        vo.setSortKind(entity.getSortKind());
        vo.setMatchedCount(viewCatalog.matchedCount(entity));
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
