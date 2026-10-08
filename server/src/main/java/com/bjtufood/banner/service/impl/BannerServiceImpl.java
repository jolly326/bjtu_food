package com.bjtufood.banner.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.banner.dto.BannerAdminVO;
import com.bjtufood.banner.dto.BannerVO;
import com.bjtufood.banner.entity.Banner;
import com.bjtufood.banner.mapper.BannerMapper;
import com.bjtufood.banner.service.BannerService;
import com.bjtufood.common.dto.SortItem;
import com.bjtufood.common.audit.AuditSnapshot;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.SortReorderUtil;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页轮播图服务实现。
 * <p>
 * 公开出参收敛：{@code status} / {@code sort_order} 仅用于过滤与排序，**不出参**；
 * {@code image_url} 统一转绝对 URL（与菜品图片同口径：相对路径转绝对、绝对地址原样返回）。
 * <p>
 * 管理端（A5）：列表含已停用、新增默认启用并排最后、启停显式传状态、排序整体替换全量行。
 */
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    /** 启用状态值（与 dish.status 同风格：on / off） */
    private static final String STATUS_ON = "on";
    /** 停用状态值 */
    private static final String STATUS_OFF = "off";
    /** 图片地址列宽（`banner.image_url VARCHAR(500)`） */
    private static final int IMAGE_URL_MAX = 500;

    private final BannerMapper bannerMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public List<BannerVO> listBanners() {
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, STATUS_ON)
                        .orderByAsc(Banner::getSortOrder))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    // ==================== 管理端（A5） ====================

    @Override
    public List<BannerAdminVO> listAllForAdmin() {
        // 含已停用（管理端要能重新启用）；按 order 升序
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .orderByAsc(Banner::getSortOrder)
                        .orderByDesc(Banner::getUpdatedAt))
                .stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BannerAdminVO create(String imageUrl) {
        String url = normalizeImageUrl(imageUrl);
        Banner banner = new Banner();
        banner.setImageUrl(url);
        // 新增默认启用，且排最后（避免插到首位打乱运营顺序）
        banner.setSortOrder((int) (bannerMapper.selectCount(null) + 1));
        banner.setStatus(STATUS_ON);
        bannerMapper.insert(banner);
        return toAdminVO(bannerMapper.selectById(banner.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String imageUrl) {
        String url = normalizeImageUrl(imageUrl);
        Banner update = new Banner();
        update.setId(id);
        update.setImageUrl(url);
        if (id == null || bannerMapper.updateById(update) == 0) {
            throw new BusinessException(4001, "Banner 不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        if (!STATUS_ON.equals(status) && !STATUS_OFF.equals(status)) {
            throw new BusinessException("status 非法");
        }
        Banner update = new Banner();
        update.setId(id);
        update.setStatus(status);
        if (id == null || bannerMapper.updateById(update) == 0) {
            throw new BusinessException(4001, "Banner 不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<SortItem> items) {
        // 必须是全量行（缺行 / 未知 id / 重复 id / 重复 order → 400），见 web/README 的「拖拽排序提交」
        List<Long> existingIds = bannerMapper.selectList(null).stream().map(Banner::getId).toList();
        Map<Long, Integer> ordered = SortReorderUtil.resolve(items, existingIds);
        for (Map.Entry<Long, Integer> e : ordered.entrySet()) {
            Banner update = new Banner();
            update.setId(e.getKey());
            update.setSortOrder(e.getValue());
            bannerMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Banner current = id == null ? null : bannerMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(4001, "Banner 不存在");
        }
        // 审计变更前值（物理删除不可逆，快照是误删后重建的依据）
        AuditSnapshot.before(current);
        bannerMapper.deleteById(id);
    }

    /** 图片地址规范化：trim；空白 / 超列宽 → 400 */
    private static String normalizeImageUrl(String imageUrl) {
        String url = imageUrl == null ? null : imageUrl.trim();
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("请上传 Banner 图片");
        }
        if (url.length() > IMAGE_URL_MAX) {
            throw new BusinessException("图片地址过长");
        }
        return url;
    }

    private BannerVO toVO(Banner banner) {
        BannerVO vo = new BannerVO();
        vo.setId(banner.getId());
        vo.setImageUrl(imageUrlUtil.toAbsoluteUrl(banner.getImageUrl()));
        return vo;
    }

    /** 管理端 VO：多带 order / status / 时间列（图片同转绝对 URL） */
    private BannerAdminVO toAdminVO(Banner banner) {
        BannerAdminVO vo = new BannerAdminVO();
        vo.setId(banner.getId());
        vo.setImageUrl(imageUrlUtil.toAbsoluteUrl(banner.getImageUrl()));
        vo.setOrder(banner.getSortOrder());
        vo.setStatus(banner.getStatus());
        vo.setCreatedAt(banner.getCreatedAt());
        vo.setUpdatedAt(banner.getUpdatedAt());
        return vo;
    }
}
