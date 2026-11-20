package vn.sspack.hrm.org.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Chọn người duyệt cấp 1 (ORG-04): trưởng đơn vị gần nhất đang làm việc, đi từ phòng ban của người gửi lên
 * gốc; bỏ qua phòng chưa có trưởng và không bao giờ chọn chính người gửi.
 */
public final class ApproverChain {

    private ApproverChain() {
    }

    /**
     * @param headsFromSelfToRoot mã nhân viên trưởng từng phòng ban, từ phòng của người gửi tới gốc;
     *                            phần tử {@code null} là phòng chưa có trưởng
     * @param workingEmployeeIds  mã các nhân viên đang làm việc
     */
    public static Optional<Long> pick(List<Long> headsFromSelfToRoot, long requesterEmployeeId,
                                      Set<Long> workingEmployeeIds) {
        for (Long head : headsFromSelfToRoot) {
            if (head != null && head != requesterEmployeeId && workingEmployeeIds.contains(head)) {
                return Optional.of(head);
            }
        }
        return Optional.empty();
    }
}
