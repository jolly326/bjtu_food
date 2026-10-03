package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.dto.SortItemsReq;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewPreviewVO;
import com.bjtufood.dish.dto.DishViewSaveReq;
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
 * <p>契约真源：docs/web/A-主数据维护/A6-首页筛选视图管理.md 的「视图」端点表（7 个）。
 *
 * <p>视图 = **筛选条件（字段白名单）+ 排序**：新增一个 tab 免发版、免客户端改动
 * （端上只认 `key` / `label`，筛选语义全在服务端）。
 */
@Tag(name = "05. 后台首页筛选视图管理", description = "管理员维护首页筛选栏的 tab：文案 / 顺序 / 启停 / 默认项 / **筛选标准** / 排序。"
        + "条件字段与操作符走白名单（不可配 SQL）；默认视图不可删除、不可停用。需要管理员 token。")
@RestController
@RequestMapping("/admin/dish-views")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class DishViewAdminController {

    private final DishViewAdminService service;

    @Operation(summary = "视图列表", description = "用途：视图管理页（按 order 升序，不分页）；带 conditions（原样回显）与 matchedCount（当前匹配的在售菜品数）。")
    @GetMapping
    public Result<List<DishViewAdminVO>> list() {
        return Result.success(service.listAll());
    }

    @Operation(summary = "新建视图", description = "用途：新建 tab（恒为**非默认**，order 默认排最后）。键全站唯一；条件字段/操作符须在白名单内。")
    @PostMapping
    public Result<DishViewAdminVO> create(@Valid @RequestBody DishViewSaveReq req) {
        return Result.success(service.create(req));
    }

    @Operation(summary = "视图排序", description = "用途：拖拽后**整体提交全量行**（`{ items: [{ id, order }] }`）；缺行 / 重复 → 400。")
    @PutMapping("/sort")
    public Result<Void> sort(@Valid @RequestBody SortItemsReq req) {
        service.sort(req.getItems());
        return Result.success();
    }

    @Operation(summary = "预览匹配数", description = "用途：保存前试算（**不入库**）。只传 conditions；返回 matchedCount + 抽样菜名。")
    @PostMapping("/preview")
    public Result<DishViewPreviewVO> preview(@Valid @RequestBody DishViewSaveReq req) {
        return Result.success(service.preview(req.getConditions()));
    }

    @Operation(summary = "设为默认", description = "用途：设为默认视图（**自动取消原默认**；默认视图恒下发、不可删除、不可停用）。")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(
            @Parameter(description = "视图ID", example = "1") @PathVariable Long id) {
        service.setDefault(id);
        return Result.success();
    }

    @Operation(summary = "修改视图", description = "用途：改文案 / 条件 / 排序 / 启停（**key 不可改**）。停用默认视图 → 400；不存在 → 4001。")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "视图ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody DishViewSaveReq req) {
        service.update(id, req);
        return Result.success();
    }

    @Operation(summary = "删除视图", description = "用途：删除 tab。**默认视图 → 400**（请先切换默认）；不存在 → 4001。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "视图ID", example = "1") @PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
