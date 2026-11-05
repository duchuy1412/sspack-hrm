-- =====================================================================
-- HRM MVP — PostgreSQL schema (v0.1)
-- Phạm vi: Ưu tiên 1 + Ưu tiên 2 theo SRS
-- Quy ước:
--   * Khóa chính BIGINT identity, tên bảng số nhiều, snake_case
--   * Mọi bảng nghiệp vụ có company_id (single-tenant hôm nay, sẵn sàng multi-tenant)
--   * Cột audit: created_at, created_by, updated_at, updated_by; version cho optimistic locking
--   * Không xóa cứng dữ liệu nghiệp vụ: dùng cờ deleted / trạng thái
--   * Dữ liệu nhạy cảm (số CCCD, tài khoản ngân hàng, lương) lưu dạng thường để truy vấn,
--     tổng hợp trực tiếp bằng SQL; bảo vệ bằng phân quyền API, che bớt khi hiển thị và audit log
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS hrm;
SET search_path TO hrm;

-- ---------------------------------------------------------------------
-- 0. CÔNG TY
-- ---------------------------------------------------------------------
CREATE TABLE companies (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    tax_code        VARCHAR(20),
    address         VARCHAR(500),
    region_wage     SMALLINT NOT NULL DEFAULT 1 CHECK (region_wage BETWEEN 1 AND 4), -- vùng lương tối thiểu
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 1. NỀN TẢNG (PLT): tài khoản, phân quyền, audit, thông báo, phê duyệt
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id          BIGINT NOT NULL REFERENCES companies(id),
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,                 -- bcrypt
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    failed_login_count  SMALLINT NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    preferred_locale    VARCHAR(5) NOT NULL DEFAULT 'vi',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             INT NOT NULL DEFAULT 0,
    UNIQUE (company_id, email)
);

CREATE TABLE roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT NOT NULL REFERENCES companies(id),
    code        VARCHAR(50)  NOT NULL,          -- ADMIN, HR, MANAGER, EMPLOYEE, INTERVIEWER
    name        VARCHAR(100) NOT NULL,
    data_scope  VARCHAR(20)  NOT NULL DEFAULT 'SELF'
                CHECK (data_scope IN ('ALL','DEPARTMENT_TREE','DEPARTMENT','SELF','ASSIGNED')),
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (company_id, code)
);

CREATE TABLE permissions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,   -- vd: employee:read, leave:approve, payroll:close
    module      VARCHAR(30)  NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(100) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(100) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,           -- 30 phút
    used_at     TIMESTAMPTZ
);

CREATE TABLE audit_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT NOT NULL,
    user_id      BIGINT,
    action       VARCHAR(10) NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT')),
    entity_type  VARCHAR(60) NOT NULL,
    entity_id    BIGINT,
    old_value    JSONB,
    new_value    JSONB,
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_logs (company_id, created_at DESC);

CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(50)  NOT NULL,          -- LEAVE_PENDING, LEAVE_APPROVED, PAYSLIP_PUBLISHED...
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    link        VARCHAR(500),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE attachments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    owner_type    VARCHAR(40) NOT NULL,          -- EMPLOYEE, CONTRACT, CANDIDATE, LEAVE_REQUEST...
    owner_id      BIGINT NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL CHECK (size_bytes <= 10485760),   -- 10 MB
    storage_key   VARCHAR(500) NOT NULL,         -- key trên MinIO
    uploaded_by   BIGINT REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachments_owner ON attachments (owner_type, owner_id);

-- Luồng phê duyệt cấu hình được (PLT-06)
CREATE TABLE approval_workflows (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    request_type  VARCHAR(40) NOT NULL,          -- LEAVE, LEAVE_CANCEL, ATTENDANCE_ADJUST, PROFILE_CHANGE, REQUISITION
    name          VARCHAR(150) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (company_id, request_type)
);

CREATE TABLE approval_workflow_steps (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_id     BIGINT NOT NULL REFERENCES approval_workflows(id) ON DELETE CASCADE,
    step_order      SMALLINT NOT NULL CHECK (step_order BETWEEN 1 AND 3),
    approver_type   VARCHAR(30) NOT NULL
                    CHECK (approver_type IN ('DIRECT_MANAGER','DEPARTMENT_HEAD','ROLE','USER')),
    approver_role_id BIGINT REFERENCES roles(id),
    approver_user_id BIGINT REFERENCES users(id),
    UNIQUE (workflow_id, step_order)
);

