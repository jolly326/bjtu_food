package com.bjtufood.canteen.controller;

import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "02. 食堂与档口", description = "公开查询接口，无需登录。用于首页、食堂页、档口详情页。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CanteenController {

    private final CanteenService canteenService;

    @Operation(summary = "食堂列表", description = "用途：首页/食堂页展示全部 open 食堂；可选传 lat/lng 按距离排序（首页推荐联动定位）。返回图片已拼接完整访问地址。")
    @GetMapping("/canteens")
    public Result<List<CanteenInfoVO>> listCanteens(
            @Parameter(description = "用户纬度（GCJ-02，可选；传则按距离升序排序）", example = "39.9538")
            @RequestParam(required = false) BigDecimal lat,
            @Parameter(description = "用户经度（GCJ-02，可选）", example = "116.3354")
            @RequestParam(required = false) BigDecimal lng) {
        return Result.success(canteenService.listCanteens(lat, lng));
    }

    @Operation(summary = "食堂列表（含档口）", description = "用途：需要一次性渲染食堂和下属档口时使用。测试：直接调用即可。")
    @GetMapping("/canteens/all")
    public Result<?> listCanteensWithStalls() {
        return Result.success(canteenService.listWithStalls());
    }
}
