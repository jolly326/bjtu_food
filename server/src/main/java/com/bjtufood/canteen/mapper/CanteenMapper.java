package com.bjtufood.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.canteen.entity.Canteen;

/**
 * 食堂 Mapper 接口
 * <p>
 * 继承 BaseMapper，基础 CRUD 方法自动可用。
 * 复杂查询（如：食堂列表含档口）建议在 Service 层使用 MyBatis-Plus 的 Wrapper 或 XML 自定义查询。
 */
public interface CanteenMapper extends BaseMapper<Canteen> {
}
