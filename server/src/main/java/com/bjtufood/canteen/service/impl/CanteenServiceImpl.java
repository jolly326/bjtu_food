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
    // 注（2026-09-21 契约精简，见 docs/project_spec.md §7.33）：
    // 1) DishMapper 依赖已随 dishCount/topDishes/perCapita 下线移除（本类不再查询菜品）；
    // 2) ReviewMapper 依赖已随档口 avgRating 出参收敛移除——batchAvgRating 批查属白算，
    //    端上（反馈页位置两级联动）只读档口的 id / name。

    @Override
    public List<CanteenInfoVO> listCanteens() {
        // 注：坐标与距离已全链下线（2026-09-20 拍板）：食堂 VO 不再暴露坐标，端上不申请定位权限、不算距离。
        // 食堂已去实体化（2026-09-14）：无停业语义，不再按 status 过滤，全量字典按 sort_order 返回
        // 2026-09-21 出参收敛：location / description / images 三字段全端零消费，已删除；
        // 图片绝对 URL 拼接随之移除（此前属白算）。
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .orderByAsc(Canteen::getSortOrder));
        return canteens.stream()
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
        // 食堂/档口已去实体化（2026-09-14）：无停业语义，不再按 status 过滤，全量字典按 sort_order 返回
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .orderByAsc(Canteen::getSortOrder));
        // 档口按 sort_order 升序（字典顺序稳定：食堂升序、同食堂下档口亦升序）
        List<Stall> allStalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .in(Stall::getCanteenId, canteens.stream().map(Canteen::getId).toList())
                .orderByAsc(Stall::getSortOrder));
        // 2026-09-15：原「批量查询全部档口在售菜品」（dishCount/topDishes/perCapita 白算的数据源）随三字段下线删除。
        // 2026-09-21（§7.33）：档口层出参收敛为 id / name，avgRating 批查（batchAvgRating）随之删除。
        return canteens.stream()
                .map(canteen -> {
                    CanteenWithStallsVO vo = new CanteenWithStallsVO();
                    vo.setId(canteen.getId());
                    vo.setName(canteen.getName());
                    List<StallDetailVO> stalls = allStalls.stream()
                            .filter(s -> s.getCanteenId().equals(canteen.getId()))
                            .map(this::toStallVO)
                            .collect(Collectors.toList());
                    vo.setStalls(stalls);
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
     * 档口节点 → 最小出参（恰为 id / name，2026-09-21 §7.33）。
     * <p>
     * 端上（反馈页位置两级联动）只读 id / name；images / location / floor / windowNo /
     * description / avgRating 均零消费，已随出参收敛删除（其中 avgRating 连带移除批查白算）。
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
