package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.dto.CanteenWithStallsVO;
import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CanteenServiceImpl implements CanteenService {

    private final CanteenMapper canteenMapper;
    private final StallMapper stallMapper;
    private final ImageUrlUtil imageUrlUtil;
    // 注（2026-09-21 §7.33 / api-slimming E 项）：
    // · ReviewMapper 依赖已随「档口层 avgRating 出参收敛」一并移除——batchAvgRating 批查属白算，删除；
    // · DishMapper 依赖已随 dishCount/topDishes/perCapita 下线一并移除（本类不再查询菜品）。

    @Override
    public List<CanteenInfoVO> listCanteens() {
        // 食堂已去实体化（2026-09-14）：无停业语义，不再按 status 过滤，全量字典按 sort_order 返回；
        // 出参收敛为 id/name 最小字典（2026-09-21 §7.33）——imageUrlUtil 的图片绝对化属白算，一并删除
        return canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .orderByAsc(Canteen::getSortOrder))
                .stream()
                .map(canteen -> {
                    CanteenInfoVO vo = new CanteenInfoVO();
                    vo.setId(canteen.getId());
                    vo.setName(canteen.getName());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CanteenWithStallsVO> listWithStalls() {
        // 食堂/档口已去实体化（2026-09-14）：无停业语义，不再按 status 过滤，全量字典按 sort_order 返回；
        // 食堂层收敛为 id/name/stalls、档口层收敛为 {id, name}（端上反馈页两级联动只读这两项）——
        // avgRating 批查与档口位置/图片等字段随出参收敛一并删除（E 项）
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .orderByAsc(Canteen::getSortOrder));
        List<Stall> allStalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .in(Stall::getCanteenId, canteens.stream().map(Canteen::getId).toList())
                .orderByAsc(Stall::getSortOrder));
        return canteens.stream()
                .map(canteen -> {
                    CanteenWithStallsVO vo = new CanteenWithStallsVO();
                    vo.setId(canteen.getId());
                    vo.setName(canteen.getName());
                    vo.setStalls(allStalls.stream()
                            .filter(s -> s.getCanteenId().equals(canteen.getId()))
                            .map(this::toStallVO)
                            .collect(Collectors.toList()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CanteenAdminVO> listAllForAdmin() {
        return canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                        .orderByAsc(Canteen::getSortOrder)
                        .orderByDesc(Canteen::getUpdatedAt))
                .stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Canteen canteen) {
        if (canteen.getId() == null || canteenMapper.updateById(canteen) == 0) {
            throw new BusinessException("Canteen not found");
        }
    }

    /**
     * 档口树节点：出参收敛为 {id, name}（2026-09-21 §7.33）。
     * <p>
     * 端上（反馈页位置两级联动）只读 id / name；楼层/窗口号/位置/描述/图片/平均分等
     * 均为全端零消费出参，已随收敛删除（见 {@link StallDetailVO} 类注释）。
     */
    private StallDetailVO toStallVO(Stall stall) {
        StallDetailVO vo = new StallDetailVO();
        vo.setId(stall.getId());
        vo.setName(stall.getName());
        return vo;
    }

    private CanteenAdminVO toAdminVO(Canteen canteen) {
        CanteenAdminVO vo = new CanteenAdminVO();
        vo.setId(canteen.getId());
        vo.setName(canteen.getName());
        vo.setLocation(canteen.getLocation());
        vo.setDescription(canteen.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
        vo.setSortOrder(canteen.getSortOrder());
        // 2026-09-15：createdBy 三端零消费（单口令模型无真实身份，恒为系统占位值），
        // VO 字段已删除；实体字段与写入侧、canteen.created_by 列定义同批退役（阶段4，schema.sql 幂等 DROP）。
        vo.setCreatedAt(canteen.getCreatedAt());
        vo.setUpdatedAt(canteen.getUpdatedAt());
        return vo;
    }
}
