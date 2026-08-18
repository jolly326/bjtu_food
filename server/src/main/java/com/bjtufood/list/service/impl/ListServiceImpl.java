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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
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
    @Transactional(rollbackFor = Exception.class)
    public Long createList(Long userId, ListCreateReq req) {
        ItemList list = new ItemList();
        list.setUserId(userId);
        list.setName(req.getName());
        list.setDescription(req.getDescription());
        list.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        itemListMapper.insert(list);
        List<Long> dishIds = req.getDishIds().stream().distinct().toList();
        // 批量插入清单项，避免逐条 insert 的多次往返
        if (!dishIds.isEmpty()) {
            List<ListItem> items = new ArrayList<>(dishIds.size());
            for (Long dishId : dishIds) {
                ListItem item = new ListItem();
                item.setListId(list.getId());
                item.setDishId(dishId);
                items.add(item);
            }
            items.forEach(listItemMapper::insert);
        }
        return list.getId();
    }

    @Override
    public List<ListVO> listByUserId(Long userId) {
        List<ItemList> lists = itemListMapper.selectList(new LambdaQueryWrapper<ItemList>()
                .eq(ItemList::getUserId, userId)
                .orderByDesc(ItemList::getCreatedAt));
        // 批量查询各清单菜品数，避免逐清单 selectCount 的 N+1
        List<Long> listIds = lists.stream().map(ItemList::getId).toList();
        Map<Long, Integer> countMap = new HashMap<>();
        if (!listIds.isEmpty()) {
            listItemMapper.selectList(new LambdaQueryWrapper<ListItem>().in(ListItem::getListId, listIds))
                    .forEach(item -> countMap.merge(item.getListId(), 1, Integer::sum));
        }
        return lists.stream().map(l -> toVO(l, countMap)).toList();
    }

    @Override
    public ListDetailVO getDetail(Long id, Long userId) {
        ItemList list = itemListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException("List not found");
        }
        // 越权防护：仅清单归属人可查看他人之外访问不到详情（与 deleteList 同口径）
        if (!list.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看该清单");
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
    @Deprecated
    public Map<String, Integer> collectAll(Long listId, Long userId) {
        // favorite 模块已在 task-12.12 移除，收藏（👍 like 体系）存储待架构师重设计。
        // 此处保留接口契约，返回空结果，不再维护收藏数，避免悬空依赖。
        return Map.of("succeeded", 0, "skipped", 0);
    }

    private ListVO toVO(ItemList list, Map<Long, Integer> countMap) {
        ListVO vo = new ListVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setShareToken(list.getShareToken());
        vo.setCreatedAt(list.getCreatedAt());
        vo.setDishCount(countMap.getOrDefault(list.getId(), 0));
        return vo;
    }

    private ListDetailVO toDetailVO(ItemList list) {
        ListDetailVO vo = new ListDetailVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setShareToken(list.getShareToken());
        vo.setCreatedAt(list.getCreatedAt());
        List<ListItem> items = listItemMapper.selectList(new LambdaQueryWrapper<ListItem>()
                .eq(ListItem::getListId, list.getId()));
        List<Long> dishIds = items.stream().map(ListItem::getDishId).filter(id -> id != null).distinct().toList();
        // 批量查菜品 + 档口名，消除逐条 selectById 的 N+1
        Map<Long, Dish> dishMap = new HashMap<>();
        if (!dishIds.isEmpty()) {
            dishMapper.selectList(new LambdaQueryWrapper<Dish>().in(Dish::getId, dishIds))
                    .forEach(d -> dishMap.put(d.getId(), d));
        }
        List<Long> stallIds = dishMap.values().stream()
                .map(Dish::getStallId).filter(id -> id != null).distinct().toList();
        Map<Long, Stall> stallMap = new HashMap<>();
        if (!stallIds.isEmpty()) {
            stallMapper.selectList(new LambdaQueryWrapper<Stall>().in(Stall::getId, stallIds))
                    .forEach(s -> stallMap.put(s.getId(), s));
        }
        List<ListDetailVO.DishItem> dishes = items.stream()
                .map(ListItem::getDishId)
                .map(id -> toDishItem(id, dishMap, stallMap))
                .filter(item -> item != null)
                .toList();
        vo.setDishes(dishes);
        return vo;
    }

    private ListDetailVO.DishItem toDishItem(Long dishId, Map<Long, Dish> dishMap, Map<Long, Stall> stallMap) {
        Dish dish = dishMap.get(dishId);
        if (dish == null || !"on".equals(dish.getStatus())) {
            return null;
        }
        Stall stall = dish.getStallId() == null ? null : stallMap.get(dish.getStallId());
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