-- Một phiếu duyệt gắn với bất kỳ đối tượng nghiệp vụ nào
CREATE TABLE approval_requests (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT NOT NULL REFERENCES companies(id),
    workflow_id    BIGINT NOT NULL REFERENCES approval_workflows(id),
    request_type   VARCHAR(40) NOT NULL,
    reference_id   BIGINT NOT NULL,              -- id của leave_requests / job_requisitions...
    requester_id   BIGINT NOT NULL REFERENCES users(id),
    current_step   SMALLINT NOT NULL DEFAULT 1,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    UNIQUE (request_type, reference_id)
);
CREATE INDEX idx_approval_pending ON approval_requests (company_id, status) WHERE status = 'PENDING';

CREATE TABLE approval_actions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    approval_request_id BIGINT NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    step_order          SMALLINT NOT NULL,
    approver_id         BIGINT NOT NULL REFERENCES users(id),
    decision            VARCHAR(10) NOT NULL CHECK (decision IN ('APPROVE','REJECT')),
    comment             VARCHAR(1000),
    acted_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (decision = 'APPROVE' OR comment IS NOT NULL)   -- từ chối bắt buộc có lý do
);

-- ---------------------------------------------------------------------
-- 2. CƠ CẤU TỔ CHỨC (ORG)
-- ---------------------------------------------------------------------
CREATE TABLE departments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    parent_id     BIGINT REFERENCES departments(id),
    code          VARCHAR(30)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    path          VARCHAR(1000) NOT NULL,        -- materialized path: /1/4/12/ để truy vấn cây nhanh
    head_employee_id BIGINT,                     -- FK thêm sau khi có bảng employees
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       INT NOT NULL DEFAULT 0,
    UNIQUE (company_id, code)
);
CREATE INDEX idx_departments_path ON departments (path varchar_pattern_ops);

CREATE TABLE job_grades (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT NOT NULL REFERENCES companies(id),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    min_salary  NUMERIC(15,0),
    max_salary  NUMERIC(15,0),
    UNIQUE (company_id, code),
    CHECK (min_salary IS NULL OR max_salary IS NULL OR min_salary <= max_salary)
);

CREATE TABLE positions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    code          VARCHAR(30)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    job_grade_id  BIGINT REFERENCES job_grades(id),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (company_id, code)
);

-- ---------------------------------------------------------------------
-- 3. HỒ SƠ NHÂN VIÊN (EMP)
-- ---------------------------------------------------------------------
CREATE TABLE employees (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT NOT NULL REFERENCES companies(id),
    user_id           BIGINT UNIQUE REFERENCES users(id),
    employee_code     VARCHAR(30)  NOT NULL,
    full_name         VARCHAR(150) NOT NULL,
    gender            VARCHAR(10) CHECK (gender IN ('MALE','FEMALE','OTHER')),
    date_of_birth     DATE,
    personal_email    VARCHAR(255),
    work_email        VARCHAR(255),
    phone             VARCHAR(20),
    address           VARCHAR(500),
    id_number         VARCHAR(20),               -- số CCCD
    tax_code          VARCHAR(20),
    social_insurance_no VARCHAR(20),
    bank_name         VARCHAR(100),
    bank_account      VARCHAR(30),               -- số tài khoản
    department_id     BIGINT NOT NULL REFERENCES departments(id),
    position_id       BIGINT REFERENCES positions(id),
    manager_id        BIGINT REFERENCES employees(id),
    hire_date         DATE NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PROBATION'
                      CHECK (status IN ('PROBATION','ACTIVE','ON_LEAVE','RESIGNED','TERMINATED')),
    termination_date  DATE,
    termination_reason VARCHAR(500),
    avatar_key        VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by        BIGINT,
    version           INT NOT NULL DEFAULT 0,
    UNIQUE (company_id, employee_code)
);
CREATE INDEX idx_employees_department ON employees (department_id);
CREATE INDEX idx_employees_status ON employees (company_id, status);
CREATE INDEX idx_employees_name ON employees (company_id, lower(full_name));

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_head FOREIGN KEY (head_employee_id) REFERENCES employees(id);

CREATE TABLE employee_dependents (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id     BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    full_name       VARCHAR(150) NOT NULL,
    relationship    VARCHAR(30)  NOT NULL,       -- CHILD, PARENT, SPOUSE...
    date_of_birth   DATE,
    tax_code        VARCHAR(20),
    deduction_from  DATE NOT NULL,               -- tháng bắt đầu giảm trừ gia cảnh
    deduction_to    DATE
);

