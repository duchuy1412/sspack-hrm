-- =====================================================================
-- CHỈ DÙNG CHO PROFILE dev: cấp bậc và chức danh mẫu của SS Pack (ORG-02). Dữ liệu và mức lương hư cấu.
-- Chạy lại an toàn: bỏ qua mã đã có.
-- =====================================================================

INSERT INTO job_grades (company_id, code, name, min_salary, max_salary)
SELECT c.id, g.code, g.name, g.min_salary, g.max_salary
FROM companies c
CROSS JOIN (VALUES
    ('G1', 'Công nhân, nhân viên phổ thông',  6000000,  9000000),
    ('G2', 'Công nhân lành nghề, nhân viên',  8000000, 13000000),
    ('G3', 'Chuyên viên, kỹ thuật viên',     12000000, 20000000),
    ('G4', 'Tổ trưởng, chuyên viên chính',   16000000, 26000000),
    ('G5', 'Trưởng phòng, quản đốc',         25000000, 40000000),
    ('G6', 'Giám đốc khối',                  38000000, 60000000),
    ('G7', 'Ban Giám đốc',                   55000000, 90000000)
) AS g(code, name, min_salary, max_salary)
WHERE c.code = 'SSPACK'
ON CONFLICT (company_id, code) DO NOTHING;

INSERT INTO positions (company_id, code, name, job_grade_id)
SELECT g.company_id, p.code, p.name, g.id
FROM (VALUES
    ('G1', 'CN-PHU',   'Công nhân phụ máy'),
    ('G1', 'NV-KHO',   'Nhân viên kho'),
    ('G2', 'CN-IN',    'Công nhân vận hành máy in'),
    ('G2', 'CN-CD',    'Công nhân vận hành máy cắt dán'),
    ('G2', 'NV-QC',    'Nhân viên kiểm tra chất lượng'),
    ('G3', 'KS-QC',    'Kỹ sư chất lượng'),
    ('G3', 'CV-NS',    'Chuyên viên nhân sự'),
    ('G3', 'KT-VIEN',  'Kế toán viên'),
    ('G3', 'NV-KD',    'Nhân viên kinh doanh'),
    ('G4', 'TO-TRUONG','Tổ trưởng sản xuất'),
    ('G4', 'KT-TH',    'Kế toán tổng hợp'),
    ('G5', 'TP',       'Trưởng phòng'),
    ('G5', 'QUAN-DOC', 'Quản đốc xưởng'),
    ('G6', 'GD-NM',    'Giám đốc nhà máy'),
    ('G7', 'TGD',      'Tổng Giám đốc')
) AS p(grade_code, code, name)
JOIN job_grades g ON g.code = p.grade_code
JOIN companies c ON c.id = g.company_id AND c.code = 'SSPACK'
ON CONFLICT (company_id, code) DO NOTHING;
