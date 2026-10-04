package com.bjtufood.review.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.review.dto.MyReviewVO;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReviewAdminVO;
import com.bjtufood.review.dto.ReviewRelatedBriefVO;

/**
 * 评价服务接口
 * <p>
 * 评价的提交、重新评价（覆盖式）、删除，和管理端的事后处置。
 * 评价发生变更后通过 Spring 事件通知 dish 模块异步重算评分聚合。
 */
public interface ReviewService {

    // ==================== 公开接口 ====================

    /**
     * 评价是否存在且公开可见（{@code is_hidden=0}）——跨域只读契约。
     * <p>
     * 供 feedback 域「举报目标校验」等消费：已隐藏 / 已删除的评价对外不可见，
     * 故不可被举报（等价于不存在）。调用方 SHALL NOT 直连评价表。
     *
     * @param id 评价ID（可空）
     * @return true=存在且未隐藏
     */
    boolean existsVisibleById(Long id);

    /**
     * 获取菜品评价列表
     * <p>
     * 只返回 is_hidden=0 的评价；排序唯一为发表时间倒序（created_at DESC），不提供排序参数。
     *
     * @param dishId   菜品ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页评价列表
     */
    IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize);

    /**
     * 获取当前用户的评价列表（我的评价）
     * <p>
     * 对评价全集按用户过滤拆分：只返回该用户本人、且未被隐藏（is_hidden=0）的评价，按发表时间倒序；
     * 被管理员隐藏的评价不对客户端（含作者本人）返回。返回项含 {@code dishId} / {@code dishName}。
     *
     * @param userId   当前登录用户ID
     * @param page     页码
     * @param pageSize 每页条数
     * @param dishId   菜品ID（可选，仅返回对该菜品的评价，用于详情页判定「我是否已评价」）
     * @return 分页评价列表
     */
    IPage<MyReviewVO> listByUserId(Long userId, int page, int pageSize, Long dishId);

    // ==================== 需登录接口（学生） ====================

    /**
     * 提交评价
     * <p>
     * 处理流程：
     * 1. 校验菜品是否存在且上架
     * 2. 校验是否已评价过该菜（每人每菜只能评价一次）
     * 3. 敏感词过滤（调用 LocalSensitiveFilter）
     * 4. 保存评价到数据库
     * 5. 发布 ReviewSubmittedEvent（触发评分重算）
     *
     * @param userId 当前用户ID
     * @param dishId 被评价菜品ID（归属由端点路径锁定）
     * @param req    评价内容
     * @return 评价ID
     * @throws com.bjtufood.common.exception.BusinessException 已评价/菜品不存在
     */
    Long submitReview(Long userId, Long dishId, ReviewReq req);

    /**
     * 删除自己的评价
     *
     * @param id     评价ID
     * @param userId 当前用户ID
     * @throws com.bjtufood.common.exception.BusinessException 评价不存在/无权限
     */
    void deleteReview(Long id, Long userId);

    // ==================== 管理端接口（系统管理员） ====================

    /**
     * 查询所有评价列表（管理端用）
     * <p>
     * 不排除已隐藏，敏感词高亮标记。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param hidden   是否隐藏（**boolean**；不传 = 全部。B1 口径：原 `isHidden`(0/1) 已改为布尔）
     * @param dishId   按菜品筛选（可选；从菜品视角看评价）
     * @param userId   提交用户ID（可选）
     * @param keyword  评价正文关键词（可选，模糊匹配）
     * @return 分页评价列表（排序 `createdAt DESC`）
     */
    IPage<ReviewAdminVO> listAllForAdmin(int page, int pageSize, Boolean hidden, Long dishId, Long userId,
                                         String keyword);

    /**
     * 设置评价隐藏状态（显式，非 toggle）。
     * <p>
     * 隐藏时向作者投递站内回执（口径见 docs/func/web/B-UGC治理/B1-评价管理.md）：
     * 有附注 ⇒ 正文「你的评价已被管理员隐藏。说明：&lt;note&gt;」；无附注 ⇒ 固定文案。
     * 投递判据 = `userId &gt; 0`（静默登录的游客同样收到；匿名无归属不投递）。已隐藏再隐藏视为幂等改附注。
     *
     * @param id     评价ID
     * @param hidden true=隐藏 false=显示（恢复显示时清空附注）
     * @param note   隐藏附注（可选，≤200 字；仅 hidden=true 时有意义）
     */
    void setHidden(Long id, boolean hidden, String note);

    /**
     * 管理员删除评价
     *
     * @param id 评价ID
     */
    void deleteByAdmin(Long id);

    /**
     * **批量**关联评价摘要（B3：举报列表内嵌「被举报内容 + 所属菜品 + 是否已隐藏」）。
     * <p>
     * 只选 4 列（`id` / `dish_id` / `content` / `is_hidden`），菜品名经 dish 域只读契约**批量**补齐 ——
     * 逐行取会退化成 N+1。评价不存在则不入图（端上按「评价已删除」展示）。
     *
     * @param reviewIds 被举报评价 ID（空集合返回空 Map，不发查询）
     * @return reviewId → 摘要
     */
    java.util.Map<Long, ReviewRelatedBriefVO> mapRelatedBriefByIds(java.util.Collection<Long> reviewIds);

    /**
     * **联动隐藏**（B3）：处置举报时顺带隐藏被举报评价。
     * <p>
     * 与 {@link #setHidden} 的差别：本方法是**幂等且自解释**的 —— 评价不存在或**已被隐藏**时
     * 返回 {@code false}（不重复投递回执、不覆盖既有附注），只有「这次真的隐藏了」才返回
     * {@code true}，供调用方在处置回执里写明「已隐藏该评价」。
     *
     * @param reviewId 被举报评价 ID
     * @return 本次是否真的执行了隐藏
     */
    boolean hideIfVisible(Long reviewId);

    /**
     * 评价总数（D1 看板概况：**含已隐藏** —— 概况回答的是「平台规模」，不是「可见量」）。
     *
     * @return 评价总数
     */
    long countAll();

    // ==================== 跨域写契约（P0-1：由本域 event 监听器消费，不对外暴露给业务域） ====================

    /**
     * 级联清理某菜品的全部评价（BE-108）。
     * <p>
     * 调用方 = {@code review.event.ReviewDishCascadeListener}（订阅 dish 域发布的
     * {@code DishDeletedEvent}）。原先由 {@code DishServiceImpl} 直接注入 ReviewMapper 硬删，
     * 属跨域写他域表；改为事件后 dish 域不再持有 review 的表知识。
     *
     * @param dishId 菜品ID
     * @return 删除条数（供日志）
     */
    int deleteByDishId(Long dishId);

    /**
     * 账号归属迁移：把 fromUserId 的评价改挂到 toUserId。
     * <p>
     * 调用方 = {@code review.event.ReviewOwnershipListener}（订阅 auth 域发布的
     * {@code UserOwnershipMigratedEvent}）。唯一键 {@code uk_review_user_dish} 要求
     * <b>先删冲突行（to 已评价过的同菜品，保留 to 的记录）再改归属</b>，该顺序知识属 review 域，
     * 故原先散落在 auth 侧的两条 UPDATE 一并收敛到此。
     *
     * @param fromUserId 迁出账号ID
     * @param toUserId   迁入账号ID
     * @return 改挂条数（供日志；冲突删除条数计入日志不在此返回）
     */
    int migrateOwnership(Long fromUserId, Long toUserId);
}
