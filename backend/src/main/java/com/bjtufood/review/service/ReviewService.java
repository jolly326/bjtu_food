package com.bjtufood.review.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.review.dto.ReviewReq;

import java.math.BigDecimal;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReviewAdminVO;
import com.bjtufood.review.dto.UsefulResult;

/**
 * 评价服务接口
 * <p>
 * 评价的提交、编辑、删除，和管理端的审核操作。
 * 提交评价后通过 Spring 事件通知 dish 模块更新评分。
 */
public interface ReviewService {

    // ==================== 公开接口 ====================

    /**
     * 获取菜品评价列表
     * <p>
     * 只返回 is_hidden=0 的评价；支持按最新（created_at）/「有用」数（useful_count）排序。
     * 若传入 userId（登录态），会回写每条评价 useful 标记（当前用户是否已标记「有用」），公开列表可传 null。
     *
     * @param dishId   菜品ID
     * @param page     页码
     * @param pageSize 每页条数
     * @param sort     排序：latest（默认）/ useful
     * @param userId   当前登录用户ID（可空，用于回写 useful 标记）
     * @return 分页评价列表
     */
    IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize, String sort, Long userId);

    /**
     * 获取档口评价列表
     * <p>
     * review 表仅关联 dish_id，档口维度评价通过 review → dish(stall_id) 推导。
     * 只返回 is_hidden=0 的评价；支持按最新（created_at）/「有用」数（useful_count）排序。
     *
     * @param stallId  档口ID
     * @param page     页码
     * @param pageSize 每页条数
     * @param sort     排序：latest（默认）/ useful
     * @param userId   当前登录用户ID（可空，用于回写 useful 标记）
     * @return 分页评价列表
     */
    IPage<ReviewVO> listByStallId(Long stallId, int page, int pageSize, String sort, Long userId);

    /**
     * 获取食堂评价列表
     * <p>
     * 通过 review → dish(stall_id) → stall(canteen_id) 推导。
     * 只返回 is_hidden=0 的评价；支持按最新/「有用」排序。
     *
     * @param canteenId 食堂ID
     * @param page      页码
     * @param pageSize  每页条数
     * @param sort      排序：latest（默认）/ useful
     * @param userId    当前登录用户ID（可空，用于回写 useful 标记）
     * @return 分页评价列表
     */
    IPage<ReviewVO> listByCanteenId(Long canteenId, int page, int pageSize, String sort, Long userId);

    /**
     * 计算某档口的平均评分（星级 1-5，取该档口下所有菜品评价的平均值）
     *
     * @param stallId 档口ID
     * @return 平均分（BigDecimal，保留两位），无评价返回 0.00
     */
    BigDecimal getAvgRatingByStallId(Long stallId);

    /**
     * 获取当前登录用户自己的评价列表
     * <p>
     * 按创建时间降序排列，只返回未隐藏的评价（is_hidden=0）。
     * 用于个人中心「我的评价」页。
     *
     * @param userId   当前用户ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页评价列表
     */
    IPage<ReviewVO> listByUserId(Long userId, int page, int pageSize);

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
     * @param req    评价内容
     * @return 评价ID
     * @throws com.bjtufood.common.exception.BusinessException 已评价/菜品不存在
     */
    Long submitReview(Long userId, ReviewReq req);

    /**
     * 修改自己的评价
     * <p>
     * 可修改评分和内容，不允许修改图片
     *
     * @param id      评价ID
     * @param userId  当前用户ID
     * @param rating  新评分
     * @param content 新内容
     * @throws com.bjtufood.common.exception.BusinessException 评价不存在/无权限
     */
    void updateReview(Long id, Long userId, Integer rating, String content);

    /**
     * 删除自己的评价（软删除）
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
     * 不排除已删除/已隐藏，敏感词高亮标记
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param isHidden 是否隐藏（可选）
     * @param isDeleted 兼容旧接口参数，现有数据库未使用
     * @return 分页评价列表
     */
    IPage<ReviewAdminVO> listAllForAdmin(int page, int pageSize, Integer isHidden, Integer isDeleted);

    /**
     * 切换隐藏/显示评价
     *
     * @param id 评价ID
     */
    void toggleHide(Long id);

    /**
     * 管理员删除评价（软删除）
     *
     * @param id 评价ID
     */
    void deleteByAdmin(Long id);

    // ==================== 互动接口 ====================

    /**
     * 评价「有用」切换（幂等）
     * <p>
     * 未标记 → 插入 review_useful 记录并 useful_count+1，返回 useful=true；
     * 已标记 → 删除记录并 useful_count-1，返回 useful=false。重复点击即取消，不抛错。
     *
     * @param userId   当前登录用户ID
     * @param reviewId 评价ID
     * @return UsefulResult{useful, usefulCount}
     * @throws com.bjtufood.common.exception.BusinessException 评价不存在
     */
    UsefulResult toggleUseful(Long userId, Long reviewId);

    /**
     * @deprecated 一期起废弃，请使用 {@link #toggleUseful(Long, Long)}。
     * 原「喜欢」单向投票语义与「切赞/取消」需求不符。
     */
    @Deprecated
    void likeReview(Long userId, Long reviewId);
}
