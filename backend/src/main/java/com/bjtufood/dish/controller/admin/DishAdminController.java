package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 菜品管理控制器（食堂管理员专用）
 * <p>
 * 食堂管理员对自己档口的菜品进行增删改和管理。
 * 通过当前登录用户的 stallId 确定管辖范围。
 */
@Tag(name = "菜品管理（食堂管理员）", description = "菜品的新增、编辑、上下架、删除")
@RestController
@RequestMapping("/admin/dishes")
@RequiredArgsConstructor
public class DishAdminController {

    private final DishService dishService;

    @Operation(summary = "我的菜品列表", description = "查询当前管理员所属档口的菜品列表（含已下架）")
    @GetMapping
    public Result<?> listMyDishes() {
        // TODO: 从 SecurityContext 获取当前用户的 stallId
        // 调用 DishService.listByStallId(stallId)
        return Result.success("菜品列表");
    }

    @Operation(summary = "新增菜品", description = "在当前档口下创建新菜品")
    @PostMapping
    public Result<Void> addDish(@Valid @RequestBody DishAdminReq req) {
        // TODO: 获取当前用户的 stallId，调用 DishService.addDish(stallId, req)
        return Result.success();
    }

    @Operation(summary = "编辑菜品", description = "修改菜品信息（名称、价格、描述、图片、标签等）")
    @PutMapping("/{id}")
    public Result<Void> updateDish(@PathVariable Long id, @Valid @RequestBody DishAdminReq req) {
        // TODO: 调用 DishService.updateDish(id, req)
        return Result.success();
    }

    @Operation(summary = "删除菜品", description = "逻辑删除菜品（软删除）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDish(@PathVariable Long id) {
        // TODO: 调用 DishService.deleteDish(id)
        return Result.success();
    }
}
