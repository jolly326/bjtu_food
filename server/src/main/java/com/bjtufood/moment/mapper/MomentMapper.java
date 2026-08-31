package com.bjtufood.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.moment.dto.MomentVO;
import com.bjtufood.moment.entity.Moment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 社区动态 Mapper 接口
 */
public interface MomentMapper extends BaseMapper<Moment> {

    /**
     * 公开广场列表（仅 approved + status=0），支持 dishId/stallId/canteenId 关联过滤，按 created_at desc
     */
    IPage<MomentVO> selectPublicPage(Page<?> page,
                                     @Param("dishId") Long dishId,
                                     @Param("stallId") Long stallId,
                                     @Param("canteenId") Long canteenId);

    /**
     * 公开广场列表「最热」排序（仅 approved + status=0），支持关联过滤。
     * 排序 (useful_count*2 + comment_count) DESC, created_at DESC（R2）。
     */
    IPage<MomentVO> selectPublicPageHot(Page<?> page,
                                        @Param("dishId") Long dishId,
                                        @Param("stallId") Long stallId,
                                        @Param("canteenId") Long canteenId);

    /**
     * 排行榜裸 List（R3）：仅 approved + status=0，按 R2 公式取前 limit。
     * dishId/stallId/canteenId 关联过滤同样生效。
     */
    List<MomentVO> selectRanking(@Param("limit") int limit,
                                 @Param("dishId") Long dishId,
                                 @Param("stallId") Long stallId,
                                 @Param("canteenId") Long canteenId);

    /**
     * 「我的动态」列表（按当前用户 + 可选审核态过滤）
     */
    List<MomentVO> selectMyMoments(@Param("userId") Long userId,
                                   @Param("auditStatus") String auditStatus);

    /**
     * 「有用」计数原子增减（并发安全：SET useful_count = useful_count ± delta，最小值 0）
     */
    int changeUsefulCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 评论数原子增减（并发安全：SET comment_count = comment_count + delta，最小值 0）
     *
     * @param id    动态ID
     * @param delta +1 新增评论 / -1 删除评论（可为负数批量）
     */
    int changeCommentCount(@Param("id") Long id, @Param("delta") int delta);
}
