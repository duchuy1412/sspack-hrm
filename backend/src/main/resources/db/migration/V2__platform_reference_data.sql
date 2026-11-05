-- =====================================================================
-- V2: Dữ liệu tham chiếu của nền tảng (PLT-01, nền cho PLT-03)
--   * Công ty SS Pack (single-tenant trong MVP)
--   * 5 vai trò hệ thống kèm phạm vi dữ liệu
--   * Danh mục quyền dạng module:action và gán quyền theo ma trận phân quyền trong SRS (mục 2)
-- Dữ liệu ở đây cần cho mọi môi trường. Tài khoản demo nằm riêng ở db/dev (chỉ profile dev).
-- =====================================================================

INSERT INTO companies (code, name, region_wage)
VALUES ('SSPACK', 'Công ty SS Pack', 1);

-- ---------------------------------------------------------------------
-- Vai trò hệ thống
-- ---------------------------------------------------------------------
INSERT INTO roles (company_id, code, name, data_scope, is_system)
SELECT c.id, r.code, r.name, r.data_scope, TRUE
FROM companies c
CROSS JOIN (VALUES
    ('ADMIN',       'Quản trị hệ thống',  'ALL'),
    ('HR',          'Nhân sự',            'ALL'),
    ('MANAGER',     'Quản lý trực tiếp',  'DEPARTMENT_TREE'),
    ('EMPLOYEE',    'Nhân viên',          'SELF'),
    ('INTERVIEWER', 'Người phỏng vấn',    'ASSIGNED')
) AS r(code, name, data_scope)
WHERE c.code = 'SSPACK';

-- ---------------------------------------------------------------------
-- Danh mục quyền (module:action). Phạm vi dữ liệu do vai trò quyết định, không tách quyền theo phạm vi.
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, module, description) VALUES
    -- Nền tảng
    ('user:manage',              'platform',    'Quản lý tài khoản người dùng'),
    ('role:manage',              'platform',    'Quản lý vai trò và gán quyền'),
    ('audit:read',               'platform',    'Xem nhật ký thao tác'),
    ('approval:configure',       'platform',    'Cấu hình luồng phê duyệt'),
    -- Cơ cấu tổ chức
    ('org:read',                 'org',         'Xem cơ cấu tổ chức, sơ đồ tổ chức'),
    ('org:manage',               'org',         'Quản lý phòng ban, chức danh, cấp bậc'),
    -- Hồ sơ nhân viên
    ('employee:read',            'employee',    'Xem hồ sơ nhân viên trong phạm vi dữ liệu'),
    ('employee:manage',          'employee',    'Tạo, sửa hồ sơ, hợp đồng, quá trình công tác'),
    ('employee:propose-change',  'employee',    'Đề xuất sửa hồ sơ của bản thân'),
    ('employee:approve-change',  'employee',    'Duyệt đề xuất sửa hồ sơ'),
    -- Nghỉ phép
    ('leave:read',               'leave',       'Xem đơn nghỉ và lịch nghỉ trong phạm vi dữ liệu'),
    ('leave:request',            'leave',       'Tạo, hủy đơn nghỉ của bản thân'),
    ('leave:approve',            'leave',       'Duyệt hoặc từ chối đơn nghỉ'),
    ('leave:configure',          'leave',       'Cấu hình loại nghỉ, ngày lễ'),
    -- Chấm công
    ('attendance:read',          'attendance',  'Xem bảng công trong phạm vi dữ liệu'),
    ('attendance:check-in',      'attendance',  'Check-in, check-out, gửi giải trình'),
    ('attendance:confirm',       'attendance',  'Xác nhận bảng công đơn vị'),
    ('attendance:manage',        'attendance',  'Cấu hình ca, import và chốt bảng công'),
    -- Tính lương
    ('payroll:manage',           'payroll',     'Thành phần lương, kỳ lương, điều chỉnh, chốt, báo cáo'),
    ('payroll:parameter-read',   'payroll',     'Xem tham số lương, thuế, bảo hiểm'),
    ('payroll:parameter-manage', 'payroll',     'Quản lý tham số lương, thuế, bảo hiểm'),
    ('payslip:read',             'payroll',     'Xem phiếu lương trong phạm vi dữ liệu'),
    -- Tuyển dụng
    ('recruitment:requisition',  'recruitment', 'Tạo yêu cầu tuyển dụng'),
    ('recruitment:manage',       'recruitment', 'Tin tuyển dụng, ứng viên, lịch phỏng vấn'),
    ('recruitment:evaluate',     'recruitment', 'Chấm điểm ứng viên được giao'),
    -- Dashboard
    ('dashboard:read',           'dashboard',   'Xem dashboard nhân sự trong phạm vi dữ liệu');

-- ---------------------------------------------------------------------
-- Gán quyền cho vai trò (ma trận phân quyền rút gọn trong SRS)
-- ---------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    -- Admin: tài khoản, vai trò, nhật ký, tham số lương; xem tổ chức và hồ sơ
    ('ADMIN', 'user:manage'),
    ('ADMIN', 'role:manage'),
    ('ADMIN', 'audit:read'),
    ('ADMIN', 'org:read'),
    ('ADMIN', 'employee:read'),
    ('ADMIN', 'payroll:parameter-read'),
    ('ADMIN', 'payroll:parameter-manage'),
    -- HR: toàn quyền nghiệp vụ nhân sự trên toàn công ty
    ('HR', 'approval:configure'),
    ('HR', 'org:read'),
    ('HR', 'org:manage'),
    ('HR', 'employee:read'),
    ('HR', 'employee:manage'),
    ('HR', 'employee:approve-change'),
    ('HR', 'leave:read'),
    ('HR', 'leave:approve'),
    ('HR', 'leave:configure'),
    ('HR', 'attendance:read'),
    ('HR', 'attendance:manage'),
    ('HR', 'payroll:manage'),
    ('HR', 'payroll:parameter-read'),
    ('HR', 'payslip:read'),
    ('HR', 'recruitment:requisition'),
    ('HR', 'recruitment:manage'),
    ('HR', 'dashboard:read'),
    -- Manager: xem và duyệt trong cây phòng ban mình quản lý
    ('MANAGER', 'org:read'),
    ('MANAGER', 'employee:read'),
    ('MANAGER', 'leave:read'),
    ('MANAGER', 'leave:approve'),
    ('MANAGER', 'attendance:read'),
    ('MANAGER', 'attendance:confirm'),
    ('MANAGER', 'recruitment:requisition'),
    ('MANAGER', 'dashboard:read'),
    -- Employee: tự phục vụ cho bản thân
    ('EMPLOYEE', 'org:read'),
    ('EMPLOYEE', 'employee:read'),
    ('EMPLOYEE', 'employee:propose-change'),
    ('EMPLOYEE', 'leave:read'),
    ('EMPLOYEE', 'leave:request'),
    ('EMPLOYEE', 'attendance:read'),
    ('EMPLOYEE', 'attendance:check-in'),
    ('EMPLOYEE', 'payslip:read'),
    -- Interviewer: ứng viên được giao
    ('INTERVIEWER', 'recruitment:evaluate')
) AS m(role_code, permission_code)
JOIN roles r ON r.code = m.role_code
JOIN companies c ON c.id = r.company_id AND c.code = 'SSPACK'
JOIN permissions p ON p.code = m.permission_code;
