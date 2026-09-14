package com.bjtufood.canteen.controller.admin;

import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台食堂/档口字典接口（2026-09-15 蓝图 v1，project_spec §7.23 第 1 条）：
 * 食堂/档口是菜品的属性，不独立建档——独立新增端点 {@code POST /admin/canteens}、{@code POST /admin/stalls}
 * 已删除；字典的写入入口收敛为「菜品录入按名 upsert」（DishServiceImpl，同名不重复建档）。
 * 本 Controller 仅保留只读列表与改名（编辑）能力。
 */
@Tag(name = "08. 后台食堂档口管理", description = "管理员维护食堂/档口筛选属性字典。生命周期仅「改名（编辑）＋列表查询」——独立新增端点已下线，"
        + "新食堂/档口由菜品录入按名 upsert 自动建档（POST/PUT /admin/dishes 传 canteenName/stallName）。无删除。需要管理员 token。")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CanteenAdminController {

    private final CanteenService canteenService;
    private final StallService stallService;

    @Operation(summary = "后台食堂列表", description = "用途：浏览器管理端查看全部食堂（筛选属性字典）。images 返回可访问的完整 URL 数组。")
    @GetMapping("/canteens")
    public Result<?> listCanteens() {
        return Result.success(canteenService.listAllForAdmin());
    }

    @Operation(summary = "编辑食堂", description = "用途：修改食堂名称、图片、位置、描述、排序。"
            + "新增食堂不再开放独立端点——由菜品录入按名 upsert 自动建档。")
    @PutMapping("/canteens/{id}")
    public Result<Void> updateCanteen(
            @Parameter(description = "食堂ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody Canteen canteen) {
        canteen.setId(id);
        canteenService.update(canteen);
        return Result.success();
    }

    @Operation(summary = "后台档口列表", description = "用途：浏览器管理端查看全部档口（筛选属性字典）。images 返回可访问的完整 URL 数组。")
    @GetMapping("/stalls")
    public Result<?> listStalls() {
        return Result.success(stallService.listAllForAdmin());
    }

    @Operation(summary = "编辑档口", description = "用途：修改档口基础信息。"
            + "新增档口不再开放独立端点——由菜品录入按名 upsert 自动建档。")
    @PutMapping("/stalls/{id}")
    public Result<Void> updateStall(
            @Parameter(description = "档口ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody Stall stall) {
        stall.setId(id);
        stallService.update(stall);
        return Result.success();
    }
}
