package com.bjtufood.review.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.review.dto.MyReviewVO;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReviewAdminVO;

/**
 * 评价服务接口
 * <p>
 * 评价的提交、重新评价（覆盖式）、删除，和管理端的事后处置。
 * 评价发生变更后通过 Spring 事件通知 dish 模块异步重算评分聚合。
 */
public interface ReviewService {

    // ==================== 公开接口 ====================

    /**
     * 获取菜品评价列表
     * <p>
     * 只返回 is_hidden=0 的评价；排序唯一为发表时间倒序（created_at DESC），不提供排序参数。
     * hasImage=1 时仅返回带图评价（images 非空且不为空数组），total 按该筛选口径统计。
     *
     * @param dishId   菜品ID
     * @param page     页码
     * @param pageSize 每页条数
     * @param hasImage 只看有图：1=仅带图；缺省/其他值=不过滤
     * @return 分页评价列表
     */
    IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize, Integer hasImage);

    /**
     * 获取当前用户的评价列表（我的评价）
     * <p>
     * 只返回该用户本人的评价（不过滤 is_hidden，被隐藏的评价作者仍可见），按发表时间倒序；
     * 返回项含 {@code dishId} / {@code dishName} / {@code isHidden}（作者视角字段）。
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
     * 3. 敏感词过滤（调用 SensitiveFilter）
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
     * 重新评价（覆盖式更新同一条评价）
     * <p>
     * 覆盖评分 / 文字 / 配图，不新建行；发表时间刷新为当前（时间倒序下自然置顶），
     * 隐藏标记重置为未隐藏（0），并发布 ReviewSubmittedEvent 重算评分聚合。
     * 内容安全检测与首次发表同口径（文本送检，违规 400 且原内容不变）；不限次数。
     *
     * @param id     评价ID
     * @param userId 当前用户ID（须为作者本人）
     * @param req    新的评价内容
     * @throws com.bjtufood.common.exception.BusinessException 评价不存在/非作者
     */
    void updateReview(Long id, Long userId, ReviewReq req);

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
     * @param isHidden 是否隐藏（可选）
     * @param userId   提交用户ID（可选）
     * @param keyword  评价正文关键词（可选，模糊匹配）
     * @return 分页评价列表
     */
    IPage<ReviewAdminVO> listAllForAdmin(int page, int pageSize, Integer isHidden, Long userId, String keyword);

    /**
     * 设置评价隐藏状态（显式，非 toggle）
     *
     * @param id     评价ID
     * @param hidden true=隐藏 false=显示
     */
    void setHidden(Long id, boolean hidden);

    /**
     * 管理员删除评价
     *
     * @param id 评价ID
     */
    void deleteByAdmin(Long id);
}
