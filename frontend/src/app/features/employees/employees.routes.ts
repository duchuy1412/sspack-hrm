import { Routes } from '@angular/router';

import { requireAnyPermission } from '../../core/auth/permission.guard';

/**
 * Phân hệ Nhân viên, lazy-load từ app.routes.ts. EMP-01: danh sách và hồ sơ (cá nhân, liên hệ, công việc).
 * Thêm mới cần `employee:manage`; xem hồ sơ thì server tự lọc theo phạm vi dữ liệu.
 */
export default [
  {
    path: '',
    loadComponent: () => import('./list/employees-page').then((m) => m.EmployeesPage),
  },
  {
    path: 'new',
    canActivate: [requireAnyPermission('employee:manage')],
    loadComponent: () =>
      import('./profile/employee-profile-page').then((m) => m.EmployeeProfilePage),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./profile/employee-profile-page').then((m) => m.EmployeeProfilePage),
  },
] satisfies Routes;
