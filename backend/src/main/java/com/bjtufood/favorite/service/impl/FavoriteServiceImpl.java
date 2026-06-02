package com.bjtufood.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.favorite.entity.Favorite;
import com.bjtufood.favorite.event.FavoriteChangedEvent;
import com.bjtufood.favorite.mapper.FavoriteMapper;
import com.bjtufood.favorite.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;

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
    public IPage<DishVO> listFavoriteDishes(Long userId, int page, int pageSize) {
        return favoriteMapper.selectFavoriteDishes(new Page<>(page, pageSize), userId)
                .convert(this::enrichImages);
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
        Map<Long, Boolean> result = new java.util.HashMap<>();
        for (Long dishId : dishIds) {
            result.put(dishId, isFavorited(userId, dishId));
        }
        return result;
    }

    /**
     * 从 images_json 解析图片列表。
     */
    private DishVO enrichImages(DishVO vo) {
        if (vo == null) return null;
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(vo.getImagesJson()));
        return vo;
    }

    private LambdaQueryWrapper<Favorite> query(Long userId, Long dishId) {
        return new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getDishId, dishId);
    }
}
