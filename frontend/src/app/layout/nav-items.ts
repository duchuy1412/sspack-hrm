/** Một mục trên thanh điều hướng bên trái; mỗi mục ứng với một phân hệ lazy-load. */
export interface NavItem {
  /** Đường dẫn route, đồng thời là khóa i18n `nav.<key>`. */
  readonly path: string;
  readonly labelKey: string;
  /**
   * Người dùng có ÍT NHẤT MỘT quyền trong danh sách thì thấy mục này và vào được route (PLT-03).
   * Backend vẫn kiểm tra quyền ở từng API; ẩn menu chỉ để giao diện gọn và đúng vai trò.
   */
  readonly permissions: readonly string[];
}

/** Thứ tự và tên mục theo mockup "SS Pack HRM — Mockup" (artboard Sidebar), thêm mục Quản trị cho Admin. */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: 'dashboard', labelKey: 'nav.dashboard', permissions: ['dashboard:read'] },
  { path: 'employees', labelKey: 'nav.employees', permissions: ['employee:read'] },
  { path: 'org', labelKey: 'nav.org', permissions: ['org:read'] },
  { path: 'leave', labelKey: 'nav.leave', permissions: ['leave:read', 'leave:request'] },
  {
    path: 'approvals',
    labelKey: 'nav.approvals',
    permissions: ['leave:approve', 'employee:approve-change', 'attendance:confirm'],
  },
  {
    path: 'attendance',
    labelKey: 'nav.attendance',
    permissions: ['attendance:read', 'attendance:check-in', 'attendance:manage'],
  },
  {
    path: 'payroll',
    labelKey: 'nav.payroll',
    permissions: ['payroll:manage', 'payroll:parameter-read', 'payslip:read'],
  },
  {
    path: 'recruitment',
    labelKey: 'nav.recruitment',
    permissions: ['recruitment:requisition', 'recruitment:manage', 'recruitment:evaluate'],
  },
  { path: 'reports', labelKey: 'nav.reports', permissions: ['payroll:manage'] },
  { path: 'admin', labelKey: 'nav.admin', permissions: ['role:manage', 'user:manage'] },
];

/** Quyền của một mục điều hướng; dùng chung cho menu và guard của route để hai nơi không lệch nhau. */
export function navPermissions(path: string): readonly string[] {
  const item = NAV_ITEMS.find((i) => i.path === path);
  if (!item) {
    throw new Error(`Không có mục điều hướng "${path}"`);
  }
  return item.permissions;
}
