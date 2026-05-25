package com.bjtufood.list.service;

import com.bjtufood.list.dto.ListCreateReq;
import com.bjtufood.list.dto.ListDetailVO;
import com.bjtufood.list.dto.ListVO;

import java.util.List;
import java.util.Map;

/**
 * 清单服务接口
 * <p>
 * 美食清单的创建、查看、删除和分享功能。
 * 创建时自动生成 shareToken，分享时无需登录即可查看。
 * 一键收藏功能通过注入 FavoriteService 接口调用批量收藏方法。
 */
public interface ListService {

    /**
     * 创建美食清单
     * <p>
     * 处理流程：
     * 1. 校验菜品ID是否合法
     * 2. 创建清单记录（自动生成 shareToken）
     * 3. 批量创建清单项
     *
     * @param userId 用户ID
     * @param req    创建请求（名称 + 描述 + 菜品ID列表）
     * @return 创建后的清单ID
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在
     */
    Long createList(Long userId, ListCreateReq req);

    /**
     * 查询用户的清单列表
     * <p>
     * 返回摘要信息（含菜品数量）
     *
     * @param userId 用户ID
     * @return 清单列表
     */
    List<ListVO> listByUserId(Long userId);

    /**
     * 查询清单详情（含完整菜品信息）
     *
     * @param id 清单ID
     * @return 清单详情（含菜品列表）
     * @throws com.bjtufood.common.exception.BusinessException 清单不存在
     */
    ListDetailVO getDetail(Long id);

    /**
     * 通过分享 token 查询清单详情
     * <p>
     * 无需登录即可访问，用于分享落地页展示
     *
     * @param shareToken 分享token
     * @return 清单详情
     * @throws com.bjtufood.common.exception.BusinessException 链接无效
     */
    ListDetailVO getByShareToken(String shareToken);

    /**
     * 删除清单
     * <p>
     * 级联删除清单项（list_item 表设置了 ON DELETE CASCADE）
     *
     * @param id     清单ID
     * @param userId 当前用户ID
     * @throws com.bjtufood.common.exception.BusinessException 清单不存在/无权限
     */
    void deleteList(Long id, Long userId);

    /**
     * 清单一键收藏
     * <p>
     * 调用 FavoriteService.batchCollect() 实现
     *
     * @param listId 清单ID
     * @param userId 当前用户ID
     * @return 收藏结果 { succeeded, skipped }
     */
    Map<String, Integer> collectAll(Long listId, Long userId);
}
