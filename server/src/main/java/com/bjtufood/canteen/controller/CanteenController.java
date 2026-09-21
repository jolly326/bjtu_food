package com.bjtufood.canteen.controller;

import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "02. 食堂与档口", description = "公开查询接口，无需登录。用于首页 / 搜索的食堂筛选（默认返回 id / name 字典）与反馈页位置两级联动（include=stalls）。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CanteenController {

    private final CanteenService canteenService;

    /**
     * 食堂字典 —— **唯一公开食堂端点**（2026-09-21 端点合并，见 docs/project_spec.md §7.33）。
     * <p>
     * 不传 {@code include} → {@code List<CanteenInfoVO>}（恰为 id / name）；
     * {@code include=stalls} → {@code List<CanteenWithStallsVO>}（id / name / stalls[]，档口项恰为 id / name）。
     * 非法 {@code include} 值按缺省处理（不报错）。
     * <p>
     * 原 {@code GET /canteens/all} 已删除并合入本端点（调用方仅需改调用方式）。
     */
    @Operation(summary = "食堂字典", description = "用途：首页 / 搜索的食堂筛选（不传 include，返回 id / name 最小字典）；反馈页位置两级联动（include=stalls，返回含档口树）。位置表达 = 食堂 · 楼层 · 档口名；不返回坐标、不涉及距离。")
    @GetMapping("/canteens")
    public Result<?> listCanteens(@RequestParam(name = "include", required = false) String include) {
        if ("stalls".equalsIgnoreCase(include)) {
            return Result.success(canteenService.listWithStalls());
        }
        return Result.success(canteenService.listCanteens());
    }
}
