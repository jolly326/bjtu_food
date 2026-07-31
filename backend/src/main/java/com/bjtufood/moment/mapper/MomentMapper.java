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
     * 公开广场列表（仅 approved + status=0），支持 dishId/stallId/canteenId 关联过滤
     */
    IPage<MomentVO> selectPublicPage(Page<?> page,
                                     @Param("dishId") Long dishId,
                                     @Param("stallId") Long stallId,
                                     @Param("canteenId") Long canteenId);

    /**
     * 「我的动态」列表（按当前用户 + 可选审核态过滤）
     */
    List<MomentVO> selectMyMoments(@Param("userId") Long userId,
                                   @Param("auditStatus") String auditStatus);
}
