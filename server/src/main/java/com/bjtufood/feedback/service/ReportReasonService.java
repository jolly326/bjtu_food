package com.bjtufood.feedback.service;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.feedback.dto.ReportReasonAdminVO;
import com.bjtufood.feedback.dto.ReportReasonVO;

import java.util.List;

/**
 * 举报原因字典服务（A7）。
 *
 * <p>契约真源：docs/web/A-主数据维护/A7-举报原因管理.md 与 docs/schema/report_reason.md。
 *
 * <p>三条不变量（服务端强制）：
 * <ol>
 *   <li><b>数据锚在 `value`</b>：`value` 在用后不可改（无改名端点），改 `label` 免费；</li>
 *   <li><b>删除受引用约束</b>：被任一举报引用 → `400`（下线一律用停用）；</li>
 *   <li><b>至少 1 条启用</b>（停用最后一条 → `400`）且<b>启用 ≤8 条</b>（单选弹层可用性）。</li>
 * </ol>
 */
public interface ReportReasonService {

    /**
     * 公开字典（`GET /report-reasons`）：**只下发启用项**，按 `order` 升序；出参恰 `value` + `label`（客户端契约不变）。
     */
    List<ReportReasonVO> listEnabled();

    /**
     * 管理端列表（`GET /admin/report-reasons`）：**含已停用**，按 `order` 升序，带 `feedbackCount`。
     */
    List<ReportReasonAdminVO> listAllForAdmin();

    /**
     * 新增（默认**启用**、排最后）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 机器值非法/重名、标签非法、启用数已达上限 8
     */
    ReportReasonAdminVO create(String value, String label);

    /**
     * 改名（**只改 `label`**，改名免费）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 原因不存在 / code=400 标签非法
     */
    void rename(Long id, String label);

    /**
     * 启停（只改 `status`）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 不存在 / code=400 status 非法、停用最后一条启用、启用数超上限
     */
    void updateStatus(Long id, String status);

    /**
     * 排序（拖拽后**整体提交全量行**）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 排序提交非法
     */
    void sort(List<SortItem> items);

    /**
     * 删除（**被举报记录引用 → `400`**）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 不存在 / code=400 仍被引用
     */
    void delete(Long id);

    /**
     * 举报提交白名单校验：该 `value` **存在且启用**（停用的原因不能再被提交，但历史记录仍能翻译出中文）。
     *
     * @param value 端上提交的原因机器值
     * @return true = 可提交
     */
    boolean isSubmittable(String value);
}
