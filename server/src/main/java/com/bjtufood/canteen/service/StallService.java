package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.entity.Stall;

import java.util.List;

/**
 * 档口服务接口
 * <p>
 * 档口已去实体化（2026-09-14）：降级为「菜品筛选属性字典」，生命周期仅「新增 / 改名（编辑）」＋列表查询，
 * 无删除、无停业/审核能力；一个档口仍从属于一个食堂（canteen_id）。
 */
public interface StallService {

    /**
     * 后台档口列表
     *
     * @return 后台档口 VO 列表（含完整图片 URL）
     */
    List<StallAdminVO> listAllForAdmin();

    /**
     * 编辑档口
     *
     * @param stall 档口信息（含ID）
     */
    void update(Stall stall);

    /**
     * 按名 upsert 档口（§7.23 第 1 条）：同名不重复建档（精确匹配，名称列无唯一键，
     * 并发双写极端情况由调用方幂等容忍）。菜品录入/编辑（DishServiceImpl#resolveStallId）
     * 与菜品纠错采纳（createIfMissing=true）共用本入口。
     * <p>
     * 新建档口必须有可解析的有效所属食堂（canteenName 有效并按名 upsert 所属食堂，
     * 空白/「其他」等空值语义名称 → 400「请选择所属食堂」），不允许落 canteen_id=0。
     *
     * @param stallName      档口名（调用方保证非空白）
     * @param rawCanteenName 所属食堂名（仅新建档口时消费，可空）
     * @return 档口 ID
     */
    Long upsertStallByName(String stallName, String rawCanteenName);

    /**
     * 档口存在性校验（纠错采纳带 stallId 时防御性复查用）。
     *
     * @param stallId 档口ID（可空）
     * @return true=存在
     */
    boolean existsById(Long stallId);
}
