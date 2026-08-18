package com.bjtufood.list.controller;

import com.bjtufood.common.annotation.RequireVerified;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.list.dto.ListCreateReq;
import com.bjtufood.list.service.ListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "06. 美食清单", description = "创建清单、我的清单、清单详情、分享清单、一键收藏。除分享查看外均需要登录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ListController {

    private final ListService listService;

    @Operation(
            summary = "创建美食清单",
            description = "用途：用户从菜品中创建一个可分享的清单，后端自动生成 shareToken。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    {
                      "name": "明湖餐厅必吃",
                      "description": "适合第一次来明湖餐厅的同学",
                      "dishIds": [1, 2, 6]
                    }
                    """)))
    )
    @RequireVerified
    @PostMapping("/lists")
    public Result<?> createList(@Valid @RequestBody ListCreateReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long id = listService.createList(userId, req);
        return Result.success(Map.of("id", id));
    }

    @Operation(summary = "我的美食清单", description = "用途：个人中心查看自己创建的全部清单。", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/lists")
    public Result<?> listMyLists() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(listService.listByUserId(userId));
    }

    @Operation(summary = "清单详情", description = "用途：查看清单及其中菜品。需要登录，且仅清单归属人可查看（越权返回 403）。", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/lists/{id}")
    public Result<?> getListDetail(
            @Parameter(description = "清单ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(listService.getDetail(id, userId));
    }

    @Operation(summary = "删除清单", description = "用途：删除自己创建的清单。", security = @SecurityRequirement(name = "bearerAuth"))
    @RequireVerified
    @DeleteMapping("/lists/{id}")
    public Result<Void> deleteList(
            @Parameter(description = "清单ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        listService.deleteList(id, userId);
        return Result.success();
    }

    @Operation(summary = "通过分享 token 查看清单", description = "用途：别人打开分享链接时查看清单详情。无需登录。测试示例：先创建清单，从详情中复制 shareToken。")
    @GetMapping("/lists/share/{token}")
    public Result<?> getByShareToken(
            @Parameter(description = "分享token", example = "abc123")
            @PathVariable String token) {
        return Result.success(listService.getByShareToken(token));
    }

    @Operation(summary = "清单一键收藏", description = "用途：将清单内全部菜品加入我的收藏，已收藏的自动跳过。", security = @SecurityRequirement(name = "bearerAuth"))
    @RequireVerified
    @PostMapping("/lists/{id}/collect-all")
    public Result<?> collectAll(
            @Parameter(description = "清单ID", example = "1")
            @PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(listService.collectAll(id, userId));
    }
}
