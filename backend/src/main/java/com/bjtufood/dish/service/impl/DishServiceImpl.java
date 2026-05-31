package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.favorite.mapper.FavoriteMapper;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {

    private final DishMapper dishMapper;
    private final ReviewMapper reviewMapper;
    private final FavoriteMapper favoriteMapper;

    @Override
    public IPage<DishVO> listDishes(DishQueryReq req) {
        LambdaQueryWrapper<Dish> wrapper = buildPublicWrapper(req);
        return dishMapper.selectPage(new Page<>(req.getPage(), req.getPageSize()), wrapper).convert(this::toVO);
    }

    @Override
    public List<DishVO> getHotDishes() {
        return dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getStatus, "on")
                        .orderByDesc(Dish::getCollectCount)
                        .last("LIMIT 10"))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public DishVO getDishDetail(Long id, Long userId) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("Dish not found");
        }
        DishVO vo = toVO(dish);
        if (userId != null) {
            vo.setIsFavorited(favoriteMapper.selectCount(new LambdaQueryWrapper<com.bjtufood.favorite.entity.Favorite>()
                    .eq(com.bjtufood.favorite.entity.Favorite::getUserId, userId)
                    .eq(com.bjtufood.favorite.entity.Favorite::getDishId, id)) > 0);
            vo.setHasReviewed(reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                    .eq(Review::getUserId, userId)
                    .eq(Review::getDishId, id)) > 0);
        }
        return vo;
    }

    @Override
    public void addViewCount(Long dishId, Long userId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BusinessException("Dish not found");
        }
        dish.setViewCount((dish.getViewCount() == null ? 0 : dish.getViewCount()) + 1);
        dishMapper.updateById(dish);
    }

    @Override
    public List<Dish> listByStallId(Long stallId) {
        return dishMapper.selectList(new LambdaQueryWrapper<Dish>().eq(Dish::getStallId, stallId));
    }

    @Override
    public void addDish(Long stallId, DishAdminReq req) {
        Dish dish = new Dish();
        applyReq(dish, req);
        dish.setStallId(stallId);
        dish.setAvgRating(BigDecimal.ZERO);
        dish.setRatingCount(0);
        dish.setCollectCount(0);
        dish.setViewCount(0);
        if (!StringUtils.hasText(dish.getStatus())) {
            dish.setStatus("on");
        }
        dishMapper.insert(dish);
    }

    @Override
    public void updateDish(Long id, DishAdminReq req) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException("Dish not found");
        }
        applyReq(dish, req);
        dishMapper.updateById(dish);
    }

    @Override
    public void deleteDish(Long id) {
        dishMapper.deleteById(id);
    }

    @Override
    public void recalcAvgRating(Long dishId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getDishId, dishId)
                .eq(Review::getIsHidden, 0));
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            return;
        }
        dish.setRatingCount(reviews.size());
        BigDecimal avg = reviews.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(reviews.stream().mapToInt(Review::getRating).average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP);
        dish.setAvgRating(avg);
        dishMapper.updateById(dish);
    }

    @Override
    public void syncCollectCount(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            return;
        }
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<com.bjtufood.favorite.entity.Favorite>()
                .eq(com.bjtufood.favorite.entity.Favorite::getDishId, dishId));
        dish.setCollectCount(count.intValue());
        dishMapper.updateById(dish);
    }

    private LambdaQueryWrapper<Dish> buildPublicWrapper(DishQueryReq req) {
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStatus, "on")
                .eq(req.getStallId() != null, Dish::getStallId, req.getStallId())
                .like(StringUtils.hasText(req.getKeyword()), Dish::getName, req.getKeyword())
                .like(StringUtils.hasText(req.getTag()), Dish::getTags, req.getTag())
                .ge(req.getMinPrice() != null, Dish::getPrice, req.getMinPrice())
                .le(req.getMaxPrice() != null, Dish::getPrice, req.getMaxPrice());
        String sortBy = req.getSortBy();
        boolean asc = "asc".equalsIgnoreCase(req.getSortOrder());
        if ("price".equals(sortBy)) {
            wrapper.orderBy(true, asc, Dish::getPrice);
        } else if ("collects".equals(sortBy)) {
            wrapper.orderBy(true, asc, Dish::getCollectCount);
        } else if ("created_at".equals(sortBy)) {
            wrapper.orderBy(true, asc, Dish::getCreatedAt);
        } else {
            wrapper.orderBy(true, asc, Dish::getAvgRating);
        }
        return wrapper;
    }

    private void applyReq(Dish dish, DishAdminReq req) {
        dish.setName(req.getName());
        dish.setPrice(req.getPrice());
        dish.setDescription(req.getDescription());
        dish.setImage(req.getImage());
        dish.setTags(req.getTags());
        dish.setStatus(req.getStatus());
    }

    private DishVO toVO(Dish dish) {
        DishVO vo = new DishVO();
        vo.setId(dish.getId());
        vo.setName(dish.getName());
        vo.setPrice(dish.getPrice());
        vo.setDescription(dish.getDescription());
        vo.setImage(dish.getImage());
        vo.setTags(dish.getTags());
        vo.setStallId(dish.getStallId());
        vo.setAvgRating(dish.getAvgRating());
        vo.setRatingCount(dish.getRatingCount());
        vo.setCollectCount(dish.getCollectCount());
        vo.setViewCount(dish.getViewCount());
        vo.setStatus(dish.getStatus());
        vo.setCreatedAt(dish.getCreatedAt());
        return vo;
    }
}
