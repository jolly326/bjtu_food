package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.dto.CanteenSaveReq;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.support.ImageColumnWriter;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
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
 * （档口树查询与档口节点出参一并移除）；仅保留后台列表与编辑能力。
 */
@Service
@RequiredArgsConstructor
public class CanteenServiceImpl implements CanteenService {

    /** `canteen.images VARCHAR(1024)` 的字符宽度上限（超长即 400，禁止静默截断） */
    private static final int IMAGES_JSON_MAX = 1024;

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
    public void update(Long id, CanteenSaveReq req) {
        if (id == null) {
            throw new BusinessException(4001, "食堂不存在");
        }
        String trimmed = normalizeName(req == null ? null : req.getName());
        // A1：改名同样受「名称唯一」约束 —— 只在新增时校验的话，
        // 「把 A 食堂改名成已存在的 B」会绕过约束，制造出同名食堂（数据重复的根源）。
        DuplicateGuard.assertUnique(canteenMapper, new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getName, trimmed)
                .ne(Canteen::getId, id), "食堂名称已存在");
        // 局部实体 + updateById（NOT_NULL 策略）：未提交的可选字段（含时间列）不带值即不写列，
        // 时间列由库的 ON UPDATE CURRENT_TIMESTAMP 维护（实体时间字段无 fill 注解，见 docs/schema/README.md）。
        Canteen patch = new Canteen();
        patch.setId(id);
        patch.setName(trimmed);
        applyOptionalFields(patch, req);
        if (canteenMapper.updateById(patch) == 0) {
            throw new BusinessException(4001, "食堂不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CanteenAdminVO createCanteen(CanteenSaveReq req) {
        String trimmed = normalizeName(req == null ? null : req.getName());
        // 名称唯一由应用层校验（A1：不强制加 DB 唯一索引 —— 历史数据可能已有重复，避免迁移阻塞）
        DuplicateGuard.assertUnique(canteenMapper, new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getName, trimmed), "食堂名称已存在");
        Canteen canteen = new Canteen();
        canteen.setName(trimmed);
        applyOptionalFields(canteen, req);
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

    /**
     * 名称不变量（服务层持有，不依赖调用方的 DTO 校验）：trim 后 1~64 字。
     */
    private static String normalizeName(String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BusinessException("食堂名称不能为空");
        }
        if (trimmed.length() > 64) {
            throw new BusinessException("食堂名称不能超过 64 字");
        }
        return trimmed;
    }

    /**
     * 写入 4 个可选列（位置 / 描述 / 图片 / 排序位）。
     * <p>
     * <b>null = 保持原值</b>（局部实体不带该列 ⇒ updateById 的 NOT_NULL 策略跳过；
     * 新建时可空列即落 NULL），给值即覆盖（空串 / 空数组表达「清空」）。
     */
    private void applyOptionalFields(Canteen entity, CanteenSaveReq req) {
        if (req == null) {
            return;
        }
        if (req.getLocation() != null) {
            entity.setLocation(req.getLocation().trim());
        }
        if (req.getDescription() != null) {
            entity.setDescription(req.getDescription().trim());
        }
        if (req.getImages() != null) {
            entity.setImages(ImageColumnWriter.encode(imageUrlUtil, req.getImages(), IMAGES_JSON_MAX,
                    "食堂图片地址过长（序列化后不得超过 " + IMAGES_JSON_MAX + " 字符）"));
        }
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }
    }

    private CanteenAdminVO toAdminVO(Canteen canteen) {
        CanteenAdminVO vo = new CanteenAdminVO();
        vo.setId(canteen.getId());
        vo.setName(canteen.getName());
        // 其下档口数：删除受阻判据 + 列表展示（量级十数条，逐行 count 可接受）
        vo.setStallCount(stallMapper.selectCount(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getCanteenId, canteen.getId())));
        // 出参空值口径：可空字符串列恒非空串，端上无需判空
        vo.setLocation(orEmpty(canteen.getLocation()));
        vo.setDescription(orEmpty(canteen.getDescription()));
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
        vo.setSortOrder(canteen.getSortOrder());
        vo.setUpdatedAt(canteen.getUpdatedAt());
        return vo;
    }

    /** 出参空值归一：{@code null} → 空串（后台列表的字符串列恒非空串，端上无需判空） */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
