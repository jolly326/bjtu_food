package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "11. 后台菜品管理", description = "管理员维护全部档口的菜品。需要管理员 token。")
@RestController
@RequestMapping("/admin/dishes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DishAdminController {

    private final DishService dishService;

    @Operation(summary = "后台菜品列表", description = "用途：后台菜品管理页。管理员可查看全部菜品（含已下架）。images 返回可访问的完整 URL 数组。")
    @GetMapping
    public Result<?> listMyDishes() {
        return Result.success(dishService.listAllForAdmin());
    }

    @Operation(
            summary = "新增菜品",
            description = "用途：管理员指定 stallId 创建菜品。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "stallId": 1,
                      "name": "测试菜品",
                      "price": 1200,
                      "description": "Knife4j 测试新增菜品",
                      "images": ["/images/seed/dishes/tomato-egg.jpg"],
                      "tags": "recommended",
                      "status": "on"
                    }
                    """)))
    )
    @PostMapping
    public Result<Void> addDish(@Valid @RequestBody DishAdminReq req) {
        dishService.addDish(req);
        return Result.success();
    }

    @Operation(summary = "编辑菜品", description = "用途：修改菜品信息。当前实现未校验菜品是否属于当前管理员档口，后续需要补权限边界。")
    @PutMapping("/{id}")
    public Result<Void> updateDish(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody DishAdminReq req) {
        dishService.updateDish(id, req);
        return Result.success();
    }

    @Operation(summary = "删除菜品", description = "用途：物理删除菜品，并同步删除该菜品关联的评价、收藏和清单项。")
    @AuditLog(action = OperationLogConst.ACTION_DISH_DELETE, targetType = "dish", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDish(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        dishService.deleteDish(id);
        return Result.success();
    }

}
