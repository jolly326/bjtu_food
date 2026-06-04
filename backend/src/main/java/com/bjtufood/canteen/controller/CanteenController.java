package com.bjtufood.canteen.controller;

import com.bjtufood.canteen.dto.BannerVO;
import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @Operation(summary = "首页轮播图", description = "获取首页展示的轮播图列表（状态为 enabled，按 sort_order 排序）")
    @GetMapping("/canteens/banners")
    public Result<List<BannerVO>> listBanners() {
        List<BannerVO> banners = canteenService.listBanners();
        return Result.success(banners);
    }

    @Operation(summary = "食堂列表", description = "获取所有食堂列表（状态为 open，按 sort_order 排序）")
    @GetMapping("/canteens")
    public Result<List<CanteenInfoVO>> listCanteens() {
        List<CanteenInfoVO> canteens = canteenService.listCanteens();
        return Result.success(canteens);
    }

    @Operation(summary = "食堂背景图片", description = "获取食堂名称到背景图片的映射")
    @GetMapping("/canteens/images")
    public Result<Map<String, List<String>>> listCanteenImages() {
        Map<String, List<String>> images = canteenService.listCanteenImages();
        return Result.success(images);
    }

    @Operation(summary = "档口详情", description = "根据食堂名称和档口名称查询档口详情")
    @GetMapping("/canteens/stallDetail")
    public Result<StallDetailVO> getStallDetail(
            @RequestParam(required = false) String canteen,
            @RequestParam(required = false) String canteenName,
            @RequestParam(required = false) String stallName) {
        String resolvedCanteen = canteenName != null ? canteenName : canteen;
        StallDetailVO detail = canteenService.getStallDetail(resolvedCanteen, stallName);
        return Result.success(detail);
    }

    @Operation(summary = "食堂列表（含档口）", description = "获取所有食堂及其下属档口，按 sort_order 排序")
    @GetMapping("/canteens/all")
    public Result<?> listCanteensWithStalls() {
        return Result.success(canteenService.listWithStalls());
    }

    @Operation(summary = "按食堂查询档口", description = "根据食堂ID查询其下属档口列表")
    @GetMapping("/stalls")
    public Result<?> listStalls(@RequestParam Long canteenId) {
        return Result.success(stallService.listByCanteenId(canteenId));
    }
}
