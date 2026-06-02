package com.bjtufood.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.favorite.entity.Favorite;
import org.apache.ibatis.annotations.Param;

/**
 * 收藏 Mapper 接口
 * <p>
 * 基础 CRUD + 按 userId 查询收藏列表（需关联菜品表查询菜品信息）
 */
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 查询用户收藏的菜品列表（联表查询完整菜品信息）
     *
     * @param page   分页参数
     * @param userId 用户ID
     * @return 分页收藏菜品列表
     */
    IPage<DishVO> selectFavoriteDishes(Page<?> page, @Param("userId") Long userId);
}
