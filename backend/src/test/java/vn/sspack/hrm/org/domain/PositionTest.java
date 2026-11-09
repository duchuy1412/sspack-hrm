package vn.sspack.hrm.org.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static vn.sspack.hrm.org.domain.JobGradeTest.T0;
import static vn.sspack.hrm.org.domain.JobGradeTest.T1;
import static vn.sspack.hrm.org.domain.JobGradeTest.codeOf;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/** ORG-02: mỗi chức danh gắn với một khung cấp bậc lương. */
class PositionTest {

    static JobGrade savedGrade(long id, long companyId, boolean active) {
        JobGrade g = JobGrade.create(companyId, "G" + id, "Cấp " + id, BigDecimal.ONE, BigDecimal.TEN, T0);
        ReflectionTestUtils.setField(g, "id", id);
        if (!active) {
            g.deactivate(0, T0);
        }
        return g;
    }

    @Test
    @DisplayName("Tạo: mã viết hoa, gắn đúng cấp bậc")
    void create() {
        Position p = Position.create(1L, "ks-qc", " Kỹ sư chất lượng ", savedGrade(3, 1, true), T0);

        assertThat(p.getCode()).isEqualTo("KS-QC");
        assertThat(p.getName()).isEqualTo("Kỹ sư chất lượng");
        assertThat(p.getJobGradeId()).isEqualTo(3L);
        assertThat(p.isActive()).isTrue();
    }

    @Test
    @DisplayName("Thiếu cấp bậc hoặc cấp bậc của công ty khác: POSITION_GRADE_REQUIRED")
    void gradeRequiredFromSameCompany() {
        assertThat(codeOf(() -> Position.create(1L, "KS", "Kỹ sư", null, T0))).isEqualTo("POSITION_GRADE_REQUIRED");
        assertThat(codeOf(() -> Position.create(1L, "KS", "Kỹ sư", savedGrade(3, 2, true), T0)))
                .isEqualTo("POSITION_GRADE_REQUIRED");
    }

    @Test
    @DisplayName("Không tạo chức danh vào cấp bậc đã ngừng hoạt động")
    void createRejectsInactiveGrade() {
        assertThat(codeOf(() -> Position.create(1L, "KS", "Kỹ sư", savedGrade(3, 1, false), T0)))
                .isEqualTo("POSITION_GRADE_INACTIVE");
    }

    @Test
    @DisplayName("Đổi sang cấp bậc đã ngừng: chặn; chỉ sửa tên khi cấp bậc hiện tại đã ngừng: cho phép")
    void updateWithInactiveGrade() {
        JobGrade current = savedGrade(3, 1, true);
        Position p = Position.create(1L, "KS", "Kỹ sư", current, T0);

        assertThat(codeOf(() -> p.update("KS", "Kỹ sư", savedGrade(4, 1, false), T1)))
                .isEqualTo("POSITION_GRADE_INACTIVE");
        assertThat(p.getJobGradeId()).isEqualTo(3L);

        current.deactivate(0, T1);
        p.update("KS", "Kỹ sư chính", current, T1);
        assertThat(p.getName()).isEqualTo("Kỹ sư chính");

        p.update("KS", "Kỹ sư chính", savedGrade(5, 1, true), T1);
        assertThat(p.getJobGradeId()).isEqualTo(5L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"K", "_KS", "KS QC", "A234567890123456789012345678901"})
    @DisplayName("Mã sai định dạng: POSITION_CODE_INVALID")
    void rejectsInvalidCode(String code) {
        assertThat(codeOf(() -> Position.create(1L, code, "Kỹ sư", savedGrade(3, 1, true), T0)))
                .isEqualTo("POSITION_CODE_INVALID");
    }

    @Test
    @DisplayName("Ngừng hoạt động lúc nào cũng được; hoạt động lại cần cấp bậc đang hoạt động")
    void statusChanges() {
        JobGrade grade = savedGrade(3, 1, true);
        Position p = Position.create(1L, "KS", "Kỹ sư", grade, T0);

        p.deactivate(T1);
        assertThat(p.isActive()).isFalse();

        grade.deactivate(0, T1);
        assertThat(codeOf(() -> p.activate(grade, T1))).isEqualTo("POSITION_GRADE_INACTIVE");
        assertThat(p.isActive()).isFalse();

        grade.activate(T1);
        p.activate(grade, T1);
        assertThat(p.isActive()).isTrue();
    }

    @Test
    @DisplayName("Đã có nhân viên (kể cả đã nghỉ) thì không xóa được")
    void deleteBlockedByEmployees() {
        Position p = Position.create(1L, "KS", "Kỹ sư", savedGrade(3, 1, true), T0);

        assertThat(codeOf(() -> p.ensureDeletable(1))).isEqualTo("POSITION_HAS_EMPLOYEES");
        assertThatCode(() -> p.ensureDeletable(0)).doesNotThrowAnyException();
    }
}
