package com.bjtufood.review.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.entity.Review;

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
     * 只返回 is_hidden=0 AND is_deleted=0 的评价
     * 按创建时间降序排列
     * 每条评价包含评价者的昵称和头像
     *
     * @param dishId   菜品ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页评价列表
     */
    IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize);

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
     * @param isDeleted 是否删除（可选）
     * @return 分页评价列表
     */
    IPage<ReviewVO> listAllForAdmin(int page, int pageSize, Integer isHidden, Integer isDeleted);

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
}
