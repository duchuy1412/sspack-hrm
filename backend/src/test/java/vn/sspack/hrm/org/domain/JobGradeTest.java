package vn.sspack.hrm.org.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vn.sspack.hrm.shared.error.ApiException;

/** ORG-02: quy tắc của cấp bậc và khung lương. */
class JobGradeTest {

    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 9, 9, 0, 0, 0, ZoneOffset.ofHours(7));
    static final OffsetDateTime T1 = T0.plusHours(1);

    static JobGrade grade(String min, String max) {
        return JobGrade.create(1L, "g3", " Chuyên viên ", new BigDecimal(min), new BigDecimal(max), T0);
    }

    static String codeOf(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e.code();
        }
        throw new AssertionError("Không ném lỗi nghiệp vụ");
    }

    @Nested
    @DisplayName("Tạo và sửa")
    class CreateAndUpdate {

        @Test
        @DisplayName("Mã viết hoa, tên bỏ khoảng trắng thừa, lương giữ đúng số đồng")
        void normalizes() {
            JobGrade g = grade("12000000", "20000000");

            assertThat(g.getCode()).isEqualTo("G3");
            assertThat(g.getName()).isEqualTo("Chuyên viên");
            assertThat(g.getMinSalary()).isEqualByComparingTo("12000000");
            assertThat(g.getMaxSalary()).isEqualByComparingTo("20000000");
            assertThat(g.getMinSalary().scale()).isZero();
            assertThat(g.isActive()).isTrue();
            assertThat(g.getUpdatedAt()).isEqualTo(T0);
        }

        @Test
        @DisplayName("Khung một mức (tối thiểu = tối đa) hợp lệ; '1.2E7' được chuẩn về scale 0")
        void singlePointRangeAndScale() {
            JobGrade g = grade("1.2E7", "12000000");

            assertThat(g.getMinSalary().toPlainString()).isEqualTo("12000000");
            assertThat(g.getMaxSalary()).isEqualByComparingTo(g.getMinSalary());
        }

        @Test
        @DisplayName("Tối thiểu lớn hơn tối đa: JOB_GRADE_RANGE_INVALID")
        void rejectsInvertedRange() {
            assertThat(codeOf(() -> grade("20000000", "12000000"))).isEqualTo("JOB_GRADE_RANGE_INVALID");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "12000000.5", "1000000000000000"})
        @DisplayName("Lương bằng 0, âm, có số lẻ hoặc quá 15 chữ số: JOB_GRADE_SALARY_INVALID")
        void rejectsInvalidSalary(String value) {
            assertThat(codeOf(() -> grade(value, "20000000"))).isEqualTo("JOB_GRADE_SALARY_INVALID");
            assertThat(codeOf(() -> grade("1000000", value))).isEqualTo("JOB_GRADE_SALARY_INVALID");
        }

        @Test
        @DisplayName("Thiếu lương: JOB_GRADE_SALARY_INVALID")
        void rejectsMissingSalary() {
            assertThat(codeOf(() -> JobGrade.create(1L, "G1", "Cấp 1", null, BigDecimal.TEN, T0)))
                    .isEqualTo("JOB_GRADE_SALARY_INVALID");
        }

        @ParameterizedTest
        @ValueSource(strings = {"G", "-G1", "G 1", "G1.2", "ABCDEFGHIJKLMNOPQRSTU"})
        @DisplayName("Mã sai định dạng: JOB_GRADE_CODE_INVALID")
        void rejectsInvalidCode(String code) {
            assertThat(codeOf(() -> JobGrade.create(1L, code, "Cấp", BigDecimal.ONE, BigDecimal.TEN, T0)))
                    .isEqualTo("JOB_GRADE_CODE_INVALID");
        }

        @Test
        @DisplayName("Sửa lỗi thì không đổi gì trên cấp bậc")
        void failedUpdateLeavesGradeUnchanged() {
            JobGrade g = grade("12000000", "20000000");

            codeOf(() -> g.update("G9", "Mới", new BigDecimal("30000000"), new BigDecimal("1"), T1));

            assertThat(g.getCode()).isEqualTo("G3");
            assertThat(g.getMinSalary()).isEqualByComparingTo("12000000");
            assertThat(g.getUpdatedAt()).isEqualTo(T0);
        }
    }

    @Nested
    @DisplayName("Ngừng hoạt động và xóa")
    class Lifecycle {

        @Test
        @DisplayName("Còn chức danh đang hoạt động thì không ngừng được")
        void deactivateBlockedByActivePositions() {
            JobGrade g = grade("1000000", "2000000");

            assertThat(codeOf(() -> g.deactivate(2, T1))).isEqualTo("JOB_GRADE_HAS_ACTIVE_POSITIONS");
            assertThat(g.isActive()).isTrue();

            g.deactivate(0, T1);
            assertThat(g.isActive()).isFalse();
            assertThat(g.getUpdatedAt()).isEqualTo(T1);

            g.activate(T1);
            assertThat(g.isActive()).isTrue();
        }

        @Test
        @DisplayName("Còn chức danh (kể cả đã ngừng) thì không xóa được")
        void deleteBlockedByPositions() {
            JobGrade g = grade("1000000", "2000000");

            assertThat(codeOf(() -> g.ensureDeletable(1))).isEqualTo("JOB_GRADE_HAS_POSITIONS");
            assertThatCode(() -> g.ensureDeletable(0)).doesNotThrowAnyException();
        }
    }
}
