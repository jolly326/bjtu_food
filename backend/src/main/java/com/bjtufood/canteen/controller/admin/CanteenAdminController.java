package com.bjtufood.canteen.controller.admin;

import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 食堂档口管理控制器（系统管理员专用）
 * <p>
 * 食堂和档口的增删改操作。
 * 删除食堂时需确保其下无档口，删除档口时需确保其下无菜品。
 */
@Tag(name = "食堂档口管理（系统管理员）", description = "食堂和档口的增删改操作")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class CanteenAdminController {

    private final CanteenService canteenService;
    private final StallService stallService;

    // ==================== 食堂管理 ====================

    @Operation(summary = "新增食堂", description = "创建新的食堂")
    @PostMapping("/canteens")
    public Result<Void> addCanteen(@Valid @RequestBody Canteen canteen) {
        // TODO: 调用 CanteenService.add(canteen)
        return Result.success();
    }

    @Operation(summary = "编辑食堂", description = "修改食堂名称、描述、排序")
    @PutMapping("/canteens/{id}")
    public Result<Void> updateCanteen(@PathVariable Long id, @Valid @RequestBody Canteen canteen) {
        // TODO: 设置 canteen.id = id，调用 CanteenService.update(canteen)
        return Result.success();
    }

    @Operation(summary = "删除食堂", description = "删除食堂（如果还有档口则禁止删除）")
    @DeleteMapping("/canteens/{id}")
    public Result<Void> deleteCanteen(@PathVariable Long id) {
        // TODO: 调用 CanteenService.delete(id)
        return Result.success();
    }

    // ==================== 档口管理 ====================

    @Operation(summary = "新增档口", description = "在指定食堂下创建新档口")
    @PostMapping("/stalls")
    public Result<Void> addStall(@Valid @RequestBody Stall stall) {
        // TODO: 调用 StallService.add(stall)
        return Result.success();
    }

    @Operation(summary = "编辑档口", description = "修改档口名称、描述、排序")
    @PutMapping("/stalls/{id}")
    public Result<Void> updateStall(@PathVariable Long id, @Valid @RequestBody Stall stall) {
        // TODO: 设置 stall.id = id，调用 StallService.update(stall)
        return Result.success();
    }

    @Operation(summary = "删除档口", description = "删除档口（如果还有菜品则禁止删除）")
    @DeleteMapping("/stalls/{id}")
    public Result<Void> deleteStall(@PathVariable Long id) {
        // TODO: 调用 StallService.delete(id)
        return Result.success();
    }
}