CREATE TABLE employee_educations (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id   BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    school        VARCHAR(255) NOT NULL,
    major         VARCHAR(150),
    degree        VARCHAR(50),
    graduated_year SMALLINT
);

CREATE TABLE contracts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT NOT NULL REFERENCES companies(id),
    employee_id      BIGINT NOT NULL REFERENCES employees(id),
    contract_no      VARCHAR(50) NOT NULL,
    contract_type    VARCHAR(20) NOT NULL CHECK (contract_type IN ('PROBATION','FIXED_TERM','INDEFINITE')),
    start_date       DATE NOT NULL,
    end_date         DATE,
    base_salary      NUMERIC(15,0) NOT NULL CHECK (base_salary >= 0),  -- lương cơ bản (VND)
    insurance_salary NUMERIC(15,0) CHECK (insurance_salary >= 0),      -- lương đóng bảo hiểm (VND)
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','TERMINATED')),
    signed_date      DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          INT NOT NULL DEFAULT 0,
    UNIQUE (company_id, contract_no),
    CHECK (end_date IS NULL OR end_date >= start_date),
    CHECK (contract_type = 'INDEFINITE' OR end_date IS NOT NULL)
);
CREATE INDEX idx_contracts_expiring ON contracts (end_date) WHERE status = 'ACTIVE';

-- Quá trình công tác: chỉ thêm, không ghi đè (EMP-03)
CREATE TABLE employment_histories (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id     BIGINT NOT NULL REFERENCES employees(id),
    event_type      VARCHAR(20) NOT NULL
                    CHECK (event_type IN ('HIRE','TRANSFER','PROMOTION','SALARY_CHANGE','RESIGN','TERMINATE')),
    effective_date  DATE NOT NULL,
    department_id   BIGINT REFERENCES departments(id),
    position_id     BIGINT REFERENCES positions(id),
    note            VARCHAR(1000),
    decision_no     VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT
);
CREATE INDEX idx_emp_history ON employment_histories (employee_id, effective_date DESC);

-- Nhân viên đề xuất sửa hồ sơ, HR duyệt (ESS-02)
CREATE TABLE profile_change_requests (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id   BIGINT NOT NULL REFERENCES employees(id),
    changes       JSONB NOT NULL,                -- {"phone": {"old": "...", "new": "..."}}
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at   TIMESTAMPTZ,
    reviewed_by   BIGINT REFERENCES users(id)
);

-- ---------------------------------------------------------------------
-- 4. NGHỈ PHÉP (LEV)
-- ---------------------------------------------------------------------
CREATE TABLE holidays (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT NOT NULL REFERENCES companies(id),
    holiday_date DATE NOT NULL,
    name        VARCHAR(150) NOT NULL,
    UNIQUE (company_id, holiday_date)
);

CREATE TABLE leave_types (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT NOT NULL REFERENCES companies(id),
    code               VARCHAR(30)  NOT NULL,    -- ANNUAL, SICK, PERSONAL_PAID, UNPAID, MATERNITY
    name               VARCHAR(100) NOT NULL,
    deduct_balance     BOOLEAN NOT NULL DEFAULT FALSE,
    is_paid            BOOLEAN NOT NULL DEFAULT TRUE,
    max_days_per_request NUMERIC(5,1),
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (company_id, code)
);

-- Quỹ phép theo năm (LEV, quy tắc nghiệp vụ)
CREATE TABLE leave_balances (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id     BIGINT NOT NULL REFERENCES employees(id),
    leave_type_id   BIGINT NOT NULL REFERENCES leave_types(id),
    year            SMALLINT NOT NULL,
    entitled_days   NUMERIC(5,1) NOT NULL DEFAULT 0,   -- được cấp / cộng dồn
    carried_days    NUMERIC(5,1) NOT NULL DEFAULT 0,   -- chuyển từ năm trước
    carry_expire_on DATE,                              -- hết hạn phép cũ (vd 31/03)
    used_days       NUMERIC(5,1) NOT NULL DEFAULT 0,   -- đã duyệt cấp cuối
    pending_days    NUMERIC(5,1) NOT NULL DEFAULT 0,   -- đang giữ tạm
    version         INT NOT NULL DEFAULT 0,
    UNIQUE (employee_id, leave_type_id, year),
    CHECK (used_days >= 0 AND pending_days >= 0)
);

