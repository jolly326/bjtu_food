package com.bjtufood.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.history.dto.ViewLogVO;
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 浏览足迹服务实现
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final ViewLogMapper viewLogMapper;
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public void record(Long userId, String targetType, Long targetId) {
        ViewLog log = new ViewLog();
        log.setUserId(userId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        viewLogMapper.insert(log);
    }

    @Override
    public IPage<ViewLogVO> listMyHistory(Long userId, String targetType, int page, int pageSize) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];

        LambdaQueryWrapper<ViewLog> wrapper = new LambdaQueryWrapper<ViewLog>()
                .eq(ViewLog::getUserId, userId)
                .eq(StringUtils.hasText(targetType), ViewLog::getTargetType, targetType)
                .orderByDesc(ViewLog::getCreatedAt);

        IPage<ViewLog> p = viewLogMapper.selectPage(new Page<>(page, pageSize), wrapper);

        // 批量补齐名称与图片
        List<ViewLog> records = p.getRecords();
        Map<Long, String> dishNameMap = new HashMap<>();
        Map<Long, String> dishImgMap = new HashMap<>();
        Map<Long, String> stallNameMap = new HashMap<>();
        Map<Long, String> canteenNameMap = new HashMap<>();

        for (ViewLog log : records) {
            String tt = log.getTargetType();
            Long tid = log.getTargetId();
            if ("dish".equals(tt) && !dishNameMap.containsKey(tid)) {
                Dish d = dishMapper.selectById(tid);
                dishNameMap.put(tid, d != null ? d.getName() : "");
                dishImgMap.put(tid, d != null ? d.getImages() : null);
            } else if ("stall".equals(tt) && !stallNameMap.containsKey(tid)) {
                Stall s = stallMapper.selectById(tid);
                stallNameMap.put(tid, s != null ? s.getName() : "");
            } else if ("canteen".equals(tt) && !canteenNameMap.containsKey(tid)) {
                Canteen c = canteenMapper.selectById(tid);
                canteenNameMap.put(tid, c != null ? c.getName() : "");
            }
        }

        IPage<ViewLogVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(records.stream().map(log -> {
            ViewLogVO vo = new ViewLogVO();
            vo.setId(log.getId());
            vo.setTargetType(log.getTargetType());
            vo.setTargetId(log.getTargetId());
            vo.setCreatedAt(log.getCreatedAt());
            switch (log.getTargetType()) {
                case "dish" -> {
                    vo.setTargetName(dishNameMap.getOrDefault(log.getTargetId(), ""));
                    vo.setTargetImage(imageUrlUtil.toAbsoluteUrl(firstImage(dishImgMap.get(log.getTargetId()))));
                }
                case "stall" -> vo.setTargetName(stallNameMap.getOrDefault(log.getTargetId(), ""));
                case "canteen" -> vo.setTargetName(canteenNameMap.getOrDefault(log.getTargetId(), ""));
                default -> vo.setTargetName("");
            }
            return vo;
        }).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOne(Long userId, Long id) {
        ViewLog log = viewLogMapper.selectById(id);
        if (log == null || !log.getUserId().equals(userId)) {
            throw new BusinessException("足迹不存在");
        }
        viewLogMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearAll(Long userId) {
        viewLogMapper.delete(new LambdaQueryWrapper<ViewLog>().eq(ViewLog::getUserId, userId));
    }

    @Override
    public List<Long> recentViewedDishIds(Long userId, int limit) {
        if (limit < 1) limit = 20;
        return viewLogMapper.selectList(new LambdaQueryWrapper<ViewLog>()
                        .eq(ViewLog::getUserId, userId)
                        .eq(ViewLog::getTargetType, "dish")
                        .orderByDesc(ViewLog::getCreatedAt)
                        .last("LIMIT " + limit))
                .stream()
                .map(ViewLog::getTargetId)
                .distinct()
                .toList();
    }

    private String firstImage(String imagesJson) {
        List<String> list = JsonListUtil.parseStringList(imagesJson);
        return list.isEmpty() ? null : list.get(0);
    }
}
