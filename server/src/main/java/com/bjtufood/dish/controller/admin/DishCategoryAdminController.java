package com.bjtufood.dish.controller.admin;

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
 * <p>契约真源：docs/api/web/categories.md 的「种类」端点表。
 *
 * <p>字典真源是独立表 `dish_category`：菜品「种类」与菜品 **1:1**（互斥、必填、单值列 + 索引），
 * 与描述属性（m:n，走 `/admin/dish-dimensions`）是两个独立实体。
 * 数据锚在 `id`（`dish.category_id` 存的就是它）⇒ **改名免费**、零菜品迁移。
 */
@Tag(name = "07. 后台分类（菜品种类）管理", description = "管理员维护菜品种类字典（`dish_category`，`dish.category_id` 的取值域）："
        + "列表 / 登记 / 重命名。数据锚在种类 ID，改名免费。需要管理员 token。")
@RestController
@RequestMapping("/admin/dish-categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class DishCategoryAdminController {

    private final DishCategoryAdminService service;

    @Operation(summary = "分类列表", description = "用途：分类（种类）维护入口（按 id 升序，不分页）；带 dishCount（引用该种类的菜品数）。")
    @GetMapping
    public Result<List<DishCategoryAdminVO>> list() {
        return Result.success(service.listAll());
    }

    @Operation(summary = "登记分类", description = "用途：登记新种类。种类的 ID 由后端生成；名 1~32 字、全表唯一。")
    @PostMapping
    public Result<DishCategoryAdminVO> create(@Valid @RequestBody DishCategorySaveReq req) {
        return Result.success(service.create(req.getName()));
    }

    @Operation(summary = "重命名分类", description = "用途：**只改 name**（改名免费，零菜品迁移）；重名 400；不存在 4001。")
    @PutMapping("/{id}")
    public Result<Void> rename(
            @Parameter(description = "种类ID", example = "3") @PathVariable Long id,
            @Valid @RequestBody DishCategoryRenameReq req) {
        service.rename(id, req.getName());
        return Result.success();
    }
}
