package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CanteenServiceImpl implements CanteenService {

    private final CanteenMapper canteenMapper;
    private final StallMapper stallMapper;

    @Override
    public List<Canteen> listWithStalls() {
        return canteenMapper.selectList(new LambdaQueryWrapper<Canteen>().orderByAsc(Canteen::getSortOrder));
    }

    @Override
    public void add(Canteen canteen) {
        canteenMapper.insert(canteen);
    }

    @Override
    public void update(Canteen canteen) {
        if (canteen.getId() == null || canteenMapper.updateById(canteen) == 0) {
            throw new BusinessException("Canteen not found");
        }
    }

    @Override
    public void delete(Long id) {
        Long count = stallMapper.selectCount(new LambdaQueryWrapper<Stall>().eq(Stall::getCanteenId, id));
        if (count > 0) {
            throw new BusinessException("Canteen still has stalls");
        }
        canteenMapper.deleteById(id);
    }
}