-- Lịch sử biến động quỹ phép để giải trình
CREATE TABLE leave_balance_transactions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leave_balance_id BIGINT NOT NULL REFERENCES leave_balances(id),
    txn_type         VARCHAR(20) NOT NULL
                     CHECK (txn_type IN ('ACCRUE','CARRY_OVER','EXPIRE','HOLD','RELEASE','DEDUCT','ADJUST')),
    days             NUMERIC(5,1) NOT NULL,
    leave_request_id BIGINT,
    note             VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE leave_requests (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    employee_id     BIGINT NOT NULL REFERENCES employees(id),
    leave_type_id   BIGINT NOT NULL REFERENCES leave_types(id),
    start_date      DATE NOT NULL,
    start_half      VARCHAR(10) NOT NULL DEFAULT 'FULL' CHECK (start_half IN ('FULL','MORNING','AFTERNOON')),
    end_date        DATE NOT NULL,
    end_half        VARCHAR(10) NOT NULL DEFAULT 'FULL' CHECK (end_half IN ('FULL','MORNING','AFTERNOON')),
    total_days      NUMERIC(5,1) NOT NULL CHECK (total_days > 0),  -- đã trừ cuối tuần, ngày lễ
    reason          VARCHAR(1000),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','CANCELLED','CANCEL_PENDING')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         INT NOT NULL DEFAULT 0,
    CHECK (end_date >= start_date)
);
CREATE INDEX idx_leave_requests_emp ON leave_requests (employee_id, start_date);
CREATE INDEX idx_leave_requests_calendar ON leave_requests (company_id, start_date, end_date)
    WHERE status IN ('PENDING','APPROVED');

ALTER TABLE leave_balance_transactions
    ADD CONSTRAINT fk_lbt_request FOREIGN KEY (leave_request_id) REFERENCES leave_requests(id);

-- Chặn hai đơn chồng ngày (quy tắc nghiệp vụ LEV) bằng exclusion constraint
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE leave_requests ADD CONSTRAINT ex_leave_no_overlap
    EXCLUDE USING gist (employee_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
    WHERE (status IN ('PENDING','APPROVED','CANCEL_PENDING'));

-- ---------------------------------------------------------------------
-- 5. CHẤM CÔNG (ATT)
-- ---------------------------------------------------------------------
CREATE TABLE shifts (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT NOT NULL REFERENCES companies(id),
    code              VARCHAR(20) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    start_time        TIME NOT NULL,
    end_time          TIME NOT NULL,
    break_start       TIME,
    break_end         TIME,
    late_grace_minutes SMALLINT NOT NULL DEFAULT 0,
    work_days         SMALLINT[] NOT NULL DEFAULT '{1,2,3,4,5}',  -- ISO: 1=Thứ 2 ... 7=CN
    UNIQUE (company_id, code)
);

CREATE TABLE shift_assignments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shift_id      BIGINT NOT NULL REFERENCES shifts(id),
    department_id BIGINT REFERENCES departments(id),
    employee_id   BIGINT REFERENCES employees(id),
    effective_from DATE NOT NULL,
    effective_to   DATE,
    CHECK ((department_id IS NULL) <> (employee_id IS NULL))   -- gán theo phòng ban HOẶC nhân viên
);

CREATE TABLE attendance_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id  BIGINT NOT NULL REFERENCES employees(id),
    log_time     TIMESTAMPTZ NOT NULL,
    log_type     VARCHAR(10) NOT NULL CHECK (log_type IN ('IN','OUT')),
    source       VARCHAR(10) NOT NULL CHECK (source IN ('WEB','IMPORT','ADJUST')),
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendance_logs_emp_time ON attendance_logs (employee_id, log_time);

CREATE TABLE attendance_adjustments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id  BIGINT NOT NULL REFERENCES employees(id),
    work_date    DATE NOT NULL,
    check_in     TIME,
    check_out    TIME,
    reason       VARCHAR(1000) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE timesheets (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    period        CHAR(7) NOT NULL,              -- 'YYYY-MM'
    standard_days NUMERIC(4,1) NOT NULL,         -- công chuẩn của tháng
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                  CHECK (status IN ('OPEN','HR_CLOSED','CONFIRMED','LOCKED')),
    closed_at     TIMESTAMPTZ,
    UNIQUE (company_id, period)
);

