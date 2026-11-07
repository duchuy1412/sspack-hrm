-- =====================================================================
-- V5: Xem nhật ký thao tác (PLT-04)
--   * entity_type = tên bảng thật ("bảng nào" trong tiêu chí chấp nhận); chuẩn hóa dữ liệu cũ
--   * Thêm hành động LOGOUT
--   * Index lọc theo người thực hiện
--   * Nhật ký chỉ thêm dòng: chặn UPDATE/DELETE bằng trigger
-- =====================================================================

-- Dữ liệu cũ: PLT-01 ghi USER/REFRESH_TOKEN, PLT-03 ghi user/role
UPDATE audit_logs SET entity_type = CASE entity_type
    WHEN 'USER'          THEN 'users'
    WHEN 'user'          THEN 'users'
    WHEN 'REFRESH_TOKEN' THEN 'refresh_tokens'
    WHEN 'role'          THEN 'roles'
    ELSE entity_type
END
WHERE entity_type IN ('USER', 'user', 'REFRESH_TOKEN', 'role');

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check
    CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'EXPORT'));

CREATE INDEX idx_audit_user ON audit_logs (company_id, user_id, created_at DESC);

CREATE FUNCTION audit_logs_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs chỉ cho phép thêm dòng (% bị chặn)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER audit_logs_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only();

-- TRUNCATE không đi qua trigger FOR EACH ROW
CREATE TRIGGER audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_append_only();
