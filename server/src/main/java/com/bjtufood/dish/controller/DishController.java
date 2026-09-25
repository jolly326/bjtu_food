package com.bjtufood.dish.controller;

import com.bjtufood.common.config.IpRateLimiter;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.GuessLikeVO;
import com.bjtufood.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "03. 菜品浏览", description = "公开菜品分页查询、热搜榜单、菜品详情、浏览量记录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
public class DishController {

    private final DishService dishService;
    private final IpRateLimiter ipRateLimiter;

    /** IP 限频：同 IP 每分钟 ≤30 次（浏览详情是高频正常行为，阈值须宽松到用户无感） */
    private static final IpRateLimiter.Rule RULE_VIEW_PER_MINUTE = new IpRateLimiter.Rule(30, 60_000L);
    /** IP 限频：同 IP 每小时 ≤300 次，补齐「分钟窗口内低频慢刷」的缺口 */
    private static final IpRateLimiter.Rule RULE_VIEW_PER_HOUR = new IpRateLimiter.Rule(300, 3_600_000L);

    @Operation(
            summary = "猜你喜欢",
            description = "用途：搜索页「猜你喜欢」区块。每次随机抽取在售菜品名"
                    + "（不看热度、不排序、不做个性化推荐算法），故不缓存；出参仅 keyword。公开接口。"
    )
    @GetMapping("/dishes/for-you")
    public Result<List<GuessLikeVO>> guessLike() {
        return Result.success(dishService.guessLike());
    }

    @Operation(
            summary = "菜品分页查询",
            description = """
                    用途：首页网格、搜索页。
                    测试示例：/dishes?page=1&pageSize=10&keyword=牛肉
                    参数集恰为 4 项：page、pageSize、keyword、mealType（排序恒为服务端热度倒序，无排序入口）。
                    出参为列表专用 DishListItemVO（8 字段；详情专属字段不发）。
                    """
    )
    @GetMapping("/dishes")
    public Result<PageResult<DishListItemVO>> listDishes(@ModelAttribute DishQueryReq req) {
        return Result.success(PageResult.of(dishService.listDishes(req)));
    }

    @Operation(
            summary = "菜品大类字典",
            description = """
                    用途：首页横向大类标签栏数据源。
                    只下发「当前有在售菜品」的大类（空类自动隐藏）；文案与顺序由后端 MealTypeConst 唯一定义，
                    端上不得维护任何标签中文映射。公开接口。
                    测试示例：/dishes/meal-types
                    """
    )
    @GetMapping("/dishes/meal-types")
    public Result<List<com.bjtufood.dish.dto.MealTypeVO>> listMealTypes() {
        return Result.success(dishService.listMealTypes());
    }

    @Operation(
            summary = "菜品描述四维字典",
            description = """
                    用途：菜品描述四维（荤素 / 主料 / 口味 / 冷热）的**取值与中文标签唯一真源**。
                    每项含 field（维度字段名，与菜品出参字段名逐字一致）/ value（机器值）/
                    label（中文标签）/ order（组内顺序）。
                    小程序端与管理端**共用同一份字典**：端上据此把菜品出参的机器值映射为中文，
                    管理端另用它渲染表单选项——两端 SHALL NOT 再硬编码映射表或选项数组。
                    内容取自后端常量表 DishAttributeConst，**不依赖库表数据**（库中无菜品时同样完整下发）。
                    公开接口。测试示例：/dishes/attributes
                    """
    )
    @GetMapping("/dishes/attributes")
    public Result<List<com.bjtufood.dish.dto.DishAttributeVO>> listAttributes() {
        return Result.success(dishService.listAttributes());
    }

    @Operation(
            summary = "菜品详情",
            description = """
                    用途：菜品详情页。未登录可访问；登录态与游客态返回结构一致。
                    **副作用（浏览计数，PV 口径）**：每次成功响应（code=200）view_count +1，
                    并写入一条访问日志（view_log，append-only；游客 user_id=0）；
                    4001（菜品不存在）与请求失败不计数。
                    滥用防护：同 IP 每分钟 ≤30 次、每小时 ≤300 次（正常浏览远低于此，用户无感）。
                    测试示例：/dishes/1
                    """
    )
    @GetMapping("/dishes/{id}")
    public Result<DishDetailVO> getDishDetail(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        checkViewIpRateLimit();
        // 计数随详情成功响应发生（service 内成功路径执行）
        return Result.success(dishService.getDishDetail(id));
    }

    /**
     * IP 维度滥用防护（浏览计数防刷兜底）。
     * <p>
     * 计数内聚于**匿名高频读接口**（{@code GET /dishes/{id}} 的成功响应），故由 IP 限频兜底：
     * 不限制「谁」「何时」，只限制同一 IP 的请求速率。
     * <p>
     * 阈值「每分钟 30 + 每小时 300」：正常浏览（连续翻菜）远低于此、用户无感；
     * 但可挡住脚本级刷量。接入层防护放 Controller（非业务逻辑），计数仍归 {@code DishService}。
     * 写法对齐既有先例 {@code FeedbackController#checkIpRateLimit}。
     */
    private void checkViewIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "dish-detail", ClientIpUtil.resolveCurrent(), RULE_VIEW_PER_MINUTE, RULE_VIEW_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("操作过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }
}
