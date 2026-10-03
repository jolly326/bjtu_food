package com.bjtufood.correction.controller.admin;

import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionAdminVO;
import com.bjtufood.correction.dto.DishCorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
import com.bjtufood.correction.dto.StallConfirmVO;
import com.bjtufood.correction.service.CorrectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 菜品纠错管理接口（Web 后台，ADM）。
 */
@Tag(name = "后台菜品纠错处理", description = "管理员查看/采纳/拒绝用户提交的菜品信息纠错。需要管理员 token。")
@RestController
@RequestMapping("/admin/corrections")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class CorrectionAdminController {

    private final CorrectionService correctionService;

    @Operation(summary = "纠错列表", description = "ADM。分页，按 status 筛选（pending/adopted/rejected；不传 = 全部）。"
            + "VO 实时回查 dish 补齐 dishName（含已下架；菜品已物理删除为 null）与提交人昵称（匿名提交为 null）。")
    @GetMapping
    public Result<AdminPageResult<DishCorrectionAdminVO>> list(
            @Parameter(description = "处理状态：pending/adopted/rejected；不传 = 全部")
            @RequestParam(required = false) String status,
            @Parameter(description = "按目标菜品筛选（从菜品视角看纠错）", example = "12")
            @RequestParam(required = false) Long dishId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(
                AdminPageResult.of(correctionService.listForAdmin(status, dishId, page, pageSize)));
    }

    @Operation(summary = "纠错详情（差异对照）", description = "ADM。返回 differences[]（**仅仍有差异的项**，"
            + "oldValue 取当前菜品/档口的**实时值**）+ submitted 提交快照；供「**逐项勾选采纳**」。"
            + "楼层项的 affectsOthers=true（采纳会连带同档口所有菜品，UI 需二次确认）。"
            + "目标菜品已物理删除时 differences 为空列表（采纳本身也会 4001）；纠错不存在 → 4001。")
    @GetMapping("/{id}")
    public Result<DishCorrectionDetailVO> detail(
            @Parameter(description = "纠错ID", example = "1")
            @PathVariable Long id) {
        return Result.success(correctionService.getDetail(id));
    }

    @Operation(summary = "采纳纠错（逐项 + 两段式档口确认）", description = "ADM。仅 status=pending 可采纳（否则 400「该纠错已处理」）；"
            + "目标菜品已物理删除返回 4001。**逐项采纳**：acceptedFields 必填且非空（空数组 → 400），"
            + "取值须为详情 differences[].field 且**此刻仍有差异**（否则 400「采纳项无效或已无差异」）——"
            + "只写回选中项，不再「七字段一次性写回」。"
            + "两段式档口确认（**仅在采纳了 canteenName/stallName 项时**）："
            + "①不带 stallId/createIfMissing 调用——提交档口名精确匹配现有档口：命中直接采纳；"
            + "未命中则不执行采纳，HTTP 200 返回 data={needStallConfirm:true, candidates:[{id,name}]}（候选档口列表）；"
            + "②管理端选定既有档口（带 stallId）或确认新建（createIfMissing=true）后再次调用，执行采纳。"
            + "采纳动作：选中项写回目标菜品；实现含 floor 时另外写回**目标档口** stall.floor（同档口其他菜品一并生效，"
            + "菜品无楼层字段）→ status=adopted、reply=附注（缺省「已采纳，菜品信息已更新」）、handled_at=now，"
            + "并向提交人投递「菜品信息更新」（type=correction_handle）站内回执。"
            + "采纳已执行时返回 data=null（code=200）。")
    @PostMapping("/{id}/adopt")
    public Result<?> adopt(
            @Parameter(description = "纠错ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "采纳请求体 {stallId?, createIfMissing?}（两段式档口确认）")
            @RequestBody(required = false) DishCorrectionAdoptReq body) {
        StallConfirmVO confirm = correctionService.adopt(id, body);
        return confirm == null ? Result.success() : Result.success(confirm);
    }

    @Operation(summary = "拒绝纠错", description = "ADM。仅 status=pending 可处理（否则 400「该纠错已处理」）。"
            + "仅接受 JSON body（{reply?, outcome:'rejected', rejectReason}）：**reply 可选**（≤1000 字；留空时回执以不采纳原因为正文），"
            + "outcome 固定 rejected（其他值 400）；rejectReason **必填**（1~200 字，纯空白 → 400「请填写不采纳原因」）。"
            + "处理：status=rejected + reply/reject_reason/handled_at 落库，"
            + "并向已认证提交人投递「菜品信息更新」（type=correction_handle）站内回执（含不采纳原因）。")
    @PutMapping("/{id}")
    public Result<Void> reject(
            @Parameter(description = "纠错ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "拒绝请求体 {reply, outcome:'rejected', rejectReason}；校验失败返回 400")
            @Valid @RequestBody DishCorrectionHandleReq body) {
        correctionService.reject(id, body);
        return Result.success();
    }
}
