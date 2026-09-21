package com.bjtufood.canteen.controller;

import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "02. 食堂与档口", description = "公开查询接口，无需登录。用于首页/搜索筛选条 + 反馈页位置联动（include=stalls）。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CanteenController {

    private final CanteenService canteenService;

    @Operation(
            summary = "食堂列表（可选含档口树）",
            description = """
                    用途：首页/搜索筛选条（不传 include，最小字典 id/name）；
                    反馈页「推荐菜品」位置两级联动（include=stalls，返回含档口树）。
                    2026-09-21 §7.33 端点合并：原 GET /canteens/all 已删除，能力并入本端点可选参数。
                    """
    )
    @GetMapping("/canteens")
    public Result<?> listCanteens(
            @Parameter(description = "档口树开关：stalls=返回含档口树，缺省/其他值=仅食堂字典", example = "stalls")
            @RequestParam(required = false) String include) {
        if ("stalls".equals(include)) {
            return Result.success(canteenService.listWithStalls());
        }
        return Result.success(canteenService.listCanteens());
    }
}
