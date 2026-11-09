-- =====================================================================
-- CHỈ DÙNG CHO PROFILE dev: hồ sơ nhân viên mẫu (EMP-01). Dữ liệu hư cấu.
-- Tên file đặt sau R__demo_users để Flyway chạy sau phòng ban, chức danh và tài khoản demo.
-- Chạy lại an toàn: bỏ qua mã nhân viên đã có; chỉ gắn tài khoản cho hồ sơ chưa gắn.
-- =====================================================================

INSERT INTO employees (company_id, employee_code, full_name, gender, date_of_birth, work_email, phone,
                       department_id, position_id, hire_date, status)
SELECT c.id, e.code, e.full_name, e.gender, e.dob::date, e.work_email, e.phone, d.id, p.id, e.hire_date::date,
       e.status
FROM (VALUES
    -- Ban Giám đốc
    ('SP0001', 'Đinh Quốc Bảo',     'MALE',   '1972-03-14', 'quocbao.dinh@sspack.vn',   '0903110001', 'BGD',       'TGD',       '2012-06-01', 'ACTIVE'),
    -- Nhà máy
    ('SP0002', 'Lương Thế Vinh',    'MALE',   '1978-11-02', 'thevinh.luong@sspack.vn',  '0903110002', 'NM',        'GD-NM',     '2013-02-18', 'ACTIVE'),
    -- Phòng Nhân sự
    ('SP0003', 'Cao Mỹ Linh',       'FEMALE', '1983-07-21', 'mylinh.cao@sspack.vn',     '0903110003', 'NS',        'TP',        '2014-09-08', 'ACTIVE'),
    ('SP0004', 'Lê Thu Hà',         'FEMALE', '1990-04-09', 'thuha.le@sspack.vn',       '0903110004', 'NS',        'CV-NS',     '2017-03-01', 'ACTIVE'),
    -- Phòng Kế toán
    ('SP0005', 'Tạ Minh Châu',      'FEMALE', '1985-12-30', 'minhchau.ta@sspack.vn',    '0903110005', 'KT',        'KT-TH',     '2015-05-11', 'ACTIVE'),
    ('SP0006', 'Ngô Thị Hằng',      'FEMALE', '1996-08-17', 'thihang.ngo@sspack.vn',    '0903110006', 'KT',        'KT-VIEN',   '2026-10-14', 'PROBATION'),
    -- Phòng Kinh doanh
    ('SP0007', 'Hồ Đức Thắng',      'MALE',   '1982-01-25', 'ducthang.ho@sspack.vn',    '0903110007', 'KD',        'TP',        '2014-01-06', 'ACTIVE'),
    ('SP0008', 'Bùi Ngọc Ánh',      'FEMALE', '1994-06-03', 'ngocanh.bui@sspack.vn',    '0903110008', 'KD-MB',     'NV-KD',     '2023-09-11', 'ACTIVE'),
    ('SP0009', 'Phan Gia Khánh',    'MALE',   '1993-10-12', 'giakhanh.phan@sspack.vn',  '0903110009', 'KD-MN',     'NV-KD',     '2021-04-19', 'ACTIVE'),
    -- Xưởng In
    ('SP0010', 'Trần Minh Anh',     'MALE',   '1984-02-28', 'minhanh.tran@sspack.vn',   '0903110010', 'XI',        'QUAN-DOC',  '2019-04-03', 'ACTIVE'),
    ('SP0011', 'Phạm Văn Đức',      'MALE',   '1991-05-15', 'vanduc.pham@sspack.vn',    '0903110011', 'XI-OFFSET', 'CN-IN',     '2021-08-15', 'ACTIVE'),
    ('SP0012', 'Đỗ Quang Huy',      'MALE',   '1989-09-09', 'quanghuy.do@sspack.vn',    '0903110012', 'XI-OFFSET', 'TO-TRUONG', '2018-10-10', 'ACTIVE'),
    ('SP0013', 'Kiều Văn Tâm',      'MALE',   '1998-12-01', 'vantam.kieu@sspack.vn',    '0903110013', 'XI-OFFSET', 'CN-PHU',    '2024-02-26', 'ACTIVE'),
    ('SP0014', 'Mai Thị Thu',       'FEMALE', '1995-03-22', 'thithu.mai@sspack.vn',     '0903110014', 'XI-FLEXO',  'CN-IN',     '2022-07-04', 'ON_LEAVE'),
    ('SP0015', 'Trịnh Công Sơn',    'MALE',   '2000-07-30', 'congson.trinh@sspack.vn',  '0903110015', 'XI-FLEXO',  'CN-PHU',    '2026-10-01', 'PROBATION'),
    -- Xưởng Cắt dán
    ('SP0016', 'Nguyễn Thị Lan',    'FEMALE', '1992-11-11', 'thilan.nguyen@sspack.vn',  '0903110016', 'XCD',       'TO-TRUONG', '2020-03-02', 'ACTIVE'),
    ('SP0017', 'Lý Thanh Tùng',     'MALE',   '1999-04-18', 'thanhtung.ly@sspack.vn',   '0903110017', 'XCD',       'CN-CD',     '2026-10-01', 'PROBATION'),
    ('SP0018', 'Võ Thị Kim Oanh',   'FEMALE', '1997-01-05', 'kimoanh.vo@sspack.vn',     '0903110018', 'XCD',       'CN-CD',     '2023-05-15', 'ACTIVE'),
    -- Kiểm soát chất lượng
    ('SP0019', 'Hà Quốc Trung',     'MALE',   '1987-06-26', 'quoctrung.ha@sspack.vn',   '0903110019', 'QC',        'KS-QC',     '2016-11-21', 'ACTIVE'),
    ('SP0020', 'Hoàng Thị Mai',     'FEMALE', '1996-02-14', 'thimai.hoang@sspack.vn',   '0903110020', 'QC',        'NV-QC',     '2023-01-06', 'ON_LEAVE'),
    ('SP0021', 'Châu Bảo Ngọc',     'FEMALE', '1998-09-27', 'baongoc.chau@sspack.vn',   '0903110021', 'QC',        'NV-QC',     '2024-08-12', 'ACTIVE'),
    -- Kho vận
    ('SP0022', 'Vũ Đình Khoa',      'MALE',   '1988-05-20', 'dinhkhoa.vu@sspack.vn',    '0903110022', 'KHO',       'TO-TRUONG', '2023-05-20', 'ACTIVE'),
    ('SP0023', 'Đặng Văn Nam',      'MALE',   '1995-10-08', 'vannam.dang@sspack.vn',    '0903110023', 'KHO',       'NV-KHO',    '2026-10-28', 'PROBATION'),
    ('SP0024', 'Tôn Nữ Diệu Hiền',  'FEMALE', '1993-12-19', 'dieuhien.ton@sspack.vn',   '0903110024', 'KHO',       'NV-KHO',    '2019-07-01', 'RESIGNED'),
    ('SP0025', 'La Văn Phúc',       'MALE',   '1990-08-08', 'vanphuc.la@sspack.vn',     '0903110025', 'KHO',       'NV-KHO',    '2022-03-14', 'ACTIVE')
) AS e(code, full_name, gender, dob, work_email, phone, department_code, position_code, hire_date, status)
JOIN companies c ON c.code = 'SSPACK'
JOIN departments d ON d.company_id = c.id AND d.code = e.department_code
JOIN positions p ON p.company_id = c.id AND p.code = e.position_code
ON CONFLICT (company_id, employee_code) DO NOTHING;

-- Nhân viên đã nghỉ cần ngày nghỉ để khớp trạng thái (EMP-06 sẽ ghi qua giao diện).
UPDATE employees
SET termination_date = DATE '2025-12-31', termination_reason = 'Chuyển nơi ở'
WHERE employee_code = 'SP0024' AND termination_date IS NULL;

-- Gắn hồ sơ với tài khoản demo cùng email (phạm vi dữ liệu SELF và DEPARTMENT_TREE cần biết "tôi là ai").
-- admin@sspack.vn là tài khoản quản trị hệ thống, không phải một nhân viên nên không có hồ sơ.
UPDATE employees e
SET user_id = u.id
FROM users u
WHERE u.company_id = e.company_id
  AND lower(u.email) = lower(e.work_email)
  AND e.user_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM employees x WHERE x.user_id = u.id);
