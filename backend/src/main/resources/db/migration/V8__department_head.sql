-- ORG-04: tra cứu phòng ban theo trưởng đơn vị (cột head_employee_id đã có từ V1).
CREATE INDEX idx_departments_head ON hrm.departments (company_id, head_employee_id)
    WHERE head_employee_id IS NOT NULL;
