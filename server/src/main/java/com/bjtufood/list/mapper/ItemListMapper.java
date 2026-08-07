package com.bjtufood.list.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.list.entity.ItemList;

/**
 * 美食清单 Mapper 接口
 * <p>
 * 基础 CRUD + 按用户ID查询清单列表 + 按分享token查询
 */
public interface ItemListMapper extends BaseMapper<ItemList> {
}
