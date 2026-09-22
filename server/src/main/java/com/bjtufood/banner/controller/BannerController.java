package com.bjtufood.banner.controller;

import com.bjtufood.banner.dto.BannerVO;
import com.bjtufood.banner.service.BannerService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页轮播图接口（公开只读）
 * <p>
 * 2026-09-22 新增：{@code GET /banners} —— 首页顶部 16:10 多图轮播的数据源。
 * 无请求参数；只返回启用项、按 sort_order 升序；出参仅 {@code id} + {@code imageUrl}（绝对 URL）。
 * 本期无管理端写入口（素材由 seed_data.sql 维护）。
 */
@Tag(name = "04. 首页轮播", description = "首页顶部 Banner 轮播图（公开只读）。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(
            summary = "首页顶部轮播图",
            description = """
                    用途：首页顶部 Banner 轮播（素材统一 16:10，端上 aspectFill 铺满）。
                    只返回启用中（status='on'）的 Banner，按 sort_order 升序；无分页（运营位数量级极小）。
                    出参仅 id + imageUrl（imageUrl 为可直接渲染的绝对 URL）；无请求参数、v1 无跳转字段。
                    测试示例：/banners
                    """
    )
    @GetMapping("/banners")
    public Result<List<BannerVO>> listBanners() {
        return Result.success(bannerService.listBanners());
    }
}
