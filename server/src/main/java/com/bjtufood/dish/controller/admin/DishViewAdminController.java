package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.dto.SortItemsReq;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewUpdateReq;
import com.bjtufood.dish.service.DishViewAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A6 首页筛选视图管理（管理端）。
 *
 * <p>契约真源：docs/api/web/views.md 的「视图」端点表（三个端点）。
 *
 * <p>后台只维护 tab 的**文案 / 顺序 / 显隐**；视图的筛选条件与排序口径由 seed / 代码固定
 * （端上只认 `key` / `label`，筛选语义全在服务端）。
 */
@Tag(name = "05. 后台首页筛选视图管理", description = "管理员维护首页筛选栏 tab 的文案 / 顺序 / 启停。"
        + "视图的筛选条件与排序口径由 seed / 代码固定，后台不可配。需要管理员 token。")
@RestController
@RequestMapping("/admin/dish-views")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class DishViewAdminController {

    private final DishViewAdminService service;

    @Operation(summary = "视图列表", description = "用途：视图管理页（按 order 升序，不分页）；带 matchedCount（当前匹配的在售菜品数）。")
    @GetMapping
    public Result<List<DishViewAdminVO>> list() {
        return Result.success(service.listAll());
    }

    @Operation(summary = "视图排序", description = "用途：拖拽后**整体提交全量行**（`{ items: [{ id, order }] }`）；缺行 / 重复 → 400。")
    @PutMapping("/sort")
    public Result<Void> sort(@Valid @RequestBody SortItemsReq req) {
        service.sort(req.getItems());
        return Result.success();
    }

    @Operation(summary = "修改视图", description = "用途：改**文案 / 启停**。停用「最后一个启用的视图」→ 400；不存在 → 4001。")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "视图ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody DishViewUpdateReq req) {
        service.update(id, req);
        return Result.success();
    }
}
