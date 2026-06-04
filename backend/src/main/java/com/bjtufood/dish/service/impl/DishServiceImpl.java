package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.dto.RatingDistributionVO;
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
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DishServiceImpl implements DishService {

    private final DishMapper dishMapper;
    private final ReviewMapper reviewMapper;
    private final FavoriteMapper favoriteMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public IPage<DishVO> listDishes(DishQueryReq req) {
        if (req == null) {
            req = new DishQueryReq();
        }
        if (req.getPage() == null || req.getPage() < 1) {
            req.setPage(1);
        }
        if (req.getPageSize() == null || req.getPageSize() < 1) {
            req.setPageSize(10);
        }
        return dishMapper.selectDishPage(new Page<>(req.getPage(), req.getPageSize()), req)
                .convert(this::enrichImages);
    }

    @Override
    public List<DishVO> getHotDishes() {
        return dishMapper.selectHotDishes()
                .stream()
                .map(this::enrichImages)
                .toList();
    }

    @Override
    public DishVO getDishDetail(Long id, Long userId) {
        DishDetailVO vo = dishMapper.selectDishDetail(id);
        if (vo == null) {
            throw new BusinessException("菜品不存在");
        }

        // 从 images_json 解析 images（避免二次查数据库）
        enrichImages(vo);

        // 查询评分分布
        List<RatingDistributionVO> distribution = dishMapper.selectRatingDistribution(id);
        vo.setRatingDistribution(fillRatingDistribution(distribution));

        // 当前用户状态
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
            throw new BusinessException("菜品不存在");
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
            throw new BusinessException("菜品不存在");
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

    private void applyReq(Dish dish, DishAdminReq req) {
        dish.setName(req.getName());
        dish.setPrice(req.getPrice());
        dish.setDescription(req.getDescription());
        dish.setImages(JsonListUtil.toJson(req.getImages()));
        dish.setTags(req.getTags());
        dish.setStatus(req.getStatus());
    }

    /**
     * 将数据库原始的 imagesJson 解析为 List{@literal <String>} 并回填到 images
     */
    private DishVO enrichImages(DishVO vo) {
        if (vo == null) {
            return null;
        }
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(vo.getImagesJson()));
        return vo;
    }

    /**
     * 补齐 1-5 星评分分布，缺失的星级补 0
     */
    private List<RatingDistributionVO> fillRatingDistribution(List<RatingDistributionVO> distribution) {
        List<RatingDistributionVO> result = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            long count = 0;
            for (RatingDistributionVO rd : distribution) {
                if (rd.getStar() == star) {
                    count = rd.getCount();
                    break;
                }
            }
            result.add(new RatingDistributionVO(star, count));
        }
        return result;
    }
}
