package com.bjtufood.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.favorite.entity.Favorite;
import com.bjtufood.favorite.event.FavoriteChangedEvent;
import com.bjtufood.favorite.mapper.FavoriteMapper;
import com.bjtufood.favorite.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean toggle(Long userId, Long dishId) {
        Favorite exists = favoriteMapper.selectOne(query(userId, dishId));
        boolean added = exists == null;
        if (added) {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setDishId(dishId);
            favoriteMapper.insert(favorite);
        } else {
            favoriteMapper.deleteById(exists.getId());
        }
        eventPublisher.publishEvent(new FavoriteChangedEvent(this, dishId, added));
        return added;
    }

    @Override
    public IPage<Map<String, Object>> listByUserId(Long userId, int page, int pageSize) {
        return favoriteMapper.selectPage(new Page<>(page, pageSize),
                        new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId).orderByDesc(Favorite::getCreatedAt))
                .convert(favorite -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", favorite.getId());
                    item.put("dishId", favorite.getDishId());
                    item.put("createdAt", favorite.getCreatedAt());
                    return item;
                });
    }

    @Override
    public Map<String, Integer> batchCollect(Long userId, List<Long> dishIds) {
        int succeeded = 0;
        int skipped = 0;
        for (Long dishId : dishIds) {
            if (isFavorited(userId, dishId)) {
                skipped++;
            } else {
                toggle(userId, dishId);
                succeeded++;
            }
        }
        return Map.of("succeeded", succeeded, "skipped", skipped);
    }

    @Override
    public boolean isFavorited(Long userId, Long dishId) {
        return favoriteMapper.selectCount(query(userId, dishId)) > 0;
    }

    @Override
    public Map<Long, Boolean> batchCheckFavorited(Long userId, List<Long> dishIds) {
        Map<Long, Boolean> result = new HashMap<>();
        for (Long dishId : dishIds) {
            result.put(dishId, isFavorited(userId, dishId));
        }
        return result;
    }

    private LambdaQueryWrapper<Favorite> query(Long userId, Long dishId) {
        return new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getDishId, dishId);
    }
}
