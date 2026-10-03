package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.CanteenAdminVO;
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
    private final StallMapper stallMapper;
    private final ImageUrlUtil imageUrlUtil;

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
        if (canteen.getId() == null) {
            throw new BusinessException("Canteen not found");
        }
        // A1：改名同样受「名称唯一」约束 —— 只在新增时校验的话，
        // 「把 A 食堂改名成已存在的 B」会绕过约束，制造出同名食堂（数据重复的根源）。
        if (canteen.getName() != null && !canteen.getName().isBlank()) {
            String trimmed = canteen.getName().trim();
            boolean duplicated = canteenMapper.selectCount(new LambdaQueryWrapper<Canteen>()
                    .eq(Canteen::getName, trimmed)
                    .ne(Canteen::getId, canteen.getId())) > 0;
            if (duplicated) {
                throw new BusinessException("食堂名称已存在");
            }
            canteen.setName(trimmed);
        }
        if (canteenMapper.updateById(canteen) == 0) {
            throw new BusinessException("Canteen not found");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CanteenAdminVO createCanteen(String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BusinessException("食堂名称不能为空");
        }
        if (trimmed.length() > 64) {
            throw new BusinessException("食堂名称不能超过 64 字");
        }
        // 名称唯一由应用层校验（A1：不强制加 DB 唯一索引 —— 历史数据可能已有重复，避免迁移阻塞）
        boolean duplicated = canteenMapper.selectCount(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getName, trimmed)) > 0;
        if (duplicated) {
            throw new BusinessException("食堂名称已存在");
        }
        Canteen canteen = new Canteen();
        canteen.setName(trimmed);
        canteenMapper.insert(canteen);
        return toAdminVO(canteenMapper.selectById(canteen.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCanteen(Long id) {
        if (id == null || canteenMapper.selectById(id) == null) {
            throw new BusinessException(4001, "食堂不存在");
        }
        // 删除受阻：其下仍有档口时禁止删除（避免孤儿档口，见 A1 错误码节）
        long stallCount = stallMapper.selectCount(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getCanteenId, id));
        if (stallCount > 0) {
            throw new BusinessException("该食堂下仍有 " + stallCount + " 个档口，不能删除");
        }
        canteenMapper.deleteById(id);
    }

    private CanteenAdminVO toAdminVO(Canteen canteen) {
        CanteenAdminVO vo = new CanteenAdminVO();
        vo.setId(canteen.getId());
        vo.setName(canteen.getName());
        // 其下档口数：删除受阻判据 + 列表展示（量级十数条，逐行 count 可接受）
        vo.setStallCount(stallMapper.selectCount(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getCanteenId, canteen.getId())));
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
