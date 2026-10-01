package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 食堂服务实现
 * <p>
 * （K4）：公开侧食堂字典端点整体删除，本类随之移除
 * {@code listCanteens()} / {@code listWithStalls()} / {@code toStallVO()} 及 {@code StallMapper} 依赖
 * （档口树查询与档口节点出参一并退役）；仅保留后台列表与编辑能力。
 */
@Service
@RequiredArgsConstructor
public class CanteenServiceImpl implements CanteenService {

    private final CanteenMapper canteenMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    @Deprecated(since = "2026-09", forRemoval = true)
    public List<CanteenAdminVO> listAllForAdmin() {
        // ⚠️ 冻结：管理端（Web 后台）方法，待后期整体重构时移除。本期保留可编译、保留功能，不删除。
        return canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                        .orderByAsc(Canteen::getSortOrder)
                        .orderByDesc(Canteen::getUpdatedAt))
                .stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Deprecated(since = "2026-09", forRemoval = true)
    public void update(Canteen canteen) {
        // ⚠️ 冻结：管理端（Web 后台）方法，待后期整体重构时移除。本期保留可编译、保留功能，不删除。
        if (canteen.getId() == null || canteenMapper.updateById(canteen) == 0) {
            throw new BusinessException("Canteen not found");
        }
    }

    private CanteenAdminVO toAdminVO(Canteen canteen) {
        CanteenAdminVO vo = new CanteenAdminVO();
        vo.setId(canteen.getId());
        vo.setName(canteen.getName());
        vo.setLocation(canteen.getLocation());
        vo.setDescription(canteen.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
        vo.setSortOrder(canteen.getSortOrder());
        // createdBy 三端零消费（单口令模型无真实身份，恒为系统占位值），
        // VO 字段已删除；实体字段与写入侧、canteen.created_by 列定义同批退役（阶段4，schema.sql 幂等 DROP）。
        vo.setCreatedAt(canteen.getCreatedAt());
        vo.setUpdatedAt(canteen.getUpdatedAt());
        return vo;
    }
}
