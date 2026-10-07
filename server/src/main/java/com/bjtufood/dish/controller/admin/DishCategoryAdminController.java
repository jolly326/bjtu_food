package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.dto.SortItemsReq;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishCategoryAdminVO;
import com.bjtufood.dish.dto.DishCategoryRenameReq;
import com.bjtufood.dish.dto.DishCategorySaveReq;
import com.bjtufood.dish.service.DishCategoryAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A6 菜品种类字典管理（管理端）。
 *
 * <p>契约真源：docs/api/web/categories.md 的「种类取值」端点表。
 *
 * <p>本路径是**系统维度（菜品种类）取值的别名面** —— 同一批行也可经
 * `/admin/dish-dimensions/{dimensionId}/values` 读写；数据锚在取值 `id`
 * （`dish.meal_type_id` 存的就是它）⇒ **改名免费**。
 */
@Tag(name = "07. 后台分类（菜品种类）管理", description = "管理员维护菜品种类字典（系统维度取值的别名面，`dish.meal_type_id` 的取值域）："
        + "列表 / 登记 / 重命名 / 排序。数据锚在取值 ID，改名免费。需要管理员 token。")
@RestController
@RequestMapping("/admin/dish-categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class DishCategoryAdminController {

    private final DishCategoryAdminService service;

    @Operation(summary = "分类值列表", description = "用途：分类（种类）维护入口（按 order 升序，不分页）；带 dishCount（引用该种类的菜品数）。")
    @GetMapping
    public Result<List<DishCategoryAdminVO>> list() {
        return Result.success(service.listAll());
    }

    @Operation(summary = "登记分类值", description = "用途：登记新种类取值。取值的 ID 由后端生成、顺序由拖拽维护；名 1~32 字、同维度下唯一。")
    @PostMapping
    public Result<DishCategoryAdminVO> create(@Valid @RequestBody DishCategorySaveReq req) {
        return Result.success(service.create(req.getLabel()));
    }

    @Operation(summary = "分类值排序", description = "用途：拖拽后**整体提交全量行**（`{ items: [{ id, order }] }`）；缺行 / 重复 → 400。")
    @PutMapping("/sort")
    public Result<Void> sort(@Valid @RequestBody SortItemsReq req) {
        service.sort(req.getItems());
        return Result.success();
    }

    @Operation(summary = "重命名分类值", description = "用途：**只改 label**（改名免费，零菜品迁移）；重名 400；不存在 4001。")
    @PutMapping("/{id}")
    public Result<Void> rename(
            @Parameter(description = "取值ID", example = "3") @PathVariable Long id,
            @Valid @RequestBody DishCategoryRenameReq req) {
        service.rename(id, req.getLabel());
        return Result.success();
    }
}
