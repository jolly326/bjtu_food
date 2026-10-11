package com.bjtufood.feedback.service;

import com.bjtufood.feedback.dto.OptionAdminVO;
import com.bjtufood.feedback.dto.OptionVO;

import java.util.List;
import java.util.Map;

/**
 * 分类字典服务共性契约（`feedback_option` / `report_option` 两张同族字典共用）。
 *
 * <p>三条不变量（服务端强制，两张字典同口径）：
 * <ol>
 *   <li><b>数据锚在选项 ID</b>：ID 由后端生成、历史工单按它落库；改 `name` 免费；</li>
 *   <li><b>删除受引用约束</b>：被任一工单引用 → `400`（下线一律用停用）；</li>
 *   <li><b>至少 1 条启用</b>（停用最后一条 → `400`）且<b>启用 ≤8 条</b>（单选可用性）。</li>
 * </ol>
 */
public interface OptionDictionaryService {

    /**
     * 公开字典（`GET /feedback-options` / `GET /report-options`）：**只下发启用项**，按 `id` 升序；
     * 出参恰 `id` + `name`。
     */
    List<OptionVO> listEnabled();

    /**
     * 管理端列表（`GET /admin/feedback-options` / `GET /admin/report-options`）：
     * **含已停用**，按 `id` 升序，带 `usageCount`。
     */
    List<OptionAdminVO> listAllForAdmin();

    /**
     * 新增（默认**启用**；选项 ID 由后端生成）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 名非法 / 重名、启用数已达上限 8
     */
    OptionAdminVO create(String name);

    /**
     * 改名（**只改 `name`**，改名免费）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 不存在 / code=400 名非法
     */
    void rename(Long id, String name);

    /**
     * 启停（只改 `status`）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 不存在 / code=400 status 非法、停用最后一条启用、启用数超上限
     */
    void updateStatus(Long id, String status);

    /**
     * 删除（**被工单引用 → `400`**）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 不存在 / code=400 仍被引用
     */
    void delete(Long id);

    /**
     * 提交白名单校验：该选项 ID **存在且启用**（停用的选项不能再被提交，但历史工单仍能翻译出中文）。
     *
     * @param optionId 端上提交的选项 ID
     * @return true = 可提交
     */
    boolean isSubmittable(Long optionId);

    /**
     * 全量 ID → 中文名映射（**含停用项**）：管理端翻译历史工单用 —— 停用选项的记录仍要能译出中文。
     */
    Map<Long, String> nameByIdAll();
}
