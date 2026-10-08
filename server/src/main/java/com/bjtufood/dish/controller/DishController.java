package com.bjtufood.dish.controller;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.ratelimit.IdEnumerationGuard;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.bjtufood.dish.dto.DishAttributeEditVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.GuessLikeVO;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.dish.view.DishViewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "03. 菜品浏览", description = "公开菜品分页查询、猜你喜欢、菜品详情、浏览量记录。")
@RestController
@RequestMapping
@RequiredArgsConstructor
@Validated
public class DishController {

    private final DishService dishService;
    private final IpRateLimiter ipRateLimiter;
    /** ID 枚举 / 蜜罐探针检测（限频压速率，本闸压「按 id 顺序把数据慢慢捞走」） */
    private final IdEnumerationGuard idEnumerationGuard;

    /** IP 限频：同 IP 每分钟 ≤30 次（浏览详情是高频正常行为，阈值须宽松到用户无感） */
    private static final IpRateLimiter.Rule RULE_VIEW_PER_MINUTE = new IpRateLimiter.Rule(30, 60_000L);
    /** IP 限频：同 IP 每小时 ≤300 次，补齐「分钟窗口内低频慢刷」的缺口 */
    private static final IpRateLimiter.Rule RULE_VIEW_PER_HOUR = new IpRateLimiter.Rule(300, 3_600_000L);

    @Operation(
            summary = "猜你喜欢",
            description = "用途：搜索页「猜你喜欢」区块。抽取在售菜品名"
                    + "（不看热度、不排序、不做个性化推荐算法），出参仅 name。公开接口。"
                    + "刷新边界 = 重进小程序（2026-09-29 收窄）：端上传会话级 seed ⇒ 服务端按 "
                    + "CRC32(seed:ID) 稳定伪随机序取数，同一次会话内多次进入拿到同一批词条"
                    + "（内容不会自变），重进小程序才整体重洗（新鲜度）；"
                    + "不传 seed ⇒ 退回 ORDER BY RAND()（可选参数，向后兼容）。"
    )
    @GetMapping("/dishes/for-you")
    public Result<List<GuessLikeVO>> guessLike(
            @Parameter(description = "会话随机种子（可选）：端上冷启动生成、会话内恒定，重进小程序才换。"
                    + "不传 ⇒ ORDER BY RAND() 真随机（向后兼容旧端 / 直连调试）", example = "m3k9x7q2")
            @RequestParam(required = false) String seed
    ) {
        return Result.success(dishService.guessLike(seed));
    }

    @Operation(
            summary = "菜品分页查询",
            description = """
                    用途：首页网格、搜索页。
                    测试示例：/dishes?page=1&pageSize=10&keyword=牛肉
                    参数集恰为 5 项：page、pageSize、keyword、view、seed（筛选与排序由所选 view 决定：种子里 7 个视图均按 CRC32(seed:ID) 会话伪随机序；无排序入口）。
                    出参为列表专用 DishListItemVO（8 字段；详情专属字段不发）。
                    """
    )
    @GetMapping("/dishes")
    public Result<PageResult<DishListItemVO>> listDishes(@ModelAttribute DishQueryReq req) {
        checkListViewIpRateLimit();
        return Result.success(PageResult.of(dishService.listDishes(req)));
    }

    @Operation(
            summary = "首页筛选视图字典",
            description = """
                    用途：首页横向筛选栏数据源。
                    下发可见视图（含「为你推荐」等聚合视角）；文案与顺序由后端视图表唯一定义，
                    端上不得维护任何标签中文映射（端上只认 id + label，回传 view=<id>）。
                    空视图自动隐藏（当前无在售菜品匹配即不下发）。
                    公开接口。测试示例：/dishes/views
                    """
    )
    @GetMapping("/dishes/views")
    public Result<List<DishViewVO>> listDishViews() {
        return Result.success(dishService.listDishViews());
    }

    @Operation(
            summary = "菜品详情",
            description = """
                    用途：菜品详情页。未登录可访问；登录态与游客态返回结构一致。
                    **副作用（浏览计数，PV 口径）**：每次成功响应（code=200）写入一行浏览明细（dish_view_log，精确到秒，不去重）；
                    4001（菜品不存在）与请求失败不计数。浏览量不参与任何排序，仅供管理端「近 30 天浏览」统计。
                    滥用防护：同 IP 每分钟 ≤30 次、每小时 ≤300 次（正常浏览远低于此，用户无感）。
                    测试示例：/dishes/1
                    """
    )
    @GetMapping("/dishes/{id}")
    public Result<DishDetailVO> getDishDetail(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        checkViewIpRateLimit();
        String clientIp = ClientIpUtil.resolveCurrent();
        // 已被判定为枚举/爬取的来源：临时限流（到期自动解除，不封账号）
        long blockedSeconds = idEnumerationGuard.blockedSeconds(clientIp);
        if (blockedSeconds > 0) {
            throw new BusinessException("操作过于频繁，请 " + blockedSeconds + " 秒后再试");
        }
        DishDetailVO detail;
        try {
            detail = dishService.getDishDetail(id);
        } catch (BusinessException e) {
            // 4001 = 资源不存在 ⇒ 计一次蜜罐探针（正常路径几乎不会命中不存在的 id）
            if (e.getCode() == 4001) {
                idEnumerationGuard.recordNotFound(clientIp, id);
            }
            throw e;
        }
        idEnumerationGuard.recordAccess(clientIp, id);
        // 计数随详情成功响应发生（service 内成功路径执行）
        return Result.success(detail);
    }
    @Operation(
            summary = "菜品描述属性编辑态选项（按菜现有维度）",
            description = """
                    用途：菜品问题反馈 / 编辑界面的属性表单（进编辑时才取，按需）。
                    只返回**该菜现有维度**的候选值：每项含 dimensionId（维度 ID，与 GET /dishes/{id} 的
                    attributes[].dimensionId 对齐）/ valueType（single|multi）/
                    options（该维度全部候选值，按 order 升序）。
                    **options 为空数组 = 暂无参考候选**（仍可自由输入）。
                    维度名与当前值在 GET /dishes/{id} 里已有，本端点不重复下发。公开接口。
                    测试示例：/dishes/1/attributes
                    """
    )
    @GetMapping("/dishes/{id}/attributes")
    public Result<List<DishAttributeEditVO>> listDishAttributes(
            @Parameter(description = "菜品ID", example = "1")
            @PathVariable Long id) {
        return Result.success(dishService.listDishAttributes(id));
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
    /** 列表读限频（IP 维度）：60/分 · 600/时 —— 防脚本高频翻页把列表全量拉走 */
    private static final IpRateLimiter.Rule RULE_LIST_PER_MINUTE = new IpRateLimiter.Rule(60, 60_000L);

    private static final IpRateLimiter.Rule RULE_LIST_PER_HOUR = new IpRateLimiter.Rule(600, 3_600_000L);

    /**
     * 列表读限频（IP 维度 60/分 · 600/时）：与详情限频同口径 —— 挡的是「高频翻页整表拉走」。
     * 正常浏览（含主动翻页）远低于此，用户无感。
     */
    private void checkListViewIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "dish-list", ClientIpUtil.resolveCurrent(), RULE_LIST_PER_MINUTE, RULE_LIST_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("操作过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }

    private void checkViewIpRateLimit() {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "dish-detail", ClientIpUtil.resolveCurrent(), RULE_VIEW_PER_MINUTE, RULE_VIEW_PER_HOUR);
        if (waitSeconds > 0) {
            throw new BusinessException("操作过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }
}
