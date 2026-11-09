package vn.sspack.hrm.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataScopeTest {

    @Test
    @DisplayName("Người có nhiều vai trò được xem theo phạm vi rộng nhất")
    void widest() {
        assertThat(DataScope.SELF.widest(DataScope.ALL)).isEqualTo(DataScope.ALL);
        assertThat(DataScope.ALL.widest(DataScope.SELF)).isEqualTo(DataScope.ALL);
        assertThat(DataScope.SELF.widest(DataScope.DEPARTMENT_TREE)).isEqualTo(DataScope.DEPARTMENT_TREE);
        assertThat(DataScope.DEPARTMENT.widest(DataScope.DEPARTMENT_TREE)).isEqualTo(DataScope.DEPARTMENT_TREE);
        assertThat(DataScope.ASSIGNED.widest(DataScope.SELF)).isEqualTo(DataScope.SELF);
        assertThat(DataScope.DEPARTMENT.widest(null)).isEqualTo(DataScope.DEPARTMENT);
    }
}
