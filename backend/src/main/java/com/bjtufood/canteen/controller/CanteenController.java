package com.bjtufood.canteen.controller;

import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 食堂档口公开控制器
 * <p>
 * 无需登录即可查询食堂列表和档口列表。
 * 用于前端首页的食堂快捷筛选栏。
 */
@Tag(name = "食堂档口查询", description = "公开接口，无需登录即可查询食堂和档口列表")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CanteenController {

    private final CanteenService canteenService;
    private final StallService stallService;

    @Operation(summary = "食堂列表（含档口）", description = "获取所有食堂及其下属档口，按 sort_order 排序")
    @GetMapping("/canteens")
    public Result<?> listCanteens() {
        // TODO: 调用 CanteenService.listWithStalls()
        // 返回含档口的食堂层级结构
        return Result.success("食堂列表");
    }

    @Operation(summary = "按食堂查询档口", description = "根据食堂ID查询其下属档口列表")
    @GetMapping("/stalls")
    public Result<?> listStalls(@RequestParam Long canteenId) {
        // TODO: 调用 StallService.listByCanteenId(canteenId)
        return Result.success("档口列表");
    }
}
