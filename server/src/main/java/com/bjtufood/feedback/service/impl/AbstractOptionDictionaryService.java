package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
import com.bjtufood.feedback.constant.OptionConst;
import com.bjtufood.feedback.dto.OptionAdminVO;
import com.bjtufood.feedback.dto.OptionVO;
import com.bjtufood.feedback.entity.OptionDictionaryItem;
import com.bjtufood.feedback.service.OptionDictionaryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 分类字典服务**共性实现**（`feedback_option` / `report_option` 两张同族字典共用）。
 * <p>
 * <b>为什么抽基类而不是各写一份</b>：两张字典的规则完全相同（ID 即数据锚点、改名免费、
 * 删除受引用约束、至少 1 条启用、启用 ≤8），差异只有两点：<b>消费表</b>
 * （反馈 / 举报，由 {@link #countUsage(Long)} 钩子表达）与<b>表名 / 实体</b>
 * （由构造参数注入）。把规则写两份，等于给「两张字典口径漂移」留后门 ——
 * 一处改了上限、另一处忘了，界面上看不出任何异常。
 * <p>
 * 列名以字符串给出（`id` / `name` / `status`）：两张字典表结构已冻结（`id` / `name` /
 * `status` / `updated_at`），且本基类需要跨实体类型构造条件，字符串列名是唯一不引入
 * 反射/泛型体操的形态。
 */
abstract class AbstractOptionDictionaryService<T extends OptionDictionaryItem>
        implements OptionDictionaryService {

    /** 选项 ID 列 */
    private static final String COL_ID = "id";
    /** 中文名列 */
    private static final String COL_NAME = "name";
    /** 状态列 */
    private static final String COL_STATUS = "status";

    private final BaseMapper<T> optionMapper;
    private final Supplier<T> factory;
    /** 消费表主语（删除拒绝提示用），如「反馈」「举报」 */
    private final String usageSubject;

    protected AbstractOptionDictionaryService(BaseMapper<T> optionMapper, Supplier<T> factory, String usageSubject) {
        this.optionMapper = optionMapper;
        this.factory = factory;
        this.usageSubject = usageSubject;
    }

    /**
     * 本字典被工单引用的次数（由具体字典指向自己的消费表：反馈表 / 举报表）。
     * <p>同域内跨表统计，不越域。
     */
    protected abstract long countUsage(Long optionId);

    @Override
    public List<OptionVO> listEnabled() {
        return optionMapper.selectList(new QueryWrapper<T>()
                        .eq(COL_STATUS, OptionConst.STATUS_ON)
                        .orderByAsc(COL_ID))
                .stream()
                .map(item -> new OptionVO(item.getId(), item.getName()))
                .toList();
    }

    @Override
    public List<OptionAdminVO> listAllForAdmin() {
        return optionMapper.selectList(new QueryWrapper<T>().orderByAsc(COL_ID))
                .stream()
                .map(this::toAdminVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OptionAdminVO create(String name) {
        String normalizedName = normalizeName(name);
        // 名唯一由应用层保证（name 无唯一索引，与 dish_attribute_value 同口径）
        DuplicateGuard.assertUnique(optionMapper,
                new QueryWrapper<T>().eq(COL_NAME, normalizedName), "中文名已存在");
        // 新增默认启用 ⇒ 启用数将达到 count+1，超上限即拒
        if (countEnabled() >= OptionConst.MAX_ENABLED) {
            throw new BusinessException("启用数已达上限 " + OptionConst.MAX_ENABLED + " 条，请先停用其它选项");
        }
        T entity = factory.get();
        entity.setName(normalizedName);
        entity.setStatus(OptionConst.STATUS_ON);
        optionMapper.insert(entity);
        // 回读以取 DB 时钟写入的 updated_at（应用层不写、不填充）
        return toAdminVO(optionMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String name) {
        String normalizedName = normalizeName(name);
        requireExists(id);
        T patch = factory.get();
        patch.setId(id);
        patch.setName(normalizedName);
        optionMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        String normalizedStatus = normalizeStatus(status);
        T current = optionMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(4001, "选项不存在");
        }
        if (OptionConst.STATUS_ON.equals(normalizedStatus)) {
            if (countEnabled() >= OptionConst.MAX_ENABLED) {
                throw new BusinessException("启用数已达上限 " + OptionConst.MAX_ENABLED + " 条");
            }
        } else if (OptionConst.STATUS_ON.equals(current.getStatus())
                && countEnabled() <= OptionConst.MIN_ENABLED) {
            // 提交入口不能被配空：停用最后一条启用项即等于让用户无法提交
            throw new BusinessException("至少保留 " + OptionConst.MIN_ENABLED + " 条启用中的选项");
        }
        if (normalizedStatus.equals(current.getStatus())) {
            return;
        }
        T patch = factory.get();
        patch.setId(id);
        patch.setStatus(normalizedStatus);
        optionMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        T current = optionMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(4001, "选项不存在");
        }
        long used = countUsage(id);
        if (used > 0) {
            // 删掉会让历史工单翻不出中文 ⇒ 下线一律用停用
            throw new BusinessException("该选项已被 " + used + " 条" + usageSubject + "引用，不能删除（可改为停用）");
        }
        optionMapper.deleteById(id);
    }

    @Override
    public boolean isSubmittable(Long optionId) {
        if (optionId == null) {
            return false;
        }
        return optionMapper.selectCount(new QueryWrapper<T>()
                .eq(COL_ID, optionId)
                .eq(COL_STATUS, OptionConst.STATUS_ON)) > 0;
    }

    @Override
    public Map<Long, String> nameByIdAll() {
        Map<Long, String> names = new HashMap<>();
        for (T item : optionMapper.selectList(null)) {
            names.put(item.getId(), item.getName());
        }
        return names;
    }

    // ==================== 内部工具 ====================

    private long countEnabled() {
        return optionMapper.selectCount(new QueryWrapper<T>().eq(COL_STATUS, OptionConst.STATUS_ON));
    }

    private void requireExists(Long id) {
        if (id == null || optionMapper.selectById(id) == null) {
            throw new BusinessException(4001, "选项不存在");
        }
    }

    private OptionAdminVO toAdminVO(T entity) {
        OptionAdminVO vo = new OptionAdminVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setStatus(entity.getStatus());
        vo.setUsageCount(countUsage(entity.getId()));
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? null : name.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("中文名不能为空");
        }
        if (normalized.length() > OptionConst.NAME_MAX_LENGTH) {
            throw new BusinessException("中文名不能超过 " + OptionConst.NAME_MAX_LENGTH + " 字");
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status) || !OptionConst.STATUSES.contains(status.trim().toLowerCase())) {
            throw new BusinessException("status 非法");
        }
        return status.trim().toLowerCase();
    }
}
