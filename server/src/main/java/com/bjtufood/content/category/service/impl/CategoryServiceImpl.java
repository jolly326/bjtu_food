package com.bjtufood.content.category.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.content.category.entity.Category;
import com.bjtufood.content.category.mapper.CategoryMapper;
import com.bjtufood.content.category.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 菜品品类后台管理服务实现
 * <p>
 * P3/ARCH-008：增删改启停与业务校验自 CategoryAdminController 原样迁移
 * （错误文案/校验规则/默认值逐一保持，行为零变化）。
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> listAll() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder));
    }

    @Override
    public List<Category> listEnabled() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, "enabled")
                        .orderByAsc(Category::getSortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("code", "")).trim();
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("品类 code 不能为空");
        }
        if (!code.matches("[a-z][a-z0-9_]{1,30}")) {
            throw new BusinessException("品类 code 需为小写字母/数字/下划线组合");
        }
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("品类名称不能为空");
        }
        checkCodeUnique(code, null);
        Category c = new Category();
        c.setCode(code);
        c.setName(name);
        c.setSortOrder(parseSortOrder(body.get("sortOrder")));
        c.setStatus(body.get("status") == null ? "enabled" : String.valueOf(body.get("status")));
        categoryMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Map<String, Object> body) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("分类不存在");
        }
        if (body.containsKey("code")) {
            String code = String.valueOf(body.get("code")).trim();
            if (!StringUtils.hasText(code)) {
                throw new BusinessException("品类 code 不能为空");
            }
            if (!code.matches("[a-z][a-z0-9_]{1,30}")) {
                throw new BusinessException("品类 code 需为小写字母/数字/下划线组合");
            }
            checkCodeUnique(code, id);
            c.setCode(code);
        }
        if (body.containsKey("name")) {
            String name = String.valueOf(body.get("name")).trim();
            if (!StringUtils.hasText(name)) {
                throw new BusinessException("分类名称不能为空");
            }
            c.setName(name);
        }
        if (body.containsKey("sortOrder")) {
            c.setSortOrder(parseSortOrder(body.get("sortOrder")));
        }
        categoryMapper.updateById(c);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Map<String, String> body) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("分类不存在");
        }
        String status = body.get("status");
        if (!"enabled".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException("非法的状态：" + status);
        }
        c.setStatus(status);
        categoryMapper.updateById(c);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (categoryMapper.selectById(id) == null) {
            throw new BusinessException("分类不存在");
        }
        categoryMapper.deleteById(id);
    }

    /**
     * code 唯一性校验：excludeId 非空时排除自身（编辑场景）。
     */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getCode, code)
                .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("品类 code 已存在：" + code);
        }
    }

    /**
     * 安全解析排序值：非数字输入返回 400 参数错误，而非抛 NumberFormatException 兜底成 500。
     */
    private int parseSortOrder(Object raw) {
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("排序值必须是整数");
        }
    }
}
