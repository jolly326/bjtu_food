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
 * A6 菜品分类值管理（管理端）。
 *
 * <p>契约真源：docs/api/web/categories.md 的「分类值」端点表。
 *
 * <p>分类值由**自由输入自动登记**产生（A3 菜品录入），本控制器是它的**最终落点与维护入口**
 * （列表 / 登记 / 重命名）。数据锚在 `key` ⇒ **改名免费**。
 */
@Tag(name = "07. 后台分类值管理", description = "管理员维护菜品分类值字典（`dish.meal_type` 的取值域）：列表 / 登记 / 重命名。"
        + "自由输入自动登记；改名免费。需要管理员 token。")
@RestController
@RequestMapping("/admin/dish-categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class DishCategoryAdminController {

    private final DishCategoryAdminService service;

    @Operation(summary = "分类值列表", description = "用途：分类值维护入口（按 order 升序，不分页）；带 dishCount（引用该分类的菜品数）。")
    @GetMapping
    public Result<List<DishCategoryAdminVO>> list() {
        return Result.success(service.listAll());
    }

    @Operation(summary = "登记分类值", description = "用途：登记新分类值（A3 菜品保存的「自动登记」最终落到本端点）。"
            + "键 1~20 小写字母/数字/-、全站唯一；名 1~32 字、应用层唯一。")
    @PostMapping
    public Result<DishCategoryAdminVO> create(@Valid @RequestBody DishCategorySaveReq req) {
        return Result.success(service.create(req.getKey(), req.getLabel()));
    }

    @Operation(summary = "重命名分类值", description = "用途：**只改 label**（改名免费，零菜品迁移）；重名 400；不存在 4001。")
    @PutMapping("/{id}")
    public Result<Void> rename(
            @Parameter(description = "分类值ID", example = "3") @PathVariable Long id,
            @Valid @RequestBody DishCategoryRenameReq req) {
        service.rename(id, req.getLabel());
        return Result.success();
    }
}
