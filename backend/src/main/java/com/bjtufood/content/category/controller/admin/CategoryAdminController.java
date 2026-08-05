package com.bjtufood.content.category.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.result.Result;
import com.bjtufood.content.category.entity.Category;
import com.bjtufood.content.category.mapper.CategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台菜品分类管理（find 宫格维护）
 * <p>
 * Web 端管理 find 宫格分类的增删改 / 启停 / 排序，小程序 find 页即时反映。
 */
@Tag(name = "16. 后台分类管理", description = "维护 find 宫格菜品分类（增删改/启停/排序）。需要管理员 token。")
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CategoryAdminController {

    private final CategoryMapper categoryMapper;

    @Operation(summary = "分类列表", description = "用途：返回全部分类（含禁用），按 sort_order 升序。")
    @GetMapping
    public Result<List<Category>> list() {
        return Result.success(categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder)));
    }

    @Operation(summary = "新增分类", description = "用途：新增 find 宫格分类。")
    @AuditLog(action = OperationLogConst.ACTION_CATEGORY_CREATE, targetType = "category", targetId = "#result")
    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("分类名称不能为空");
        }
        Category c = new Category();
        c.setName(name);
        c.setSortOrder(body.get("sortOrder") == null ? 0 : Integer.parseInt(String.valueOf(body.get("sortOrder"))));
        c.setStatus(body.get("status") == null ? "enabled" : String.valueOf(body.get("status")));
        categoryMapper.insert(c);
        return Result.success(c.getId());
    }

    @Operation(summary = "编辑分类", description = "用途：修改分类名称 / 排序。")
    @AuditLog(action = OperationLogConst.ACTION_CATEGORY_UPDATE, targetType = "category", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "分类ID", example = "1")
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("分类不存在");
        }
        if (body.containsKey("name")) {
            String name = String.valueOf(body.get("name")).trim();
            if (!StringUtils.hasText(name)) {
                throw new BusinessException("分类名称不能为空");
            }
            c.setName(name);
        }
        if (body.containsKey("sortOrder")) {
            c.setSortOrder(Integer.parseInt(String.valueOf(body.get("sortOrder"))));
        }
        categoryMapper.updateById(c);
        return Result.success();
    }

    @Operation(summary = "启停分类", description = "用途：enabled 显示 / disabled 隐藏。")
    @AuditLog(action = OperationLogConst.ACTION_CATEGORY_TOGGLE, targetType = "category", targetId = "#id")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "分类ID", example = "1")
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
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
        return Result.success();
    }

    @Operation(summary = "删除分类", description = "用途：删除分类（请先确认 find 页不再引用）。")
    @AuditLog(action = OperationLogConst.ACTION_CATEGORY_DELETE, targetType = "category", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "分类ID", example = "1")
            @PathVariable Long id) {
        if (categoryMapper.selectById(id) == null) {
            throw new BusinessException("分类不存在");
        }
        categoryMapper.deleteById(id);
        return Result.success();
    }
}
