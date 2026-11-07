-- =====================================================================
-- CHỈ DÙNG CHO PROFILE dev: cây phòng ban mẫu của SS Pack (ORG-01). Dữ liệu hư cấu.
-- Chạy lại an toàn: bỏ qua mã đã có, path tính lại từ parent_id.
-- =====================================================================

-- Cấp 1
INSERT INTO departments (company_id, parent_id, code, name, path)
SELECT c.id, NULL, d.code, d.name, '/'
FROM companies c
CROSS JOIN (VALUES
    ('BGD', 'Ban Giám đốc'),
    ('KVP', 'Khối Văn phòng'),
    ('NM',  'Nhà máy Bình Dương')
) AS d(code, name)
WHERE c.code = 'SSPACK'
ON CONFLICT (company_id, code) DO NOTHING;

-- Cấp 2
INSERT INTO departments (company_id, parent_id, code, name, path)
SELECT p.company_id, p.id, d.code, d.name, '/'
FROM (VALUES
    ('KVP', 'NS',  'Phòng Nhân sự'),
    ('KVP', 'KT',  'Phòng Kế toán'),
    ('KVP', 'KD',  'Phòng Kinh doanh'),
    ('NM',  'XI',  'Xưởng In'),
    ('NM',  'XCD', 'Xưởng Cắt dán'),
    ('NM',  'QC',  'Phòng Kiểm soát chất lượng'),
    ('NM',  'KHO', 'Kho vận')
) AS d(parent_code, code, name)
JOIN departments p ON p.code = d.parent_code
JOIN companies c ON c.id = p.company_id AND c.code = 'SSPACK'
ON CONFLICT (company_id, code) DO NOTHING;

-- Cấp 3
INSERT INTO departments (company_id, parent_id, code, name, path)
SELECT p.company_id, p.id, d.code, d.name, '/'
FROM (VALUES
    ('KD', 'KD-MB', 'Kinh doanh miền Bắc'),
    ('KD', 'KD-MN', 'Kinh doanh miền Nam'),
    ('XI', 'XI-OFFSET', 'Tổ In offset'),
    ('XI', 'XI-FLEXO',  'Tổ In flexo')
) AS d(parent_code, code, name)
JOIN departments p ON p.code = d.parent_code
JOIN companies c ON c.id = p.company_id AND c.code = 'SSPACK'
ON CONFLICT (company_id, code) DO NOTHING;

-- Tính lại materialized path cho cả cây
WITH RECURSIVE tree AS (
    SELECT id, '/' || id || '/' AS path
    FROM departments
    WHERE parent_id IS NULL
    UNION ALL
    SELECT d.id, t.path || d.id || '/'
    FROM departments d
    JOIN tree t ON d.parent_id = t.id
)
UPDATE departments d
SET path = tree.path
FROM tree
WHERE d.id = tree.id AND d.path <> tree.path;
