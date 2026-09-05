package com.bjtufood.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.moment.entity.MomentComment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 动态评论 Mapper 接口
 */
public interface MomentCommentMapper extends BaseMapper<MomentComment> {

    /**
     * 「有用」计数原子增减（并发安全；GREATEST 保证不为负）
     */
    @Update("UPDATE moment_comment SET useful_count = GREATEST(useful_count + #{delta}, 0) WHERE id = #{id}")
    int changeUsefulCount(@Param("id") Long id, @Param("delta") int delta);
}
