package com.bjtufood.canteen.controller.admin;

import com.bjtufood.canteen.entity.Banner;
import com.bjtufood.canteen.service.BannerService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "12. 后台轮播图管理", description = "系统管理员维护首页轮播图。需要管理员 token。")
@RestController
@RequestMapping("/admin/banners")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BannerAdminController {

    private final BannerService bannerService;

    @Operation(summary = "后台轮播图列表", description = "用途：浏览器管理端查看全部轮播图，返回图片为完整可访问 URL。")
    @GetMapping
    public Result<?> listBanners() {
        return Result.success(bannerService.listAll());
    }

    @Operation(
            summary = "新增轮播图",
            description = "用途：创建新的首页轮播图。images 字段传 JSON 字符串。",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "title": "首页推荐",
                      "subtitle": "今日热菜推荐",
                      "type": "dish",
                      "targetId": 1,
                      "canteenId": 1,
                      "sortOrder": 1,
                      "status": "enabled",
                      "images": "[\\"/images/seed/dishes/tomato-egg.jpg\\"]"
                    }
                    """)))
    )
    @PostMapping
    public Result<Void> addBanner(@Valid @RequestBody Banner banner) {
        bannerService.add(banner);
        return Result.success();
    }

    @Operation(summary = "编辑轮播图", description = "用途：修改轮播图标题、图片、跳转等。")
    @PutMapping("/{id}")
    public Result<Void> updateBanner(
            @Parameter(description = "轮播图ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody Banner banner) {
        banner.setId(id);
        bannerService.update(banner);
        return Result.success();
    }

    @Operation(summary = "删除轮播图", description = "用途：删除轮播图。")
    @DeleteMapping("/{id}")
    public Result<Void> deleteBanner(
            @Parameter(description = "轮播图ID", example = "1")
            @PathVariable Long id) {
        bannerService.delete(id);
        return Result.success();
    }
}
