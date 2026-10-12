package com.bjtufood.correction.controller.admin;

import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import com.bjtufood.correction.dto.CorrectionAdminVO;
import com.bjtufood.correction.dto.CorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
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
 * <b>管理端处置不改写菜品</b>：管理员「阅读」反馈后，统一两个处置动作 ——
 * **标记已处理**（接受）与 **驳回**（拒绝），两者都只归档 {@code moderation_task}
 * 并投递站内回执；菜品资料修改 / {@code gone} 可逆下架由管理员在菜品管理（web/dishes）手动执行。
 * <p>
 * 提交行本身只存提交事实，不被处置改写。
 */
@Tag(name = "后台菜品问题反馈处理", description = "管理员查看/标记已处理/驳回用户提交的菜品问题反馈（信息有误 / 已经下架）。需要管理员 token。")
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

    @Operation(summary = "问题反馈详情", description = "ADM。返回提交项对照（只读）："
            + "**type=field** → differences[]（**只列用户提交过的项**，oldValue 取当前菜品/档口**实时值**，same 标记是否一致），供管理员阅读判断；"
            + "**type=gone** → differences 恒空，只返回 note + images + goneUserCount。"
            + "菜品已物理删除时 oldValue 各项为 null（无从对照）；反馈不存在 → 4001。")
    @GetMapping("/{id}")
    public Result<CorrectionDetailVO> detail(
            @Parameter(description = "反馈ID", example = "1")
            @PathVariable Long id) {
        return Result.success(correctionService.getDetail(id));
    }

    @Operation(summary = "标记已处理（接受反馈）", description = "ADM。仅待处置（moderation_task.status=pending）可处理（否则 400「该反馈已处理」）。"
            + "**只归档处置结论 + 投递站内回执，不改写任何菜品数据**："
            + "处置任务归档 handled / outcome='handled'（已受理，管理员已手动处理），"
            + "标题缺省「已收到你的反馈」、正文缺省「感谢反馈，我们已核实处理」，并投递站内回执。"
            + "菜品资料修改 / gone 型可逆下架由管理员在菜品管理（web/dishes）手动执行，不在本端点。")
    @PostMapping("/{id}/handle")
    public Result<Void> handle(
            @Parameter(description = "反馈ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "处置请求体 {replyTitle?, replyContent?}：均可选（回执标题 ≤40 字 / 处理附注 ≤600 字）")
            @Valid @RequestBody(required = false) DishCorrectionHandleReq body) {
        correctionService.handle(id, body, SecurityUtil.getCurrentAdminId());
        return Result.success();
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
