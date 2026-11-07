import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';
import { homeRedirect, requireAnyPermission } from './core/auth/permission.guard';
import { Shell } from './layout/shell/shell';
import { navPermissions } from './layout/nav-items';

/** Route của một phân hệ: lazy-load và chặn khi không có quyền của mục điều hướng tương ứng (PLT-03). */
function moduleRoute(path: string, loadChildren: () => Promise<Routes | { default: Routes }>) {
  return { path, canActivate: [requireAnyPermission(...navPermissions(path))], loadChildren };
}

/** Mỗi phân hệ một chunk lazy-load; đường dẫn khớp với NAV_ITEMS. Mọi trang trong shell cần đăng nhập. */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.LoginPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: homeRedirect },
      moduleRoute('dashboard', () => import('./features/dashboard/dashboard.routes')),
      moduleRoute('employees', () => import('./features/employees/employees.routes')),
      moduleRoute('org', () => import('./features/org/org.routes')),
      moduleRoute('leave', () => import('./features/leave/leave.routes')),
      moduleRoute('approvals', () => import('./features/approvals/approvals.routes')),
      moduleRoute('attendance', () => import('./features/attendance/attendance.routes')),
      moduleRoute('payroll', () => import('./features/payroll/payroll.routes')),
      moduleRoute('recruitment', () => import('./features/recruitment/recruitment.routes')),
      moduleRoute('reports', () => import('./features/reports/reports.routes')),
      moduleRoute('admin', () => import('./features/admin/admin.routes')),
      {
        path: 'forbidden',
        loadComponent: () => import('./features/errors/forbidden').then((m) => m.ForbiddenPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
