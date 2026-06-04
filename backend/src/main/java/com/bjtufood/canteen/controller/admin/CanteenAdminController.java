package com.bjtufood.canteen.controller.admin;

import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "08. 后台食堂档口管理", description = "系统管理员维护食堂和档口基础数据。需要管理员 token。")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CanteenAdminController {

    private final CanteenService canteenService;
    private final StallService stallService;

    @Operation(summary = "后台食堂列表", description = "用途：浏览器管理端查看全部食堂，包含 open/closed 状态。images 返回可访问的完整 URL 数组。")
    @GetMapping("/canteens")
    public Result<?> listCanteens() {
        return Result.success(canteenService.listAllForAdmin());
    }

    @Operation(
            summary = "新增食堂",
            description = "用途：创建新的物理食堂/餐厅。images 字段传 JSON 字符串，例如 [\"/images/a.jpg\"]。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "name": "测试食堂",
                      "images": "[\\"/images/seed/canteens/canteen-dining-hall.jpg\\"]",
                      "location": "主校区",
                      "description": "Knife4j 测试新增食堂",
                      "sortOrder": 99,
                      "status": "open"
                    }
                    """)))
    )
    @PostMapping("/canteens")
    public Result<Void> addCanteen(@Valid @RequestBody Canteen canteen) {
        canteenService.add(canteen);
        return Result.success();
    }

    @Operation(summary = "编辑食堂", description = "用途：修改食堂名称、图片、位置、描述、排序、状态。")
    @PutMapping("/canteens/{id}")
    public Result<Void> updateCanteen(
            @Parameter(description = "食堂ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody Canteen canteen) {
        canteen.setId(id);
        canteenService.update(canteen);
        return Result.success();
    }

    @Operation(summary = "删除食堂", description = "用途：删除食堂。若食堂下仍有档口，Service 会阻止删除。")
    @DeleteMapping("/canteens/{id}")
    public Result<Void> deleteCanteen(
            @Parameter(description = "食堂ID", example = "99")
            @PathVariable Long id) {
        canteenService.delete(id);
        return Result.success();
    }

    @Operation(
            summary = "新增档口",
            description = "用途：在指定食堂下创建档口。images 字段传 JSON 字符串。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "canteenId": 1,
                      "name": "测试档口",
                      "images": "[\\"/images/seed/canteens/canteen-food-counter.jpg\\"]",
                      "location": "一层",
                      "description": "Knife4j 测试新增档口",
                      "avgRating": 0,
                      "sortOrder": 99,
                      "status": "open"
                    }
                    """)))
    )
    @PostMapping("/stalls")
    public Result<Void> addStall(@Valid @RequestBody Stall stall) {
        stallService.add(stall);
        return Result.success();
    }

    @Operation(summary = "后台档口列表", description = "用途：浏览器管理端查看全部档口，包含 open/closed 状态。images 返回可访问的完整 URL 数组。")
    @GetMapping("/stalls")
    public Result<?> listStalls() {
        return Result.success(stallService.listAllForAdmin());
    }

    @Operation(summary = "编辑档口", description = "用途：修改档口基础信息。")
    @PutMapping("/stalls/{id}")
    public Result<Void> updateStall(
            @Parameter(description = "档口ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody Stall stall) {
        stall.setId(id);
        stallService.update(stall);
        return Result.success();
    }

    @Operation(summary = "删除档口", description = "用途：删除档口。若档口下仍有菜品，Service 会阻止删除。")
    @DeleteMapping("/stalls/{id}")
    public Result<Void> deleteStall(
            @Parameter(description = "档口ID", example = "99")
            @PathVariable Long id) {
        stallService.delete(id);
        return Result.success();
    }
}
