-- =====================================================================
-- EMP-01 (phần lõi): hồ sơ nhân viên gồm thông tin cá nhân, liên hệ, công việc.
-- Bảng employees đã có từ V1; ở đây chỉ thêm ràng buộc và tiện ích tìm kiếm.
-- =====================================================================

-- Tìm theo tên không dấu ("nguyen van" khớp "Nguyễn Văn"). unaccent là extension "trusted" từ PostgreSQL 13.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Email công việc là định danh liên lạc nội bộ: không trùng trong công ty, không phân biệt hoa thường.
CREATE UNIQUE INDEX uq_employees_work_email
    ON employees (company_id, lower(work_email))
    WHERE work_email IS NOT NULL;

-- Ngày sinh (nếu có) phải trước ngày vào làm.
ALTER TABLE employees
    ADD CONSTRAINT employees_birth_before_hire CHECK (date_of_birth IS NULL OR date_of_birth < hire_date);

-- Lọc theo chức danh ở danh sách (EMP-05) và đếm theo chức danh (ORG-02) đã có idx_employees_position từ V6.
