package com.bjtufood.list.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.list.dto.ListCreateReq;
import com.bjtufood.list.service.ListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 美食清单控制器
 * <p>
 * 学生用户创建、查看、删除美食清单，分享清单给微信好友。
 * 通过分享 token 查看清单无需登录。
 */
@Tag(name = "美食清单", description = "创建清单、查看清单、删除清单、分享清单、一键收藏")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ListController {

    private final ListService listService;

    @Operation(summary = "创建清单", description = "从收藏的菜品中挑选创建美食清单，创建时自动生成分享 token")
    @PostMapping("/lists")
    public Result<?> createList(@Valid @RequestBody ListCreateReq req) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long id = listService.createList(userId, req);
        return Result.success(Map.of("id", id));
    }

    @Operation(summary = "我的清单列表", description = "查看当前用户创建的所有美食清单")
    @GetMapping("/lists")
    public Result<?> listMyLists() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(listService.listByUserId(userId));
    }

    @Operation(summary = "清单详情", description = "查看清单详情及其包含的完整菜品信息")
    @GetMapping("/lists/{id}")
    public Result<?> getListDetail(@PathVariable Long id) {
        return Result.success(listService.getDetail(id));
    }

    @Operation(summary = "删除清单", description = "删除美食清单（级联删除清单项）")
    @DeleteMapping("/lists/{id}")
    public Result<Void> deleteList(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        listService.deleteList(id, userId);
        return Result.success();
    }

    @Operation(summary = "通过分享token查看", description = "无需登录，通过分享链接中的 token 查看清单详情")
    @GetMapping("/lists/share/{token}")
    public Result<?> getByShareToken(@PathVariable String token) {
        return Result.success(listService.getByShareToken(token));
    }

    @Operation(summary = "清单一键收藏", description = "将清单内所有菜品加入我的收藏（已收藏的自动跳过）")
    @PostMapping("/lists/{id}/collect-all")
    public Result<?> collectAll(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(listService.collectAll(id, userId));
    }
}
