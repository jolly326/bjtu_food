package com.bjtufood.content.broadcast.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.result.Result;
import com.bjtufood.content.broadcast.dto.BroadcastVO;
import com.bjtufood.content.broadcast.entity.Broadcast;
import com.bjtufood.content.broadcast.mapper.BroadcastMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页广播通知条（task-14 W6，CONTRACT_IMPACT A.14）
 * <p>
 * 公开接口，无需登录；首页竖直翻滚 ticker 数据来源，按 broadcastType 分发跳转。
 */
@Tag(name = "18. 首页广播通知", description = "首页广播通知条列表，公开接口。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastMapper broadcastMapper;

    @Operation(
            summary = "首页广播通知列表",
            description = """
                    用途：首页广播通知条（竖直翻滚 ticker）数据来源。
                    返回 enabled 广播，按 sort_order 升序、created_at 降序；前端按 broadcastType 分发跳转。
                    无数据时返回空数组（前端保留轻量占位，不隐藏）。
                    """
    )
    @GetMapping("/broadcasts")
    public Result<List<BroadcastVO>> listBroadcasts() {
        List<Broadcast> list = broadcastMapper.selectList(
                new LambdaQueryWrapper<Broadcast>()
                        .eq(Broadcast::getStatus, "enabled")
                        .orderByAsc(Broadcast::getSortOrder)
                        .orderByDesc(Broadcast::getCreatedAt));
        return Result.success(list.stream().map(this::toVO).toList());
    }

    private BroadcastVO toVO(Broadcast b) {
        BroadcastVO vo = new BroadcastVO();
        vo.setId(b.getId());
        vo.setTitle(b.getTitle());
        vo.setContent(b.getContent());
        vo.setBroadcastType(b.getBroadcastType());
        vo.setTargetId(b.getTargetId());
        vo.setTargetUrl(b.getTargetUrl());
        vo.setCreatedAt(b.getCreatedAt());
        return vo;
    }
}
