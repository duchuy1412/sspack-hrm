import { Routes } from '@angular/router';

import { Shell } from './layout/shell/shell';

/** Mỗi phân hệ một chunk lazy-load; đường dẫn khớp với NAV_ITEMS. */
export const routes: Routes = [
  {
    path: '',
    component: Shell,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadChildren: () => import('./features/dashboard/dashboard.routes') },
      { path: 'employees', loadChildren: () => import('./features/employees/employees.routes') },
      { path: 'org', loadChildren: () => import('./features/org/org.routes') },
      { path: 'leave', loadChildren: () => import('./features/leave/leave.routes') },
      { path: 'approvals', loadChildren: () => import('./features/approvals/approvals.routes') },
      { path: 'attendance', loadChildren: () => import('./features/attendance/attendance.routes') },
      { path: 'payroll', loadChildren: () => import('./features/payroll/payroll.routes') },
      { path: 'recruitment', loadChildren: () => import('./features/recruitment/recruitment.routes') },
      { path: 'reports', loadChildren: () => import('./features/reports/reports.routes') },
    ],
  },
  { path: '**', redirectTo: '' },
];