CREATE TABLE timesheet_entries (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    timesheet_id     BIGINT NOT NULL REFERENCES timesheets(id) ON DELETE CASCADE,
    employee_id      BIGINT NOT NULL REFERENCES employees(id),
    work_days        NUMERIC(4,1) NOT NULL DEFAULT 0,
    paid_leave_days  NUMERIC(4,1) NOT NULL DEFAULT 0,
    unpaid_leave_days NUMERIC(4,1) NOT NULL DEFAULT 0,
    late_count       SMALLINT NOT NULL DEFAULT 0,
    late_minutes     INT NOT NULL DEFAULT 0,
    early_leave_minutes INT NOT NULL DEFAULT 0,
    overtime_hours   NUMERIC(5,1) NOT NULL DEFAULT 0,
    manager_confirmed_at TIMESTAMPTZ,
    UNIQUE (timesheet_id, employee_id)
);

-- ---------------------------------------------------------------------
-- 6. TÍNH LƯƠNG (PAY)
-- ---------------------------------------------------------------------
-- Tham số theo quy định, có ngày hiệu lực (PAY-03). Không hardcode trong code.
CREATE TABLE payroll_parameters (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    code            VARCHAR(50) NOT NULL,   -- PERSONAL_DEDUCTION, DEPENDENT_DEDUCTION, SI_EMP_RATE,
                                            -- HI_EMP_RATE, UI_EMP_RATE, SI_CAP, REGION_MIN_WAGE_1...
    value           NUMERIC(18,4) NOT NULL,
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    legal_reference VARCHAR(255),           -- văn bản quy định, để kiểm chứng
    UNIQUE (company_id, code, effective_from)
);

-- Biểu thuế lũy tiến từng phần, có ngày hiệu lực
CREATE TABLE tax_brackets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    effective_from  DATE NOT NULL,
    bracket_no      SMALLINT NOT NULL,
    lower_bound     NUMERIC(15,0) NOT NULL,
    upper_bound     NUMERIC(15,0),          -- NULL = không giới hạn
    rate            NUMERIC(5,4) NOT NULL,
    UNIQUE (company_id, effective_from, bracket_no)
);

CREATE TABLE salary_components (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    code          VARCHAR(40)  NOT NULL,     -- dùng làm biến trong công thức: LUONG_CB, PC_AN_TRUA...
    name          VARCHAR(150) NOT NULL,
    category      VARCHAR(20)  NOT NULL
                  CHECK (category IN ('EARNING','ALLOWANCE','BONUS','DEDUCTION','INSURANCE','TAX','INFO')),
    is_taxable    BOOLEAN NOT NULL DEFAULT TRUE,
    tax_exempt_cap NUMERIC(15,0),            -- phần miễn thuế tối đa (vd phụ cấp ăn trưa)
    formula       TEXT,                      -- vd: LUONG_CB * NGAY_CONG / CONG_CHUAN
    calc_order    SMALLINT NOT NULL DEFAULT 100,
    show_on_payslip BOOLEAN NOT NULL DEFAULT TRUE,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (company_id, code)
);

-- Giá trị cố định theo nhân viên (phụ cấp riêng...), có hiệu lực
CREATE TABLE employee_salary_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id         BIGINT NOT NULL REFERENCES employees(id),
    salary_component_id BIGINT NOT NULL REFERENCES salary_components(id),
    amount              NUMERIC(15,0) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE
);

CREATE TABLE payroll_periods (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    period        CHAR(7) NOT NULL,          -- 'YYYY-MM'
    timesheet_id  BIGINT NOT NULL REFERENCES timesheets(id),
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT','CALCULATING','CALCULATED','CLOSED','PAID')),
    total_employees INT,
    processed_employees INT NOT NULL DEFAULT 0,   -- tiến độ khi chạy qua Kafka
    calculated_at TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    closed_by     BIGINT REFERENCES users(id),
    version       INT NOT NULL DEFAULT 0,
    UNIQUE (company_id, period)
);

CREATE TABLE payslips (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payroll_period_id BIGINT NOT NULL REFERENCES payroll_periods(id),
    employee_id       BIGINT NOT NULL REFERENCES employees(id),
    gross_income      NUMERIC(15,0) NOT NULL,
    taxable_income    NUMERIC(15,0),
    insurance         NUMERIC(15,0),
    pit               NUMERIC(15,0),         -- thuế TNCN
    net_pay           NUMERIC(15,0) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'CALCULATED'
                      CHECK (status IN ('CALCULATED','ERROR','PUBLISHED')),
    error_message     VARCHAR(1000),
    pdf_key           VARCHAR(500),
    published_at      TIMESTAMPTZ,
    UNIQUE (payroll_period_id, employee_id)
);

