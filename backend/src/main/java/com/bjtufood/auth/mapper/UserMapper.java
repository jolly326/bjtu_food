package com.bjtufood.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.auth.entity.User;

/**
 * 用户 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动获取基础的 CRUD 方法：
 * - insert / deleteById / updateById / selectById / selectList / selectPage
 * - 复杂查询使用 MyBatis-Plus 的 QueryWrapper 或 XML 自定义 SQL
 */
public interface UserMapper extends BaseMapper<User> {
}
