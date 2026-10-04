package com.bjtufood.canteen.controller.admin;

import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.review.dto.StallAvgRatingVO;
import com.bjtufood.review.service.ReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台食堂/档口管理接口（管理端维护的**主数据实体**，口径见
 * docs/api/web/stalls.md）。
 * <p>
 * 生命周期 = <b>列表 / 新增 / 改名 / 删除</b>；归属由实体下拉提供（`stallId`），
 * **不再走「菜品录入按名 upsert」**（该路径制造重复档口，已退役）。
 */
@Tag(name = "08. 后台食堂档口管理", description = "管理员维护食堂 / 档口主数据：列表（档口可按 canteenId 筛选）/ 新增 / 改名 / 删除。"
        + "删除受阻：食堂下仍有档口、档口下仍有菜品 → 400。需要管理员 token。")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class CanteenAdminController {

    private final CanteenService canteenService;
    private final StallService stallService;
    /** 删除档口的「其下仍有菜品」受阻判据由本层编排（跨域计数，避免 canteen → dish 反向依赖）。 */
    private final DishService dishService;
    /**
     * 评价域只读投影。
     * <p>
     * 档口均分原本由 {@code StallServiceImpl} 自行拉取，导致
     * {@code canteen -> review -> dish -> canteen} 成环（dish 需 canteen 的档口名）。
     * 均分是 review 按 dish 聚合的<b>派生展示值</b>，不属于 canteen 的自有知识；
     * 改由本层编排——controller 位于依赖图顶端，<b>跨域只经 Service 契约</b>，
     * 不产生新的包级依赖边，接口出参与口径（无评价按 0.00）保持不变。
     */
    private final ReviewQueryService reviewQueryService;

    @Operation(summary = "后台食堂列表", description = "用途：浏览器管理端查看全部食堂（筛选属性字典）。images 返回可访问的完整 URL 数组。")
    @GetMapping("/canteens")
    public Result<?> listCanteens() {
        return Result.success(canteenService.listAllForAdmin());
    }

    @Operation(summary = "编辑食堂", description = "用途：修改食堂名称（新名重名 → 400）。")
    @PutMapping("/canteens/{id}")
    public Result<Void> updateCanteen(
            @Parameter(description = "食堂ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody Canteen canteen) {
        canteen.setId(id);
        canteenService.update(canteen);
        return Result.success();
    }

    @Operation(summary = "后台档口列表", description = "用途：浏览器管理端查看档口，可按 canteenId 筛选（不传=全部）。images 返回可访问的完整 URL 数组。")
    @GetMapping("/stalls")
    public Result<List<StallAdminVO>> listStalls(
            @Parameter(description = "食堂ID（可选，不传=全部）", example = "1")
            @RequestParam(required = false) Long canteenId) {
        List<StallAdminVO> stalls = stallService.listAllForAdmin(canteenId);
        fillAvgRatings(stalls);
        // 其下菜品数（A2 新出参）：跨域计数由本层编排，避免 canteen → dish 反向依赖
        for (StallAdminVO vo : stalls) {
            vo.setDishCount(dishService.countByStallId(vo.getId()));
        }
        return Result.success(stalls);
    }

    @Operation(summary = "新增食堂", description = "用途：管理端新建食堂。名称应用层查重（重名 400）。")
    @PostMapping("/canteens")
    public Result<CanteenAdminVO> createCanteen(@Valid @RequestBody Canteen canteen) {
        return Result.success(canteenService.createCanteen(canteen.getName()));
    }

    @Operation(summary = "删除食堂", description = "用途：删除食堂。其下仍有档口 → 400（避免孤儿档口）；不存在 → 4001。")
    @DeleteMapping("/canteens/{id}")
    public Result<Void> deleteCanteen(
            @Parameter(description = "食堂ID", example = "1")
            @PathVariable Long id) {
        canteenService.deleteCanteen(id);
        return Result.success();
    }

    @Operation(summary = "新增档口", description = "用途：管理端新建档口（canteenId 必填且须存在；同食堂下名称唯一；floor 须命中楼层字典）。")
    @PostMapping("/stalls")
    public Result<StallAdminVO> createStall(@Valid @RequestBody Stall stall) {
        return Result.success(stallService.createStall(stall));
    }

    @Operation(summary = "删除档口", description = "用途：删除档口。其下仍有菜品 → 400（由本层编排跨域计数）；不存在 → 4001。")
    @DeleteMapping("/stalls/{id}")
    public Result<Void> deleteStall(
            @Parameter(description = "档口ID", example = "1")
            @PathVariable Long id) {
        long dishCount = dishService.countByStallId(id);
        if (dishCount > 0) {
            throw new BusinessException("该档口下仍有 " + dishCount + " 个菜品，不能删除");
        }
        stallService.deleteStall(id);
        return Result.success();
    }

    /**
     * 用 review 域的档口均分回填后台列表（原地修改）。
     * <p>
     * BE-08：一次 IN 查询取回全部档口平均分，替代逐档口查询的 N+1。
     * 无 approved 评价的档口不会出现在结果集中，保留 {@code StallServiceImpl} 已置的 0.00 兜底。
     * <p>
     * 由 {@code StallServiceImpl} 上移至此，以断开 {@code canteen -> review} 包级边。
     */
    private void fillAvgRatings(List<StallAdminVO> stalls) {
        if (stalls == null || stalls.isEmpty()) {
            return;
        }
        List<Long> ids = stalls.stream().map(StallAdminVO::getId).distinct().toList();
        Map<Long, BigDecimal> byStallId = new HashMap<>(ids.size());
        for (StallAvgRatingVO r : reviewQueryService.findAvgRatingByStallIds(ids)) {
            if (r.getStallId() != null) {
                byStallId.put(r.getStallId(), r.getAvgRating());
            }
        }
        for (StallAdminVO vo : stalls) {
            BigDecimal avg = byStallId.get(vo.getId());
            if (avg != null) {
                vo.setAvgRating(avg.setScale(2, RoundingMode.HALF_UP));
            }
        }
    }

    @Operation(summary = "编辑档口", description = "用途：修改档口基础信息（canteenId / name / floor / windowNo）。"
            + "同食堂下重名 → 400；floor 不在楼层字典 → 400。")
    @PutMapping("/stalls/{id}")
    public Result<Void> updateStall(
            @Parameter(description = "档口ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody Stall stall) {
        stall.setId(id);
        stallService.update(stall);
        return Result.success();
    }
}
