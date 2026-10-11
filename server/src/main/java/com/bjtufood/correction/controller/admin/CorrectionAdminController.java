package com.bjtufood.correction.controller.admin;

import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.correction.dto.CorrectionAdminVO;
import com.bjtufood.correction.dto.CorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.StallConfirmVO;
import com.bjtufood.correction.service.CorrectionService;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 菜品问题反馈管理接口（Web 后台，ADM）。
 * <p>
 * <b>按 {@code type} 分派处置</b>：{@code field}（信息有误）走「差异对照 + 逐项采纳」；
 * {@code gone}（已经下架）<b>只能下架</b>（🔴 绝不提供删除 —— 删除会级联清掉该菜全部评价）。
 * <p>
 * 处置状态 / 结论 / 处置文案取自处置任务表（`moderation_task`），提交行本身不被改写。
 */
@Tag(name = "后台菜品问题反馈处理", description = "管理员查看/采纳/驳回用户提交的菜品问题反馈（信息有误 / 已经下架）。需要管理员 token。")
@RestController
@RequestMapping("/admin/corrections")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class CorrectionAdminController {

    private final CorrectionService correctionService;

    @Operation(summary = "问题反馈列表", description = "ADM。分页，按处置状态筛选（pending/handled；不传 = 全部）"
            + "与按 type 筛选（field/gone；不传 = 全部，管理端据此分Tab）。"
            + "VO 实时回查 dish 补齐 dishName（含已下架；菜品已物理删除为 null）与提交人昵称，"
            + "并按处置任务表回填状态 / 结论 / 处置文案 / 处置人。"
            + "**type=gone 的行含 note（选填补充）与 goneUserCount（N 人反馈，仅参考、非下架阈值）。**")
    @GetMapping
    public Result<AdminPageResult<CorrectionAdminVO>> list(
            @Parameter(description = "处置状态：pending/handled；不传 = 全部")
            @RequestParam(required = false) String status,
            @Parameter(description = "问题类型：field=信息有误 / gone=已经下架；不传 = 全部")
            @RequestParam(required = false) String type,
            @Parameter(description = "按目标菜品筛选（从菜品视角看反馈）", example = "12")
            @RequestParam(required = false) Long dishId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(
                AdminPageResult.of(correctionService.listForAdmin(status, type, dishId, page, pageSize)));
    }

    @Operation(summary = "问题反馈详情", description = "ADM。按 type 分派返回："
            + "**type=field** → differences[]（**仅仍有差异的项**，oldValue 取当前菜品/档口的**实时值**），供「**逐项勾选采纳**」；楼层项的 affectsOthers=true（采纳会连带同档口所有菜品，UI 需二次确认）。"
            + "**type=gone** → **不返回差异对照**（differences 恒空），只返回 note + images + goneUserCount，"
            + "处置动作**仅「下架」**（🔴 本流程不提供删除，删除仅在菜品管理中由管理员主动执行）。"
            + "目标菜品已物理删除时 differences 为空列表（采纳本身也会 4001）；反馈不存在 → 4001。")
    @GetMapping("/{id}")
    public Result<CorrectionDetailVO> detail(
            @Parameter(description = "反馈ID", example = "1")
            @PathVariable Long id) {
        return Result.success(correctionService.getDetail(id));
    }

    @Operation(summary = "采纳反馈（按 type 分派）", description = "ADM。仅待处置（moderation_task.status=pending）可采纳（否则 400「该反馈已处理」）；"
            + "目标菜品已物理删除返回 4001。"
            + "**type=gone（已经下架）**：**忽略请求体的差异项，直接下架该菜品**（dish.status=off，**可逆**、评价完整保留）"
            + "→ 处置任务归档 handled/replyContent 缺省「已下架，感谢反馈」、标题「菜品已下架」，并投递站内回执。"
            + "⚠️ **本流程绝不删除菜品**（review.dish_id ON DELETE CASCADE ⇒ 删除会永久清空该菜评价）；"
            + "误下架可在菜品管理中重新上架。"
            + "**type=field（信息有误）**：acceptedFields 必填且非空（空数组 → 400），"
            + "取值须为详情 differences[].field 且**此刻仍有差异**（否则 400「采纳项无效或已无差异」）——只写回选中项。"
            + "两段式档口确认（**仅在采纳了 canteenName/stallName 项时**）："
            + "①不带 stallId/createIfMissing 调用——提交档口名精确匹配现有档口：命中直接采纳；"
            + "未命中则不执行采纳，HTTP 200 返回 data={needStallConfirm:true, candidates:[{id,name}]}（候选档口列表）；"
            + "②管理端选定既有档口（带 stallId）或确认新建（createIfMissing=true）后再次调用，执行采纳。"
            + "采纳动作：选中项写回目标菜品；含 floor 时另外写回**目标档口** stall.floor（同档口其他菜品一并生效）"
            + "→ 处置任务归档 handled（附注缺省「已采纳，菜品信息已更新」；部分采纳时逐项告知已采纳/未采纳），并投递站内回执。"
            + "采纳已执行时返回 data=null（code=200）。")
    @PostMapping("/{id}/adopt")
    public Result<?> adopt(
            @Parameter(description = "反馈ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "**field 型**采纳请求体 {acceptedFields[], stallId?, createIfMissing?, replyContent?}；"
                    + "**gone 型**忽略差异项参数（下架无需勾选）")
            @RequestBody(required = false) DishCorrectionAdoptReq body) {
        StallConfirmVO confirm = correctionService.adopt(id, body, SecurityUtil.getCurrentAdminId());
        return confirm == null ? Result.success() : Result.success(confirm);
    }

    @Operation(summary = "驳回反馈", description = "ADM。仅待处置（moderation_task.status=pending）可处理（否则 400「该纠错已处理」）。"
            + "仅接受 JSON body（{replyTitle?, replyContent, outcome:'rejected'}）："
            + "outcome 固定 rejected（其他值 400）；replyContent **必填**（即为不采纳原因，1~600 字，纯空白 → 400「请填写不采纳原因」）；"
            + "replyTitle 可选（缺省按类型给「菜品信息未采纳」/「菜品仍在售」）。"
            + "**gone 型驳回**语义为「经核实仍在售」，建议理由写明原因（如「今日临时售罄，明天恢复」）。"
            + "处理：处置任务归档 rejected + 处置文案，并投递站内回执（含不采纳原因）。")
    @PutMapping("/{id}")
    public Result<Void> reject(
            @Parameter(description = "反馈ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "处置请求体 {replyTitle, replyContent, outcome:'rejected'}；校验失败返回 400")
            @Valid @RequestBody ModerationHandleReq body) {
        correctionService.reject(id, body, SecurityUtil.getCurrentAdminId());
        return Result.success();
    }
}
