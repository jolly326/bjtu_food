package com.bjtufood.common.handler;

import com.bjtufood.common.utils.JsonListUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * {@code List<String>} ↔ 「JSON 数组串」列的 MyBatis 类型处理器。
 * <p>
 * 用途（2026-09-23 change {@code dish-detail-contract-hardening} R5）：图片类列
 * （{@code dish.images} / {@code review.images}）以 JSON 数组串存储，此前由各出参 VO 上寄生一个
 * {@code @JsonIgnore String imagesJson} 的**存储形态中转字段**承接、再由 Service 手工解析 ——
 * 该写法让「DB 行 → VO」的转换职责落在出参 VO 上，且**靠注解兜住不出参**（漏注 / 换序列化器即泄漏）。
 * 改由本处理器在持久层完成「列 ↔ List」转换，出参 VO 只留出参字段，从类型上消除该中转字段。
 * <p>
 * <b>职责边界</b>：本处理器只做「JSON 串 ↔ List」的形态转换，**不掺业务逻辑** ——
 * 图片相对路径 → 绝对 URL 的转换（{@code ImageUrlUtil}）仍留在 Service 层。
 * <p>
 * 与 {@link JsonListUtil} 同源：解析兼容历史「逗号分隔」旧数据格式；序列化统一输出 JSON 数组串。
 */
@MappedTypes(List.class)
public class StringListTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonListUtil.toJson(parameter));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JsonListUtil.parseStringList(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JsonListUtil.parseStringList(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JsonListUtil.parseStringList(cs.getString(columnIndex));
    }
}
