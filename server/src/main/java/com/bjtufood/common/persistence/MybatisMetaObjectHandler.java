package com.bjtufood.common.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * 时间戳（created_at / updated_at）一律由 DB 时钟决定
 * （INSERT = DEFAULT CURRENT_TIMESTAMP，UPDATE = ON UPDATE CURRENT_TIMESTAMP，
 * 见 docs/schema/README.md「时间戳写入来源」），本类刻意不填充任何字段。
 *
 * <p>MyBatis-Plus 3.5.5 中 {@link MetaObjectHandler} 的两个方法为 abstract，
 * 故保留空实现以满足接口契约。
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 刻意不填充：时间戳由 DB 的 DEFAULT CURRENT_TIMESTAMP 写入
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 刻意不填充：时间戳由 DB 的 ON UPDATE CURRENT_TIMESTAMP 写入
    }
}
