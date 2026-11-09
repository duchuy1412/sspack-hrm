package vn.sspack.hrm.platform.application.rbac;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.platform.infrastructure.persistence.UserAccountRepository;
import vn.sspack.hrm.shared.security.DataScope;

/**
 * Điểm vào công khai cho module khác hỏi phạm vi dữ liệu của người dùng. Người có nhiều vai trò được xem theo
 * phạm vi rộng nhất (vd HR + Nhân viên → ALL). Đọc từ CSDL nên đổi vai trò có hiệu lực ngay ở bước lọc dữ liệu.
 */
@Service
public class DataScopeService {

    private final UserAccountRepository users;

    public DataScopeService(UserAccountRepository users) {
        this.users = users;
    }

    /** Phạm vi rộng nhất; rỗng khi tài khoản không có vai trò nào (khi đó không được xem dữ liệu của ai). */
    @Transactional(readOnly = true)
    public Optional<DataScope> widestScope(long companyId, long userId) {
        return users.findDataScopes(userId, companyId).stream().reduce(DataScope::widest);
    }
}
