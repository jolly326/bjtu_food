package com.bjtufood.correction.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionAdminVO;
import com.bjtufood.correction.dto.DishCorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.dto.StallConfirmVO;

/**
 * 菜品信息纠错服务接口（独立资源，端点：POST /dishes/{id}/correction + /admin/corrections）。
 */
public interface CorrectionService {

    /**
     * 提交菜品信息纠错（PUB：游客与登录用户均可，匿名允许），status=pending。
     * <p>
     * **局部提交（patch）**：只落库用户改动的字段（name / price / canteenName / stallName /
     * floor / attributes / images），未改动列留 NULL（采纳时不覆盖既有值）；**空请求体 → 400「未提交任何改动」**
     * ——仅改楼层（如 1F → 2F）同样算「有改动」，不得被该判据拦下。
     * 校验：菜品须存在且上架（否则 4001）；name 传入时非空 ≤64 字且敏感词命中即 400
     * （写回字段不放行替换版）；price 传入时为 &gt;0 的整数（分）；canteenName/stallName 传入时非空 ≤64 字；
     * floor 传入时非空 ≤16 字（空白 → 400「楼层不能为空」，超长 → 400「楼层超长」，
     * 上限与 {@code stall.floor VARCHAR(16)} 对齐）；
     * images ≤3 张且逐项 COS 白名单校验（安检转存发生在上传时）。
     *
     * @param userId 提交人用户ID（游客为 null）
     * @param dishId 目标菜品ID（路径参数）
     */
    void submit(Long userId, Long dishId, DishCorrectionReq req);

    /**
     * 纠错列表（管理端，ADM）：分页，status 筛选（pending/adopted/rejected，不传 = 全部）。
     * VO 补齐 dishName（实时回查 dish，含已下架/已删除兜底）与提交人昵称。
     */
    /**
     * 管理端纠错列表（分页；按 `createdAt DESC`）。
     *
     * @param status 处理状态（`pending`/`adopted`/`rejected`；可空 = 全部；非法值 `400`）
     * @param dishId 按目标菜品筛选（可空；从菜品视角看纠错）
     */
    IPage<DishCorrectionAdminVO> listForAdmin(String status, Long dishId, int page, int pageSize);

    /**
     * 管理端问题反馈列表（分页；按 `createdAt DESC`）——<b>支持按 {@code type} 筛选</b>。
     * <p>
     * 管理端按类型分 Tab 展示（信息有误 / 已经下架）；{@code type} 不传 = 全部（含存量 field 记录）。
     *
     * @param status 处理状态（`pending`/`adopted`/`rejected`；可空 = 全部；非法值 `400`）
     * @param type   问题类型（`field`/`gone`；可空 = 全部；非法值 `400`）
     * @param dishId 按目标菜品筛选（可空；从菜品视角看反馈）
     */
    IPage<DishCorrectionAdminVO> listForAdmin(String status, String type, Long dishId, int page, int pageSize);

    /**
     * 待处理的「已经下架」反馈数（管理端「疑似下架」参考值）。
     * <p>
     * ⚠️ <b>仅作参考展示，不是下架阈值</b> —— ≥1 条即进待办，是否下架由管理员人工决定。
     *
     * @return {@code type=gone} 且 {@code status=pending} 的条数
     */
    long countPendingGone();

    /**
     * 待办计数（运营看板）：status=pending 的总数。
     * <p>
     * 等价于 {@code listForAdmin("pending", null, 1, 1).getTotal()}，但免去一次 LIMIT 1 列表查询。
     *
     * @return 待处理条数
     */
    long countPending();

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
    DishCorrectionDetailVO getDetail(Long id);

    /**
     * 采纳纠错（管理端，ADM，两段式档口确认）。
     * <p>
     * 档口解析优先级：请求带 stallId（管理端选定，校验存在）&gt; 提交档口名归一化精确匹配现有档口
     * &gt; createIfMissing=true 按名 upsert 新建；均未命中则<b>不执行采纳</b>，
     * 返回 {@link StallConfirmVO}（needStallConfirm=true + 候选档口列表，HTTP 200）供管理端二次选择。
     * <p>
     * 采纳动作：七字段写回 dish（name/price/档口挂靠/flavorTags/ingredients/images；
     * 可空快照字段不覆盖既有值，保护「菜品首图必填」不变量）→ status=adopted、
     * reply=「已采纳，菜品信息已更新」、handled_at=now → 向可归属提交人投递站内回执。
     * <p>
     * <b>楼层纠错</b>：{@code floor} 不写回 dish（菜品无楼层字段）——本次纠错携带楼层时，
     * 写回上面已解析出的<b>目标档口</b> {@code stall.floor}，同档口下其他菜品一并生效。
     * 档口解析沿用同一优先级（显式 stallId &gt; 提交档口名命中 &gt; createIfMissing 新建）；
     * {@code stallName} 未改动（快照为 null）时目标档口即「该菜当前所属档口」，由管理端在
     * 两段式确认里选定既有档口后落定（既有行为不变）。
     *
     * @return null = 已执行采纳（Controller 返回 Result<Void>）；
     *         非 null = 需要档口确认（未执行任何写操作），Controller 原样下发
     */
    StallConfirmVO adopt(Long id, DishCorrectionAdoptReq req);

    /**
     * 拒绝纠错（管理端，ADM，形态对齐 feedback handle）：status=rejected + reply/rejectReason/handled_at
     * + 站内回执。reply 必填（1~1000 字）；outcome 固定 rejected（其他值 400）；
     * rejectReason 必填（1~200 字，纯空白 → 400「请填写不采纳原因」）。
     */
    void reject(Long id, DishCorrectionHandleReq req);
}
