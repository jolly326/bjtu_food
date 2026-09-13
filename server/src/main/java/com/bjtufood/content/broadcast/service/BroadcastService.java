package com.bjtufood.content.broadcast.service;

import com.bjtufood.content.broadcast.dto.BroadcastReq;
import com.bjtufood.content.broadcast.entity.Broadcast;

import java.util.List;

/**
 * 后台广播管理服务接口
 * <p>
 * P3/ARCH-008：CRUD 自 BroadcastAdminController 下沉，Controller 只留参数与响应包装。
 */
public interface BroadcastService {

    /**
     * 全部广播（含 disabled），按 sort_order 升序、created_at 降序
     */
    List<Broadcast> listAll();

    /**
     * 新增广播，返回自增ID
     */
    Long create(BroadcastReq req);

    /**
     * 编辑广播（部分字段更新：null 字段不覆盖，保持原 PATCH 语义）
     */
    void update(Long id, BroadcastReq req);

    /**
     * 删除广播
     */
    void delete(Long id);
}
