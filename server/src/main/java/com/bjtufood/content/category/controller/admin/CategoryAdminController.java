package com.bjtufood.content.category.controller.admin;

import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.Result;
import com.bjtufood.content.category.entity.Category;
import com.bjtufood.content.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台菜品品类管理（首页品类滚轮维护）
 * <p>
 * Web 端管理品类（code 机器标识 + 名称 + 排序）的增删改 / 启停 / 排序，小程序首页品类滚轮即时反映。
 * <p>
 * P3/ARCH-008：增删改启停与业务校验下沉 CategoryService（行为零变化），
 * Controller 只留参数与响应包装；@AuditLog 埋点注解保留在 Controller。
 * 入参沿用原 Map 裸参契约（update 的 containsKey 部分更新语义）。
 */
@Tag(name = "16. 后台品类管理", description = "维护首页品类滚轮菜品品类（增删改/启停/排序）。需要管理员 token。")
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CategoryAdminController {

    private final CategoryService categoryService;

    @Operation(summary = "品类列表", description = "用途：返回全部分类（含禁用），按 sort_order 升序。")
    @GetMapping
    public Result<List<Category>> list() {
        return Result.success(categoryService.listAll());
    }

    @Operation(summary = "新增品类", description = "用途：新增首页品类滚轮品类，code 为唯一机器标识。")
    @AuditLog(action = OperationLogConst.ACTION_CATEGORY_CREATE, targetType = "category", targetId = "#result")
    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        return Result.success(categoryService.create(body));
    }

    @Operation(summary = "编辑品类", description = "用途：修改品类名称 / code / 排序。")
    @AuditLog(action = OperationLogConst.ACTION_CATEGORY_UPDATE, targetType = "category", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "分类ID", example = "1")
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        categoryService.update(id, body);
        return Result.success();
    }

    @Operation(summary = "启停品类", description = "用途：enabled 显示 / disabled 隐藏。")
    @AuditLog(action = OperationLogConst.ACTION_CATEGORY_TOGGLE, targetType = "category", targetId = "#id")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "分类ID", example = "1")
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        categoryService.updateStatus(id, body);
        return Result.success();
    }

    @Operation(summary = "删除品类", description = "用途：删除品类（请先确认不再被菜品引用）。")
    @AuditLog(action = OperationLogConst.ACTION_CATEGORY_DELETE, targetType = "category", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "分类ID", example = "1")
            @PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
