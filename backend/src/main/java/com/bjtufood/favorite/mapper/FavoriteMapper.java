package com.bjtufood.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.favorite.entity.Favorite;

/**
 * 收藏 Mapper 接口
 * <p>
 * 基础 CRUD + 按 userId 查询收藏列表（需关联菜品表查询菜品信息）
 */
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
