/** Một mục trên thanh điều hướng bên trái; mỗi mục ứng với một phân hệ lazy-load. */
export interface NavItem {
  /** Đường dẫn route, đồng thời là khóa i18n `nav.<key>`. */
  readonly path: string;
  readonly labelKey: string;
}

/** Thứ tự và tên mục theo mockup "SS Pack HRM — Mockup" (artboard Sidebar). */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: 'dashboard', labelKey: 'nav.dashboard' },
  { path: 'employees', labelKey: 'nav.employees' },
  { path: 'org', labelKey: 'nav.org' },
  { path: 'leave', labelKey: 'nav.leave' },
  { path: 'approvals', labelKey: 'nav.approvals' },
  { path: 'attendance', labelKey: 'nav.attendance' },
  { path: 'payroll', labelKey: 'nav.payroll' },
  { path: 'recruitment', labelKey: 'nav.recruitment' },
  { path: 'reports', labelKey: 'nav.reports' },
];
