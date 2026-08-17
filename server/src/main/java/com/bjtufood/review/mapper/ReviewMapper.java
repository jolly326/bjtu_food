package com.bjtufood.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReplyTotalVO;
import com.bjtufood.review.dto.StallAvgRatingDTO;
import com.bjtufood.review.entity.Review;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 评价 Mapper 接口
 * <p>
 * 基础 CRUD + 复杂查询（查询评价列表时需关联用户表获取昵称和头像）
 */
public interface ReviewMapper extends BaseMapper<Review> {

    IPage<ReviewVO> selectReviewPageByDishId(Page<?> page, @Param("dishId") Long dishId, @Param("sort") String sort, @Param("withImage") boolean withImage);

    IPage<ReviewVO> selectReviewPageByUserId(Page<?> page, @Param("userId") Long userId, @Param("sort") String sort);

    /**
     * 按档口查询评价列表。
     * <p>
     * review 表仅关联 dish_id（无 stall_id / canteen_id），档口/食堂维度的评价通过
     * review → dish(stall_id) 推导。只返回 is_hidden=0 的评价。
     */
    IPage<ReviewVO> selectReviewPageByStallId(Page<?> page, @Param("stallId") Long stallId, @Param("sort") String sort, @Param("withImage") boolean withImage);

    /**
     * 按食堂查询评价列表。
     * <p>
     * 通过 review → dish(stall_id) → stall(canteen_id) 推导。只返回 is_hidden=0 的评价。
     */
    IPage<ReviewVO> selectReviewPageByCanteenId(Page<?> page, @Param("canteenId") Long canteenId, @Param("sort") String sort, @Param("withImage") boolean withImage);

    /**
     * 计算某档口下所有菜品评价的平均分（星级 1-5）。
     * <p>
     * 通过 review → dish(stall_id) 推导；无评价返回 NULL。
     *
     * @param stallId 档口ID
     * @return 平均分，可能为 null
     */
    BigDecimal selectAvgRatingByStallId(@Param("stallId") Long stallId);

    /**
     * 批量计算多个档口下所有菜品评价的平均分（星级 1-5）。
     * <p>
     * 用于消除逐档口 N+1 查询：一次查询返回 stall_id → avg(rating) 的映射。无评价的档口不会出现在结果中。
     *
     * @param stallIds 档口ID集合
     * @return 每行含 stallId、avgRating（可能为 null）
     */
    List<StallAvgRatingDTO> selectAvgRatingByStallIds(@Param("stallIds") Collection<Long> stallIds);

    /**
     * 评价「有用」计数原子增减（并发安全：SET useful_count = useful_count ± delta，最小值 0）
     *
     * @param id    评价ID
     * @param delta +1 标记 / -1 取消
     * @return 影响行数
     */
    int changeUsefulCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 根据用户ID查昵称（回复时冗余存储被回复者昵称用）
     *
     * @param userId 用户ID
     * @return 昵称，用户不存在返回 null
     */
    String selectNicknameByUserId(@Param("userId") Long userId);

    /**
     * 批量查楼中楼子回复（按父评价ID列表），每个父只取最近 limit 条（窗口函数），按 created_at 升序
     *
     * @param parentIds 父评价ID集合
     * @param limit     每个父最多返回的子回复条数
     * @return 子回复列表（含用户昵称头像）
     */
    List<ReviewVO> selectRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds, @Param("limit") int limit);

    /**
     * 批量统计各父评价的子回复总数（判断窗口限制后是否还有更多）
     *
     * @param parentIds 父评价ID集合
     * @return parentId → 子回复总数
     */
    List<ReplyTotalVO> selectReplyTotalByParentIds(@Param("parentIds") Collection<Long> parentIds);

    /**
     * 批量查某层子回复的 id（仅 id，性能最优，供删除时 BFS 收集后代用）
     *
     * @param parentIds 父评价ID集合
     * @return 该层子回复 id 列表
     */
    List<Long> selectReplyIdsByParentIds(@Param("parentIds") Collection<Long> parentIds);

    /**
     * 分页查某父评价的直接子回复（「查看全部回复」展开用，普通分页、按 created_at 升序）
     *
     * @param page     分页对象（MyBatis-Plus 分页插件）
     * @param parentId 父评价ID
     * @return 分页子回复列表
     */
    IPage<ReviewVO> selectRepliesPageByParentId(Page<ReviewVO> page, @Param("parentId") Long parentId);
}