CREATE INDEX idx_payslips_employee ON payslips (employee_id);

CREATE TABLE payslip_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payslip_id          BIGINT NOT NULL REFERENCES payslips(id) ON DELETE CASCADE,
    salary_component_id BIGINT NOT NULL REFERENCES salary_components(id),
    amount              NUMERIC(15,0) NOT NULL,
    UNIQUE (payslip_id, salary_component_id)
);

-- Điều chỉnh thủ công phải có lý do và lưu vết (PAY-05)
CREATE TABLE payslip_adjustments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payslip_id          BIGINT NOT NULL REFERENCES payslips(id) ON DELETE CASCADE,
    salary_component_id BIGINT NOT NULL REFERENCES salary_components(id),
    old_amount          NUMERIC(15,0),
    new_amount          NUMERIC(15,0) NOT NULL,
    reason              VARCHAR(1000) NOT NULL,
    adjusted_by         BIGINT NOT NULL REFERENCES users(id),
    adjusted_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 7. TUYỂN DỤNG (REC)
-- ---------------------------------------------------------------------
CREATE TABLE job_requisitions (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT NOT NULL REFERENCES companies(id),
    department_id  BIGINT NOT NULL REFERENCES departments(id),
    position_id    BIGINT REFERENCES positions(id),
    title          VARCHAR(200) NOT NULL,
    headcount      SMALLINT NOT NULL CHECK (headcount > 0),
    description    TEXT,
    budget_min     NUMERIC(15,0),
    budget_max     NUMERIC(15,0),
    requested_by   BIGINT NOT NULL REFERENCES users(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','OPEN','FILLED','CLOSED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE job_postings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requisition_id  BIGINT NOT NULL REFERENCES job_requisitions(id),
    slug            VARCHAR(200) NOT NULL UNIQUE,   -- URL công khai
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    location        VARCHAR(150),
    published_at    TIMESTAMPTZ,
    closes_at       DATE,
    is_published    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE candidates (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT NOT NULL REFERENCES companies(id),
    full_name     VARCHAR(150) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    cv_key        VARCHAR(500),
    source        VARCHAR(30) NOT NULL DEFAULT 'CAREER_PAGE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, email)
);

CREATE TABLE applications (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id   BIGINT NOT NULL REFERENCES candidates(id),
    posting_id     BIGINT NOT NULL REFERENCES job_postings(id),
    stage          VARCHAR(20) NOT NULL DEFAULT 'NEW'
                   CHECK (stage IN ('NEW','SCREENING','INTERVIEW','OFFER','HIRED','REJECTED')),
    stage_order    INT NOT NULL DEFAULT 0,          -- vị trí trong cột kanban
    rejected_reason VARCHAR(500),
    hired_employee_id BIGINT REFERENCES employees(id),  -- REC-06
    applied_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        INT NOT NULL DEFAULT 0,
    UNIQUE (candidate_id, posting_id)
);
CREATE INDEX idx_applications_board ON applications (posting_id, stage, stage_order);

CREATE TABLE interviews (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    round_no        SMALLINT NOT NULL DEFAULT 1,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    duration_minutes SMALLINT NOT NULL DEFAULT 60,
    location        VARCHAR(255),                   -- phòng họp hoặc link online
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                    CHECK (status IN ('SCHEDULED','DONE','CANCELLED','NO_SHOW'))
);

CREATE TABLE interview_panelists (
    interview_id  BIGINT NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    PRIMARY KEY (interview_id, user_id)
);

CREATE TABLE interview_evaluations (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    interview_id  BIGINT NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    evaluator_id  BIGINT NOT NULL REFERENCES users(id),
    criteria      JSONB NOT NULL,                   -- [{"name":"Kỹ thuật","score":4}, ...]
    overall_score NUMERIC(2,1) NOT NULL CHECK (overall_score BETWEEN 1 AND 5),
    recommendation VARCHAR(20) CHECK (recommendation IN ('STRONG_HIRE','HIRE','NO_HIRE')),
    comment       TEXT,
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (interview_id, evaluator_id)
);
