package com.bjtufood.banner.controller.admin;

import com.bjtufood.banner.dto.BannerAdminVO;
import com.bjtufood.banner.dto.BannerSaveReq;
import com.bjtufood.banner.dto.BannerStatusReq;
import com.bjtufood.banner.service.BannerService;
import com.bjtufood.common.dto.SortItemsReq;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A5 首页 Banner 管理（管理端）。
 *
 * <p>契约真源：docs/web/A-主数据维护/A5-首页Banner管理.md。归属 `/admin/**` ⇒ 由 `AdminTokenFilter`
 * 统一以口令 `X-Admin-Token` 守卫。
 *
 * <p>公开只读端点（`GET /banners`）仍在 {@code banner/controller/BannerController}，
 * 只下发启用项、只出 `id` + `imageUrl` —— 本控制器**不改动**它。
 */
@Tag(name = "09. 后台首页 Banner 管理", description = "管理员维护首页顶部轮播图：列表（含已停用）/ 新增 / 换图 / 启停 / 排序 / 删除。需要管理员 token。")
@RestController
@RequestMapping("/admin/banners")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class BannerAdminController {

    private final BannerService bannerService;

    @Operation(summary = "Banner 列表", description = "用途：管理端列表（按 order 升序，**含已停用**，不分页）。")
    @GetMapping
    public Result<List<BannerAdminVO>> list() {
        return Result.success(bannerService.listAllForAdmin());
    }

    @Operation(summary = "新增 Banner", description = "用途：新增轮播图（默认**启用**、排最后）。")
    @PostMapping
    public Result<BannerAdminVO> create(@Valid @RequestBody BannerSaveReq req) {
        return Result.success(bannerService.create(req.getImageUrl()));
    }

    @Operation(summary = "编辑 Banner", description = "用途：换图（可编辑字段整体替换）；不存在 → 4001。")
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "Banner ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody BannerSaveReq req) {
        bannerService.update(id, req.getImageUrl());
        return Result.success();
    }

    @Operation(summary = "启停 Banner", description = "用途：只改 status（on 启用 / off 停用）；非法值 400，不存在 4001。")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "Banner ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody BannerStatusReq req) {
        bannerService.updateStatus(id, req.getStatus());
        return Result.success();
    }

    @Operation(summary = "Banner 排序", description = "用途：拖拽后**整体提交全量行**（`{ items: [{ id, order }] }`）；"
            + "缺行 / 重复 id / 重复 order / 未知 id → 400「排序提交非法」。")
    @PutMapping("/sort")
    public Result<Void> sort(@Valid @RequestBody SortItemsReq req) {
        bannerService.sort(req.getItems());
        return Result.success();
    }

    @Operation(summary = "删除 Banner", description = "用途：删除轮播图；不存在 → 4001。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "Banner ID", example = "1") @PathVariable Long id) {
        bannerService.delete(id);
        return Result.success();
    }
}
