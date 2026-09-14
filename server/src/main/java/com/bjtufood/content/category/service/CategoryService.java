package com.bjtufood.content.category.service;

import com.bjtufood.content.category.entity.Category;

import java.util.List;
import java.util.Map;

/**
 * 菜品品类后台管理服务接口
 * <p>
 * P3/ARCH-008：增删改启停（含业务校验）自 CategoryAdminController 下沉，
 * Controller 只留参数与响应包装（@AuditLog 埋点注解保留在 Controller）。
 * 入参沿用原 Map 裸参契约：update 依赖 containsKey 区分「未传/不更新」，
 * 强改 DTO 会破坏部分更新语义，故保持原样（行为零变化）。
 */
public interface CategoryService {

    /**
     * 全部品类（含禁用），按 sort_order 升序（后台列表）
     */
    List<Category> listAll();

    /**
     * 启用品类（status=enabled），按 sort_order 升序（小程序公开列表）
     * <p>
     * BE-04：原 CategoryController 绕过本 Service 直接注入 CategoryMapper 拼查询，
     * 破坏四层分层；此处补齐只读方法，Controller 统一改走 Service。
     *
     * @return enabled 品类列表，无数据时返回空列表
     */
    List<Category> listEnabled();

    /**
     * 新增品类（code 必填、格式与唯一性校验；status 缺省 enabled），返回自增ID
     */
    Long create(Map<String, Object> body);

    /**
     * 编辑品类（containsKey 部分更新：code 冲突校验、名称非空校验、排序值安全解析）
     */
    void update(Long id, Map<String, Object> body);

    /**
     * 启停品类（status 仅允许 enabled/disabled）
     */
    void updateStatus(Long id, Map<String, String> body);

    /**
     * 删除品类（存在性校验）
     */
    void delete(Long id);
}
