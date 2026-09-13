package com.bjtufood.content.broadcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.content.broadcast.dto.BroadcastReq;
import com.bjtufood.content.broadcast.entity.Broadcast;
import com.bjtufood.content.broadcast.mapper.BroadcastMapper;
import com.bjtufood.content.broadcast.service.BroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 后台广播管理服务实现
 * <p>
 * P3/ARCH-008：CRUD 逻辑自 BroadcastAdminController 原样迁移（行为零变化）。
 * 更新走 MyBatis-Plus updateById 的 NOT_NULL 字段策略：请求未传的字段不覆盖，
 * 与原「实体收请求体」的部分更新行为一致。
 */
@Service
@RequiredArgsConstructor
public class BroadcastServiceImpl implements BroadcastService {

    private final BroadcastMapper broadcastMapper;

    @Override
    public List<Broadcast> listAll() {
        return broadcastMapper.selectList(new LambdaQueryWrapper<Broadcast>()
                .orderByAsc(Broadcast::getSortOrder)
                .orderByDesc(Broadcast::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(BroadcastReq req) {
        Broadcast b = toEntity(req);
        broadcastMapper.insert(b);
        return b.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BroadcastReq req) {
        Broadcast b = toEntity(req);
        b.setId(id);
        broadcastMapper.updateById(b);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        broadcastMapper.deleteById(id);
    }

    private Broadcast toEntity(BroadcastReq req) {
        Broadcast b = new Broadcast();
        b.setTitle(req.getTitle());
        b.setContent(req.getContent());
        b.setBroadcastType(req.getBroadcastType());
        b.setTargetId(req.getTargetId());
        b.setTargetUrl(req.getTargetUrl());
        b.setSortOrder(req.getSortOrder());
        b.setStatus(req.getStatus());
        return b;
    }
}
