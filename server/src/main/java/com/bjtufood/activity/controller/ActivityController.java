package com.bjtufood.activity.controller;

import com.bjtufood.activity.dto.ActivityVO;
import com.bjtufood.activity.service.ActivityService;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 最新活动（小程序「最新活动」页，task 公众号文章卡片）
 * <p>
 * 公开接口，无需登录；返回 enabled 活动，按 sort_order 升序；点击卡片由小程序 web-view 打开公众号文章。
 */
@Tag(name = "20. 最新活动", description = "最新活动（公众号文章卡片）列表，公开接口。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(
            summary = "最新活动列表",
            description = """
                    用途：小程序「最新活动」页卡片列表数据来源。
                    返回 enabled 活动（公众号文章卡片），按 sort_order 升序、created_at 降序。
                    每张卡片含 articleUrl（公众号文章链接），小程序点击后经 web-view 打开。
                    """
    )
    @GetMapping("/activities")
    public Result<List<ActivityVO>> listActivities() {
        return Result.success(activityService.listEnabled());
    }
}
