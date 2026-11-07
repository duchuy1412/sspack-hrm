-- =====================================================================
-- V4: Quản lý vai trò (PLT-03)
--   * Mô tả vai trò, mốc thời gian tạo/sửa
--   * version cho optimistic locking khi hai Admin sửa cùng một vai trò
-- =====================================================================

ALTER TABLE roles
    ADD COLUMN description VARCHAR(255),
    ADD COLUMN created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version     INT NOT NULL DEFAULT 0;

UPDATE roles SET description = CASE code
    WHEN 'ADMIN'       THEN 'Cấu hình hệ thống, tài khoản, vai trò, tham số'
    WHEN 'HR'          THEN 'Quản lý hồ sơ, hợp đồng, chấm công, tính lương, tuyển dụng'
    WHEN 'MANAGER'     THEN 'Duyệt đơn, xem nhân viên và bảng công của đơn vị'
    WHEN 'EMPLOYEE'    THEN 'Tự phục vụ: hồ sơ, đơn từ, phiếu lương'
    WHEN 'INTERVIEWER' THEN 'Xem hồ sơ ứng viên được giao, chấm điểm phỏng vấn'
END
WHERE is_system;

CREATE INDEX idx_user_roles_role ON user_roles (role_id);
