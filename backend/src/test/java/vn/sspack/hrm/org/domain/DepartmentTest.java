package vn.sspack.hrm.org.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import vn.sspack.hrm.shared.error.ApiException;

/** ORG-01: quy tắc của cây phòng ban. */
class DepartmentTest {

    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 7, 9, 0, 0, 0, ZoneOffset.ofHours(7));

    /** Tạo phòng ban như sau khi lưu: có id và path đầy đủ. */
    static Department saved(long id, Department parent, String code) {
        Department d = Department.create(1L, parent, code, "Phòng " + code, T0);
        ReflectionTestUtils.setField(d, "id", id);
        d.completePath();
        return d;
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
    @DisplayName("Tạo")
    class Create {

        @Test
        @DisplayName("Path nối id các cấp từ gốc; không giới hạn số cấp")
        void buildsPathWithoutDepthLimit() {
            Department parent = saved(1, null, "NM");
            for (int level = 2; level <= 12; level++) {
                parent = saved(level, parent, "L" + level);
            }

            assertThat(parent.getPath()).isEqualTo("/1/2/3/4/5/6/7/8/9/10/11/12/");
            assertThat(parent.depth()).isEqualTo(12);
            assertThat(parent.getParentId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("Mã chuẩn hóa chữ in hoa; tên cắt khoảng trắng")
        void normalizes() {
            Department d = Department.create(1L, null, " xuong-in ", "  Xưởng In ", T0);

            assertThat(d.getCode()).isEqualTo("XUONG-IN");
            assertThat(d.getName()).isEqualTo("Xưởng In");
            assertThat(d.isActive()).isTrue();
        }

        @Test
        @DisplayName("Mã sai định dạng bị từ chối")
        void rejectsInvalidCode() {
            for (String code : new String[] {"A", "-AB", "PHÒNG", "A B", "", null, "A".repeat(31)}) {
                assertThat(codeOf(() -> Department.create(1L, null, code, "x", T0))).as(String.valueOf(code))
                        .isEqualTo("DEPARTMENT_CODE_INVALID");
            }
        }

        @Test
        @DisplayName("Không tạo dưới phòng ban đã ngừng hoạt động")
        void rejectsInactiveParent() {
            Department parent = saved(1, null, "OLD");
            parent.deactivate(0, 0, T0);

            assertThat(codeOf(() -> Department.create(1L, parent, "NEW", "x", T0)))
                    .isEqualTo("DEPARTMENT_PARENT_INACTIVE");
        }
    }

    @Nested
    @DisplayName("Đổi cấp cha")
    class Move {

        final Department nm = saved(1, null, "NM");
        final Department xi = saved(2, nm, "XI");
        final Department offset = saved(3, xi, "OFFSET");
        final Department kvp = saved(4, null, "KVP");

        @Test
        @DisplayName("Chuyển sang cấp cha khác: path mới, trả path cũ để cập nhật nhánh con")
        void movesAndReturnsOldPath() {
            assertThat(xi.moveUnder(kvp, T0.plusHours(1))).contains("/1/2/");

            assertThat(xi.getPath()).isEqualTo("/4/2/");
            assertThat(xi.getParentId()).isEqualTo(4L);
            assertThat(xi.getUpdatedAt()).isEqualTo(T0.plusHours(1));
        }

        @Test
        @DisplayName("Đưa lên cấp cao nhất")
        void movesToRoot() {
            assertThat(xi.moveUnder(null, T0)).contains("/1/2/");

            assertThat(xi.getPath()).isEqualTo("/2/");
            assertThat(xi.getParentId()).isNull();
            assertThat(xi.depth()).isEqualTo(1);
        }

        @Test
        @DisplayName("Cấp cha không đổi: không làm gì")
        void sameParentIsNoOp() {
            assertThat(xi.moveUnder(nm, T0)).isEmpty();
            assertThat(nm.moveUnder(null, T0)).isEmpty();
        }

        @Test
        @DisplayName("Không chuyển vào chính nó hoặc phòng ban cấp dưới (tạo vòng)")
        void rejectsCycles() {
            assertThat(codeOf(() -> xi.moveUnder(xi, T0))).isEqualTo("DEPARTMENT_MOVE_CYCLE");
            assertThat(codeOf(() -> nm.moveUnder(offset, T0))).isEqualTo("DEPARTMENT_MOVE_CYCLE");
            assertThat(nm.getPath()).isEqualTo("/1/");
        }

        @Test
        @DisplayName("Không chuyển vào phòng ban đã ngừng hoạt động")
        void rejectsInactiveTarget() {
            kvp.deactivate(0, 0, T0);

            assertThat(codeOf(() -> xi.moveUnder(kvp, T0))).isEqualTo("DEPARTMENT_PARENT_INACTIVE");
        }

        @Test
        @DisplayName("Id có tiền tố giống nhau không bị coi là cấp dưới (/1/ và /12/)")
        void prefixOfIdIsNotDescendant() {
            Department twelve = saved(12, null, "D12");

            assertThat(nm.moveUnder(twelve, T0)).contains("/1/");
            assertThat(nm.getPath()).isEqualTo("/12/1/");
        }
    }

    @Nested
    @DisplayName("Xóa và ngừng hoạt động")
    class Remove {

        final Department d = saved(5, null, "KHO");

        @Test
        @DisplayName("Xóa: còn phòng ban con hoặc từng có nhân viên thì chặn, kèm số lượng")
        void deleteRules() {
            d.ensureDeletable(0, 0);

            assertThatThrownBy(() -> d.ensureDeletable(2, 0))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.code()).isEqualTo("DEPARTMENT_HAS_CHILDREN");
                        assertThat(e.properties()).containsEntry("childCount", 2L);
                    });
            assertThatThrownBy(() -> d.ensureDeletable(0, 1))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.code()).isEqualTo("DEPARTMENT_HAS_EMPLOYEES");
                        assertThat(e.properties()).containsEntry("employeeCount", 1L);
                    });
        }

        @Test
        @DisplayName("Ngừng hoạt động: chặn khi còn con đang hoạt động hoặc nhân viên đang làm")
        void deactivateRules() {
            assertThat(codeOf(() -> d.deactivate(1, 0, T0))).isEqualTo("DEPARTMENT_HAS_ACTIVE_CHILDREN");
            assertThat(codeOf(() -> d.deactivate(0, 3, T0))).isEqualTo("DEPARTMENT_HAS_ACTIVE_EMPLOYEES");
            assertThat(d.isActive()).isTrue();

            d.deactivate(0, 0, T0);
            assertThat(d.isActive()).isFalse();
        }

        @Test
        @DisplayName("Hoạt động lại: cấp cha phải đang hoạt động")
        void activateRequiresActiveParent() {
            Department parent = saved(6, null, "PA");
            Department child = saved(7, parent, "CH");
            child.deactivate(0, 0, T0);
            parent.deactivate(0, 0, T0);

            assertThat(codeOf(() -> child.activate(parent, T0))).isEqualTo("DEPARTMENT_PARENT_INACTIVE");

            parent.activate(null, T0);
            child.activate(parent, T0);
            assertThat(child.isActive()).isTrue();
        }
    }
}
