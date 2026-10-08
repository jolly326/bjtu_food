package com.bjtufood.common.audit;

import com.bjtufood.auth.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计「变更前后值」快照回归。
 *
 * <p>关键约束：① before 一旦登记即**冻结**（后续改动对象不影响已登记值，否则「变更前」会被
 * 后一次赋值覆盖）；② {@code take()} 取走即清除，绝不跨请求残留（容器线程是复用的）。
 */
class AuditSnapshotTest {

    @AfterEach
    void clearContext() {
        AuditSnapshot.clear();
    }

    @Test
    @DisplayName("before 快照在登记时即冻结：之后改动对象不改变已登记值")
    void beforeIsFrozenAtCaptureTime() {
        User user = new User();
        user.setId(1L);
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));

        AuditSnapshot.before(user);
        user.setStatus("disabled");
        AuditSnapshot.after(user);

        String[] snapshot = AuditSnapshot.take();
        assertThat(snapshot[0]).contains("\"status\":\"active\"").contains("\"id\":1");
        assertThat(snapshot[1]).contains("\"status\":\"disabled\"");
        // 时间字段可序列化（JSR-310 模块已注册），不是数组形态的时间戳
        assertThat(snapshot[0]).contains("2026-01-02T03:04:05");
    }

    @Test
    @DisplayName("take() 取走即清除：第二次取到空，防止跨请求残留")
    void takeRemovesContext() {
        AuditSnapshot.before(new User());
        assertThat(AuditSnapshot.take()[0]).isNotNull();

        String[] again = AuditSnapshot.take();
        assertThat(again[0]).isNull();
        assertThat(again[1]).isNull();
    }

    @Test
    @DisplayName("未登记快照时 take() 返回两侧 null（审计留痕不受影响）")
    void emptySnapshotIsNull() {
        String[] snapshot = AuditSnapshot.take();
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot[0]).isNull();
        assertThat(snapshot[1]).isNull();
    }

    @Test
    @DisplayName("null 对象不写快照，也不抛异常")
    void nullStateProducesNoSnapshot() {
        AuditSnapshot.before(null);
        AuditSnapshot.after(null);
        String[] snapshot = AuditSnapshot.take();
        assertThat(snapshot[0]).isNull();
        assertThat(snapshot[1]).isNull();
    }
}
