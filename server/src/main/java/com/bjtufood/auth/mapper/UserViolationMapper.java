package com.bjtufood.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.auth.entity.UserViolation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生账号违规留痕 Mapper（只追加，无更新 / 删除入口）。
 */
@Mapper
public interface UserViolationMapper extends BaseMapper<UserViolation> {
}
