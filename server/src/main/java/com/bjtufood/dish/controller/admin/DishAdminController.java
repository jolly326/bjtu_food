package com.bjtufood.dish.controller.admin;

import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.dto.DishAdminListItemVO;
import com.bjtufood.dish.dto.DishAdminListQuery;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishCopyReq;
import com.bjtufood.dish.dto.DishStatusReq;
import com.bjtufood.dish.dto.DishAdminVO;
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
@SecurityRequirement(name = "adminToken")
public class DishAdminController {

    private final DishService dishService;

    @Operation(summary = "后台菜品列表", description = "用途：后台菜品管理页。管理员可查看全部菜品（含已下架，分页）。images 返回可访问的完整 URL 数组。")
    @GetMapping
    public Result<AdminPageResult<DishAdminListItemVO>> listMyDishes(
            @Parameter(description = "按档口筛选") @RequestParam(required = false) Long stallId,
            @Parameter(description = "按食堂筛选（经档口间接）") @RequestParam(required = false) Long canteenId,
            @Parameter(description = "按分类键筛选（A6 分类值字典）") @RequestParam(required = false) String mealType,
            @Parameter(description = "按上架状态筛选：on / off；不传 = 全部（含已下架）")
            @RequestParam(required = false) String status,
            @Parameter(description = "关键词（菜名 / 档口名 / 食堂名，与 client 搜索同口径）")
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        DishAdminListQuery query = new DishAdminListQuery(stallId, canteenId, mealType, status, keyword);
        return Result.success(AdminPageResult.of(dishService.listAllForAdmin(query, page, pageSize)));
    }

    @Operation(
            summary = "新增菜品",
            description = "用途：管理员指定 stallId 创建菜品。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "stallId": 1,
                      "name": "测试菜品",
                      "price": 1200,
                      "description": "Swagger UI 测试新增菜品",
                      "images": ["/images/seed/dishes/tomato-egg.jpg"],
                      "attributes": {"dietType": "half", "ingredients": ["egg", "rice"], "flavorTags": ["sour", "sweet"], "serveTemp": "hot"},
                      "status": "on"
                    }
                    """)))
    )
    @PostMapping
    public Result<DishAdminVO> addDish(@Valid @RequestBody DishAdminReq req) {
        return Result.success(dishService.addDish(req));
    }

    @Operation(summary = "菜品详情", description = "用途：编辑回填（全字段）。菜品不存在返回 4001。")
    @GetMapping("/{id}")
    public Result<DishAdminVO> getDish(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        return Result.success(dishService.getForAdmin(id));
    }

    @Operation(summary = "复制菜品", description = "用途：以源菜品为模板新建一条（只改菜名，其余字段全部复制）。"
            + "副本默认**下架**（半成品，确认内容后再上架）。源菜品不存在返回 4001。")
    @PostMapping("/{id}/copy")
    public Result<DishAdminVO> copyDish(
            @Parameter(description = "源菜品ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody DishCopyReq req) {
        return Result.success(dishService.copyDish(id, req.getName()));
    }

    @Operation(summary = "上下架", description = "用途：只改 status（on 上架 / off 下架）。非法值 400，菜品不存在 4001。")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody DishStatusReq req) {
        dishService.updateStatus(id, req.getStatus());
        return Result.success();
    }

    @Operation(summary = "编辑菜品", description = "用途：修改菜品信息（支持部分更新，未传字段不修改）。管理端口令鉴权（AdminTokenFilter）；单口令管理模型下无「档口归属」概念，管理员对全部菜品具备编辑权限。")
    @PutMapping("/{id}")
    public Result<Void> updateDish(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody DishAdminReq req) {
        dishService.updateDish(id, req);
        return Result.success();
    }

    @Operation(summary = "删除菜品", description = "用途：物理删除菜品，并同步删除该菜品关联的评价与浏览足迹。")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDish(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        dishService.deleteDish(id);
        return Result.success();
    }

}
