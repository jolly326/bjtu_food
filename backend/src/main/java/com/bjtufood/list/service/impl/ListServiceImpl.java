package com.bjtufood.list.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.favorite.service.FavoriteService;
import com.bjtufood.list.dto.ListCreateReq;
import com.bjtufood.list.dto.ListDetailVO;
import com.bjtufood.list.dto.ListVO;
import com.bjtufood.list.entity.ItemList;
import com.bjtufood.list.entity.ListItem;
import com.bjtufood.list.mapper.ItemListMapper;
import com.bjtufood.list.mapper.ListItemMapper;
import com.bjtufood.list.service.ListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListServiceImpl implements ListService {

    private final ItemListMapper itemListMapper;
    private final ListItemMapper listItemMapper;
    private final FavoriteService favoriteService;

    @Override
    public Long createList(Long userId, ListCreateReq req) {
        ItemList list = new ItemList();
        list.setUserId(userId);
        list.setName(req.getName());
        list.setDescription(req.getDescription());
        list.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        itemListMapper.insert(list);
        for (Long dishId : req.getDishIds()) {
            ListItem item = new ListItem();
            item.setListId(list.getId());
            item.setDishId(dishId);
            listItemMapper.insert(item);
        }
        return list.getId();
    }

    @Override
    public List<ListVO> listByUserId(Long userId) {
        return itemListMapper.selectList(new LambdaQueryWrapper<ItemList>()
                        .eq(ItemList::getUserId, userId)
                        .orderByDesc(ItemList::getCreatedAt))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public ListDetailVO getDetail(Long id) {
        ItemList list = itemListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException("List not found");
        }
        return toDetailVO(list);
    }

    @Override
    public ListDetailVO getByShareToken(String shareToken) {
        ItemList list = itemListMapper.selectOne(new LambdaQueryWrapper<ItemList>().eq(ItemList::getShareToken, shareToken));
        if (list == null) {
            throw new BusinessException("List not found");
        }
        return toDetailVO(list);
    }

    @Override
    public void deleteList(Long id, Long userId) {
        ItemList list = itemListMapper.selectById(id);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new BusinessException("List not found");
        }
        itemListMapper.deleteById(id);
    }

    @Override
    public Map<String, Integer> collectAll(Long listId, Long userId) {
        List<Long> dishIds = listItemMapper.selectList(new LambdaQueryWrapper<ListItem>().eq(ListItem::getListId, listId))
                .stream()
                .map(ListItem::getDishId)
                .toList();
        return favoriteService.batchCollect(userId, dishIds);
    }

    private ListVO toVO(ItemList list) {
        ListVO vo = new ListVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setShareToken(list.getShareToken());
        vo.setCreatedAt(list.getCreatedAt());
        vo.setDishCount(listItemMapper.selectCount(new LambdaQueryWrapper<ListItem>().eq(ListItem::getListId, list.getId())).intValue());
        return vo;
    }

    private ListDetailVO toDetailVO(ItemList list) {
        ListDetailVO vo = new ListDetailVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setShareToken(list.getShareToken());
        vo.setCreatedAt(list.getCreatedAt());
        vo.setDishes(List.of());
        return vo;
    }
}
