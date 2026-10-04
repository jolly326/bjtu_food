package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.dto.SortItemsReq;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishDimensionAdminVO;
import com.bjtufood.dish.dto.DishDimensionSaveReq;
import com.bjtufood.dish.dto.DishValueAdminVO;
import com.bjtufood.dish.dto.DishValueSaveReq;
import com.bjtufood.dish.service.DishAttributeAdminService;
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
 * A4 菜品属性维度与取值管理（管理端）。
 *
 * <p>契约真源：docs/api/web/dimensions.md（10 个端点）。
 * <p>三条约束：`fieldKey` 在用后不可改；维度 / 取值**被引用不可删**（`400`）；
 * `valueType` 切换自动迁移该维度下菜品的数据形状。
 * <p>取值**改名免费**（菜品存的是取值 ID）—— 没有「改 ID」的端点。
 */
@Tag(name = "06. 后台属性维度与取值管理", description = "管理员维护菜品描述属性的维度与取值字典。"
        + "改名免费（数据锚在取值 ID）；维度/取值被引用不可删；单多选切换自动迁移数据。需要管理员 token。")
@RestController
@RequestMapping("/admin/dish-dimensions")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class DishAttributeAdminController {

    private final DishAttributeAdminService service;

    /* ==================== 维度 ==================== */

    @Operation(summary = "维度列表", description = "用途：维度管理页（按 order 升序，不分页）；带 dishCount（使用该维度的菜品数）与 valueCount（其下取值数）。")
    @GetMapping
    public Result<List<DishDimensionAdminVO>> listDimensions() {
        return Result.success(service.listDimensions());
    }

    @Operation(summary = "新增维度", description = "用途：新建维度（默认排最后）。维度键须为 camelCase 且唯一。")
    @PostMapping
    public Result<DishDimensionAdminVO> createDimension(@Valid @RequestBody DishDimensionSaveReq req) {
        return Result.success(service.createDimension(req.getFieldKey(), req.getName(), req.getValueType()));
    }

    @Operation(summary = "维度排序", description = "用途：拖拽后**整体提交全量行**（`{ items: [{ id, order }] }`）；缺行 / 重复 → 400。")
    @PutMapping("/sort")
    public Result<Void> sortDimensions(@Valid @RequestBody SortItemsReq req) {
        service.sortDimensions(req.getItems());
        return Result.success();
    }

    @Operation(summary = "修改维度", description = "用途：改维度名 / 取值类型（**维度键不可改**）。取值类型切换会**自动迁移**该维度下菜品的数据形状；不存在 → 4001。")
    @PutMapping("/{dimensionId}")
    public Result<Void> updateDimension(
            @Parameter(description = "维度ID", example = "1") @PathVariable Long dimensionId,
            @Valid @RequestBody DishDimensionSaveReq req) {
        service.updateDimension(dimensionId, req.getName(), req.getValueType());
        return Result.success();
    }

    @Operation(summary = "删除维度", description = "用途：删除维度（连带其下取值）。**仍被菜品使用 → 400**；不存在 → 4001。")
    @DeleteMapping("/{dimensionId}")
    public Result<Void> deleteDimension(
            @Parameter(description = "维度ID", example = "1") @PathVariable Long dimensionId) {
        service.deleteDimension(dimensionId);
        return Result.success();
    }

    /* ==================== 取值 ==================== */

    @Operation(summary = "取值列表", description = "用途：某维度下的取值（按 order 升序）；带 dishCount（引用该取值的菜品数，删除前判断）。")
    @GetMapping("/{dimensionId}/values")
    public Result<List<DishValueAdminVO>> listValues(
            @Parameter(description = "维度ID", example = "1") @PathVariable Long dimensionId) {
        return Result.success(service.listValues(dimensionId));
    }

    @Operation(summary = "新增取值", description = "用途：新增取值（默认排最后）。同维度下**名称唯一**；维度不存在 → 4001。")
    @PostMapping("/{dimensionId}/values")
    public Result<DishValueAdminVO> createValue(
            @Parameter(description = "维度ID", example = "1") @PathVariable Long dimensionId,
            @Valid @RequestBody DishValueSaveReq req) {
        return Result.success(service.createValue(dimensionId, req.getLabel()));
    }

    @Operation(summary = "取值排序", description = "用途：拖拽后**整体提交该维度下全量取值**；缺行 / 重复 → 400。")
    @PutMapping("/{dimensionId}/values/sort")
    public Result<Void> sortValues(
            @Parameter(description = "维度ID", example = "1") @PathVariable Long dimensionId,
            @Valid @RequestBody SortItemsReq req) {
        service.sortValues(dimensionId, req.getItems());
        return Result.success();
    }

    @Operation(summary = "修改取值", description = "用途：**只改取值名**（改名免费，菜品数据零迁移）；同维度下重名 → 400；不存在 → 4001。")
    @PutMapping("/{dimensionId}/values/{valueId}")
    public Result<Void> updateValue(
            @Parameter(description = "维度ID", example = "1") @PathVariable Long dimensionId,
            @Parameter(description = "取值ID", example = "3") @PathVariable Long valueId,
            @Valid @RequestBody DishValueSaveReq req) {
        service.updateValue(dimensionId, valueId, req.getLabel());
        return Result.success();
    }

    @Operation(summary = "删除取值", description = "用途：删除取值。**仍被菜品引用 → 400**；不存在 → 4001。")
    @DeleteMapping("/{dimensionId}/values/{valueId}")
    public Result<Void> deleteValue(
            @Parameter(description = "维度ID", example = "1") @PathVariable Long dimensionId,
            @Parameter(description = "取值ID", example = "3") @PathVariable Long valueId) {
        service.deleteValue(dimensionId, valueId);
        return Result.success();
    }
}
