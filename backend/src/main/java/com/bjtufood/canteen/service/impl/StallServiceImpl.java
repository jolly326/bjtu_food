package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StallServiceImpl implements StallService {

    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final DishMapper dishMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public List<StallDetailVO> listByCanteenId(Long canteenId) {
        return stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getCanteenId, canteenId)
                .eq(Stall::getStatus, "open")
                .orderByAsc(Stall::getSortOrder))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public void add(Stall stall) {
        if (canteenMapper.selectById(stall.getCanteenId()) == null) {
            throw new BusinessException("Canteen not found");
        }
        stallMapper.insert(stall);
    }

    @Override
    public void update(Stall stall) {
        if (stall.getId() == null || stallMapper.updateById(stall) == 0) {
            throw new BusinessException("Stall not found");
        }
    }

    @Override
    public void delete(Long id) {
        Long count = dishMapper.selectCount(new LambdaQueryWrapper<Dish>().eq(Dish::getStallId, id));
        if (count > 0) {
            throw new BusinessException("Stall still has dishes");
        }
        stallMapper.deleteById(id);
    }

    @Override
    public Stall getById(Long id) {
        Stall stall = stallMapper.selectById(id);
        if (stall == null) {
            throw new BusinessException("Stall not found");
        }
        return stall;
    }

    private StallDetailVO toVO(Stall stall) {
        StallDetailVO vo = new StallDetailVO();
        vo.setId(stall.getId());
        vo.setName(stall.getName());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        vo.setLocation(stall.getLocation());
        vo.setDescription(stall.getDescription());
        return vo;
    }
}
