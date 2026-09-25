package com.bjtufood.correction.controller.admin;

import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionAdminVO;
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
    public Result<PageResult<DishCorrectionAdminVO>> list(
            @Parameter(description = "处理状态：pending/adopted/rejected；不传 = 全部")
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(PageResult.of(correctionService.listForAdmin(status, page, pageSize)));
    }

    @Operation(summary = "采纳纠错（两段式档口确认）", description = "ADM。仅 status=pending 可采纳（否则 400「该纠错已处理」）；"
            + "目标菜品已物理删除返回 4001。两段式档口确认："
            + "①不带 stallId/createIfMissing 调用——提交档口名精确匹配现有档口：命中直接采纳；"
            + "未命中则不执行采纳，HTTP 200 返回 data={needStallConfirm:true, candidates:[{id,name}]}（候选档口列表）；"
            + "②管理端选定既有档口（带 stallId）或确认新建（createIfMissing=true）后再次调用，执行采纳。"
            + "采纳动作：七字段写回目标菜品 → status=adopted、reply=「已采纳，菜品信息已更新」、handled_at=now，"
            + "并向已认证提交人投递「菜品信息更新」（type=correction_handle）站内回执。"
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
            + "仅接受 JSON body（{reply, outcome:'rejected', rejectReason}）：reply 必填（1~1000 字，纯空白视为未填写），"
            + "缺失/空白返回 400；outcome 固定 rejected（其他值 400）；rejectReason 必填（1~200 字，纯空白 → 400「请填写不采纳原因」）。"
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
