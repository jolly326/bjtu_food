package com.bjtufood.banner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.banner.entity.Banner;

/**
 * 首页轮播图 Mapper 接口
 * <p>
 * 继承 BaseMapper，基础 CRUD 自动可用；公开查询在 Service 层用 MyBatis-Plus Wrapper 完成
 * （仅 {@code status='on'} + {@code ORDER BY sort_order ASC}，无需自定义 XML）。
 */
public interface BannerMapper extends BaseMapper<Banner> {
}
