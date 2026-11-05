-- =====================================================================
-- CHỈ DÙNG CHO PROFILE dev: tài khoản demo hiển thị ở trang đăng nhập (mockup SC-01).
-- Không nằm trong db/migration nên không bao giờ chạy ở môi trường khác.
-- Mật khẩu chung: Demo@2026 (bcrypt cost 10). Dữ liệu hư cấu.
-- =====================================================================

INSERT INTO users (company_id, email, password_hash)
SELECT c.id, u.email, '$2y$10$BhpzFT1nhJifKX6Ydc.HW.BB2JfAFFnzTtr2I.kizuQ7wU0P4Xtay'
FROM companies c
CROSS JOIN (VALUES
    ('admin@sspack.vn'),
    ('thuha.le@sspack.vn'),
    ('minhanh.tran@sspack.vn'),
    ('vanduc.pham@sspack.vn')
) AS u(email)
WHERE c.code = 'SSPACK'
ON CONFLICT (company_id, email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT usr.id, r.id
FROM (VALUES
    ('admin@sspack.vn',        'ADMIN'),
    ('thuha.le@sspack.vn',     'HR'),
    ('thuha.le@sspack.vn',     'EMPLOYEE'),
    ('minhanh.tran@sspack.vn', 'MANAGER'),
    ('minhanh.tran@sspack.vn', 'EMPLOYEE'),
    ('vanduc.pham@sspack.vn',  'EMPLOYEE')
) AS m(email, role_code)
JOIN users usr ON usr.email = m.email
JOIN roles r ON r.code = m.role_code AND r.company_id = usr.company_id
ON CONFLICT DO NOTHING;
