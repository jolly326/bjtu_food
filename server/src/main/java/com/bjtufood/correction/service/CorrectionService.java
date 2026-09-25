package com.bjtufood.correction.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionAdminVO;
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
     * 校验：菜品须存在且上架（否则 4001）；name 必填 ≤64 字且敏感词命中即 400
     * （写回字段不放行替换版）；price 必填整数 &gt;0（分）；canteenName/stallName 必填 ≤64 字；
     * images ≤9 张且逐项 COS 白名单校验（安检转存发生在上传时）。
     *
     * @param userId 提交人用户ID（游客为 null）
     * @param dishId 目标菜品ID（路径参数）
     */
    void submit(Long userId, Long dishId, DishCorrectionReq req);

    /**
     * 纠错列表（管理端，ADM）：分页，status 筛选（pending/adopted/rejected，不传 = 全部）。
     * VO 补齐 dishName（实时回查 dish，含已下架/已删除兜底）与提交人昵称。
     */
    IPage<DishCorrectionAdminVO> listForAdmin(String status, int page, int pageSize);

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
