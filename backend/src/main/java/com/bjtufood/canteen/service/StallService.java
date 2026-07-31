package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.MyPublishStallVO;
import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.entity.Stall;

import java.util.List;

/**
 * 档口服务接口
 * <p>
 * 档口的查询和管理，一个档口从属于一个食堂。
 */
public interface StallService {

    /**
     * 按食堂ID查询档口列表
     *
     * @param canteenId 食堂ID
     * @return 该食堂下的档口列表
     */
    List<StallDetailVO> listByCanteenId(Long canteenId);

    /**
     * 后台档口列表
     *
     * @return 后台档口 VO 列表（含完整图片 URL）
     */
    List<StallAdminVO> listAllForAdmin();

    /**
     * 新增档口
     *
     * @param stall 档口信息
     * @throws com.bjtufood.common.exception.BusinessException 食堂不存在
     */
    void add(Stall stall);

    /**
     * 编辑档口
     *
     * @param stall 档口信息（含ID）
     */
    void update(Stall stall);

    /**
     * 删除档口
     * <p>
     * 约束：如果档口下还有菜品，禁止删除
     *
     * @param id 档口ID
     * @throws com.bjtufood.common.exception.BusinessException 档口下有菜品
     */
    void delete(Long id);

    /**
     * 根据ID查询档口
     *
     * @param id 档口ID
     * @return 档口实体
     * @throws com.bjtufood.common.exception.BusinessException 档口不存在
     */
    Stall getById(Long id);

    /**
     * 学生 UGC 提交档口/食堂
     * <p>
     * 写入 audit_status=pending、created_by=当前登录用户，等待后台审核。
     *
     * @param req 提交内容（含 type=stall|canteen 及名称/描述/关联食堂等）
     * @return 提交的实体ID
     * @throws com.bjtufood.common.exception.BusinessException 参数非法或关联食堂不存在
     */
    Long submitUgc(com.bjtufood.canteen.dto.StallUgcSubmitReq req);

    /**
     * 学生"我的发布"：查询当前登录学生提交的档口/食堂列表
     * <p>
     * 分别查 stall 表与 canteen 表（where created_by=当前用户），合并为统一列表。
     * 顺序：先食堂(canteen)后档口(stall)，各自按创建时间倒序；同一表内按创建时间倒序。
     *
     * @return 当前学生提交的档口/食堂 VO 列表（含 auditStatus / rejectReason）
     */
    List<MyPublishStallVO> listMySubmissions();
}
