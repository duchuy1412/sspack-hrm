-- =====================================================================
-- ORG-02: danh mục chức danh và cấp bậc. "Mỗi chức danh gắn với một khung cấp bậc lương":
--   * cấp bậc bắt buộc có khung lương (tối thiểu, tối đa, cả hai > 0, tối thiểu ≤ tối đa)
--   * chức danh bắt buộc thuộc một cấp bậc
--   * thêm cột ngừng hoạt động cho cấp bậc, và version/created_at/updated_at cho cả hai bảng
-- Hai bảng chưa có dữ liệu ở môi trường nào (chưa có màn hình nhập), nên đặt NOT NULL trực tiếp.
-- =====================================================================

ALTER TABLE job_grades
    ADD COLUMN is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version    INT         NOT NULL DEFAULT 0,
    ALTER COLUMN min_salary SET NOT NULL,
    ALTER COLUMN max_salary SET NOT NULL,
    ADD CONSTRAINT job_grades_salary_positive CHECK (min_salary > 0);

ALTER TABLE positions
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version    INT         NOT NULL DEFAULT 0,
    ALTER COLUMN job_grade_id SET NOT NULL;

-- Đếm chức danh theo cấp bậc (chặn xóa, ngừng hoạt động cấp bậc còn dùng)
CREATE INDEX idx_positions_job_grade ON positions (job_grade_id);
-- Đếm nhân viên theo chức danh (chặn xóa chức danh đã có người)
CREATE INDEX IF NOT EXISTS idx_employees_position ON employees (position_id);
