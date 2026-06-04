package com.bjtufood.canteen.controller;

import com.bjtufood.canteen.dto.BannerVO;
import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "02. 食堂与档口", description = "公开查询接口，无需登录。用于首页、食堂页、档口详情页。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CanteenController {

    private final CanteenService canteenService;
    private final StallService stallService;

    @Operation(summary = "首页轮播图", description = "用途：首页顶部轮播。返回 enabled 状态 banner，按 sort_order 升序。测试：直接调用即可。")
    @GetMapping("/canteens/banners")
    public Result<List<BannerVO>> listBanners() {
        return Result.success(canteenService.listBanners());
    }

    @Operation(summary = "食堂列表", description = "用途：首页/食堂页展示全部 open 食堂。返回图片已拼接完整访问地址。测试：直接调用即可。")
    @GetMapping("/canteens")
    public Result<List<CanteenInfoVO>> listCanteens() {
        return Result.success(canteenService.listCanteens());
    }

    @Operation(summary = "食堂图片映射", description = "用途：前端按食堂名获取背景图。返回结构为 { 食堂名: [图片URL] }。测试：直接调用即可。")
    @GetMapping("/canteens/images")
    public Result<Map<String, List<String>>> listCanteenImages() {
        return Result.success(canteenService.listCanteenImages());
    }

    @Operation(
            summary = "档口详情",
            description = """
                    用途：从食堂页进入某个档口时展示档口介绍和图片。
                    参数：canteen 或 canteenName 二选一；stallName 必填。
                    测试示例：/canteens/stallDetail?canteenName=明湖餐厅&stallName=明湖一层基本伙食窗口
                    """
    )
    @GetMapping("/canteens/stallDetail")
    public Result<StallDetailVO> getStallDetail(
            @Parameter(description = "食堂名称，兼容旧前端参数", example = "明湖餐厅")
            @RequestParam(required = false) String canteen,
            @Parameter(description = "食堂名称，推荐参数", example = "明湖餐厅")
            @RequestParam(required = false) String canteenName,
            @Parameter(description = "档口名称", example = "明湖一层基本伙食窗口")
            @RequestParam(required = false) String stallName) {
        String resolvedCanteen = canteenName != null ? canteenName : canteen;
        return Result.success(canteenService.getStallDetail(resolvedCanteen, stallName));
    }

    @Operation(summary = "食堂列表（含档口）", description = "用途：需要一次性渲染食堂和下属档口时使用。测试：直接调用即可。")
    @GetMapping("/canteens/all")
    public Result<?> listCanteensWithStalls() {
        return Result.success(canteenService.listWithStalls());
    }

    @Operation(summary = "按食堂查询档口", description = "用途：根据食堂 ID 查询下属 open 档口。测试示例：/stalls?canteenId=1")
    @GetMapping("/stalls")
    public Result<?> listStalls(
            @Parameter(description = "食堂ID", example = "1")
            @RequestParam Long canteenId) {
        return Result.success(stallService.listByCanteenId(canteenId));
    }
}
