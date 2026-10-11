package com.bjtufood.correction.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.correction.dto.CorrectionAdminVO;
import com.bjtufood.correction.dto.CorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.dto.StallConfirmVO;
import com.bjtufood.moderation.dto.ModerationHandleReq;

/**
 * 菜品问题反馈服务接口（独立资源，端点：POST /dishes/{id}/correction + /admin/corrections）。
 * <p>
 * 提交信息落在 `correction`（append-only）；处置状态与处置文案外置在 `moderation_task`
 * —— 采纳 / 驳回均经 {@code ModerationTaskService#handle} 归档并投递站内回执。
 */
public interface CorrectionService {

    /**
     * 提交菜品问题反馈（<b>需登录</b>：写请求必须绑定账号），并在**同一事务**内预建处置任务行。
     * <p>
     * **局部提交（patch）**：只落库用户改动的字段（name / price / canteenName / stallName /
     * floor / attributes / images），未改动列留 NULL（采纳时不覆盖既有值）；**空请求体 → 400「未提交任何改动」**
     * ——仅改楼层（如「一层」→「二层」）同样算「有改动」，不得被该判据拦下。
     * 校验：无登录态 → 401；菜品须存在且上架（否则 4001）；name 传入时非空 ≤64 字且敏感词命中即 400
     * （写回字段不放行替换版）；price 传入时为 &gt;0 的整数（分）；canteenName/stallName 传入时非空 ≤64 字；
     * floor 传入时非空、须命中楼层受控字典（值即汉字）、≤16 字（空白 → 400「楼层不能为空」，
     * 字典外 → 400，超长 → 400「楼层超长」，上限与 {@code stall.floor VARCHAR(16)} 对齐）；
     * images ≤3 张且逐项 COS 白名单校验（安检转存发生在上传时）。
     *
     * @param userId 提交人用户ID（登录态，非空；缺失 → 401）
     * @param dishId 目标菜品ID（路径参数）
     */
    void submit(Long userId, Long dishId, DishCorrectionReq req);

    /**
     * 管理端问题反馈列表（分页；按 `createdAt DESC`）——<b>支持按处置状态与 {@code type} 筛选</b>。
     * <p>
     * 管理端按类型分 Tab 展示（信息有误 / 已经下架）；{@code type} 不传 = 全部。
     *
     * @param status 处置状态（`pending`/`handled`；可空 = 全部；非法值 `400`）
     * @param type   问题类型（`field`/`gone`；可空 = 全部；非法值 `400`）
     * @param dishId 按目标菜品筛选（可空；从菜品视角看反馈）
     */
    IPage<CorrectionAdminVO> listForAdmin(String status, String type, Long dishId, int page, int pageSize);

    /**
     * 管理端纠错**详情**（含 `differences[]` 差异对照，供「逐项勾选采纳」）。
     * <p>
     * `differences` **只列「仍有差异」的项**（`oldValue` 取当前菜品 / 档口的**实时值**）；
     * 目标菜品已被物理删除时为空列表（无从对照，采纳本身也会 `4001`）。
     *
     * @param id 纠错 ID
     * @return 详情 VO
     * @throws com.bjtufood.common.exception.BusinessException code=4001 纠错不存在
     */
    CorrectionDetailVO getDetail(Long id);

    /**
     * 采纳纠错（管理端，ADM，两段式档口确认）。
     * <p>
     * 档口解析优先级：请求带 stallId（管理端选定，校验存在）&gt; 提交档口名归一化精确匹配现有档口
     * &gt; createIfMissing=true 按名 upsert 新建；均未命中则<b>不执行采纳</b>，
     * 返回 {@link StallConfirmVO}（needStallConfirm=true + 候选档口列表，HTTP 200）供管理端二次选择。
     * <p>
     * 采纳动作：选中项写回 dish（name/price/档口挂靠/attributes/images；可空快照字段不覆盖既有值，
     * 保护「菜品首图必填」不变量）→ 经 {@code ModerationTaskService#handle} 归档处置结果
     * （`outcome=handled`）+ 投递站内回执。{@code type=gone} 的采纳动作恒为「下架该菜品」（可逆）。
     * <p>
     * <b>楼层纠错</b>：{@code floor} 不写回 dish（菜品无楼层字段）——本次纠错携带楼层时，
     * 写回上面已解析出的<b>目标档口</b> {@code stall.floor}，同档口下其他菜品一并生效。
     *
     * @param id      纠错 ID
     * @param req     采纳请求（逐项清单 + 两段式档口确认参数 + 可选采纳附注）
     * @param adminId 处置人账号ID（写处置任务行的 `handled_by`）
     * @return null = 已执行采纳（Controller 返回 Result<Void>）；
     *         非 null = 需要档口确认（未执行任何写操作），Controller 原样下发
     */
    StallConfirmVO adopt(Long id, DishCorrectionAdoptReq req, Long adminId);

    /**
     * 驳回纠错（管理端，ADM，形态对齐反馈处置）：经 {@code ModerationTaskService#handle}
     * 归档 `outcome=rejected` + 投递站内回执。
     * <p>
     * {@code outcome} 固定 {@code rejected}（本端点即驳回动作；传其他值 400）；
     * {@code replyContent} **必填**（即为不采纳原因，纯空白 → 400「请填写不采纳原因」）；
     * {@code replyTitle} 可选（缺省按类型给「菜品信息未采纳」/「菜品仍在售」）。
     *
     * @param id      纠错 ID
     * @param req     处置请求（{@code replyTitle} / {@code replyContent} / {@code outcome}）
     * @param adminId 处置人账号ID
     */
    void reject(Long id, ModerationHandleReq req, Long adminId);
}
