package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewCreateReq;
import com.bjtufood.dish.dto.DishViewUpdateReq;
import com.bjtufood.dish.service.DishViewAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A6 首页筛选视图管理（管理端）。
 *
 * <p>契约真源：docs/api/web/views.md。
 *
 * <p>视图是**纯数据**：文案 / 显隐 / 归属种类全在 `dish_filter_view`，
 * 后台可自助增删改、免发版；端上只认 `id` + `name`，取数口径全在服务端。
 */
@Tag(name = "05. 后台首页筛选视图管理", description = "管理员维护首页筛选栏 tab 的文案 / 启停 / 归属种类。"
        + "取数口径固定为「种类过滤（可空）+ 随机序 + 在售」。需要管理员 token。")
@RestController
@RequestMapping("/admin/dish-views")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class DishViewAdminController {

    private final DishViewAdminService service;

    @Operation(summary = "视图列表", description = "用途：视图管理页（按 id 升序，不分页）；带 matchedCount（当前匹配的在售菜品数）。")
    @GetMapping
    public Result<List<DishViewAdminVO>> list() {
        return Result.success(service.listAll());
    }

    @Operation(summary = "新建视图", description = "用途：新建 tab（默认启用）。categoryId 缺省 = 不按种类过滤；种类不存在 → 400。")
    @PostMapping
    public Result<DishViewAdminVO> create(@Valid @RequestBody DishViewCreateReq req) {
        return Result.success(service.create(req));
    }

    @Operation(summary = "修改视图", description = "用途：改**文案 / 启停 / 归属种类**（整体替换）。停用「最后一个启用的视图」→ 400；不存在 → 4001。")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "视图ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody DishViewUpdateReq req) {
        service.update(id, req);
        return Result.success();
    }

    @Operation(summary = "删除视图", description = "用途：删除 tab（从下发集合移除）。删除「最后一个启用的视图」→ 400；不存在 → 4001。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "视图ID", example = "1") @PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
