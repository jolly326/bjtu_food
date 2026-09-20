package com.bjtufood.canteen.controller;

import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "02. 食堂与档口", description = "公开查询接口，无需登录。用于首页、食堂页、档口详情页。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CanteenController {

    private final CanteenService canteenService;

    @Operation(summary = "食堂列表", description = "用途：首页/食堂页展示全部食堂（筛选属性字典）；位置表达 = 食堂 · 楼层 · 档口名；不返回坐标、不涉及距离。返回图片已拼接完整访问地址。")
    @GetMapping("/canteens")
    public Result<List<CanteenInfoVO>> listCanteens() {
        return Result.success(canteenService.listCanteens());
    }

    @Operation(summary = "食堂列表（含档口）", description = "用途：需要一次性渲染食堂和下属档口时使用。测试：直接调用即可。")
    @GetMapping("/canteens/all")
    public Result<?> listCanteensWithStalls() {
        return Result.success(canteenService.listWithStalls());
    }
}
