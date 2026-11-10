import { Routes } from '@angular/router';

import { requireAnyPermission } from '../../core/auth/permission.guard';

/**
 * Phân hệ Cơ cấu tổ chức, lazy-load từ app.routes.ts. ORG-01: cây phòng ban; ORG-03: sơ đồ tổ chức; ORG-02: chức danh và cấp bậc
 * (khung lương chỉ dành cho người có `org:manage`).
 */
export default [
  {
    path: '',
    loadComponent: () => import('./org-layout').then((m) => m.OrgLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'departments' },
      {
        path: 'departments',
        loadComponent: () =>
          import('./departments/departments-page').then((m) => m.DepartmentsPage),
      },
      {
        path: 'chart',
        loadComponent: () => import('./chart/org-chart-page').then((m) => m.OrgChartPage),
      },
      {
        path: 'positions',
        loadComponent: () => import('./positions/positions-page').then((m) => m.PositionsPage),
      },
      {
        path: 'job-grades',
        canActivate: [requireAnyPermission('org:manage')],
        loadComponent: () => import('./job-grades/job-grades-page').then((m) => m.JobGradesPage),
      },
    ],
  },
] satisfies Routes;
