package com.bjtufood.list.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.StallMapper;
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
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public Long createList(Long userId, ListCreateReq req) {
        ItemList list = new ItemList();
        list.setUserId(userId);
        list.setName(req.getName());
        list.setDescription(req.getDescription());
        list.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        itemListMapper.insert(list);
        List<Long> dishIds = req.getDishIds().stream().distinct().toList();
        for (Long dishId : dishIds) {
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
        // favorite 模块已在 task-12.12 移除，收藏（👍 like 体系）存储待架构师重设计。
        // 此处保留接口契约，返回空结果，不再维护收藏数，避免悬空依赖。
        return Map.of("succeeded", 0, "skipped", 0);
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
        List<ListDetailVO.DishItem> dishes = listItemMapper.selectList(new LambdaQueryWrapper<ListItem>()
                        .eq(ListItem::getListId, list.getId()))
                .stream()
                .map(ListItem::getDishId)
                .map(this::toDishItem)
                .filter(item -> item != null)
                .toList();
        vo.setDishes(dishes);
        return vo;
    }

    private ListDetailVO.DishItem toDishItem(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || !"on".equals(dish.getStatus())) {
            return null;
        }
        Stall stall = stallMapper.selectById(dish.getStallId());
        ListDetailVO.DishItem item = new ListDetailVO.DishItem();
        item.setId(dish.getId());
        item.setName(dish.getName());
        item.setPrice(dish.getPrice());
        item.setImages(imageUrlUtil.parseAndToAbsoluteUrls(dish.getImages()));
        item.setAvgRating(dish.getAvgRating());
        item.setStallName(stall == null ? null : stall.getName());
        return item;
    }
}
