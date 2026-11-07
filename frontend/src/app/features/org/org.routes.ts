import { Routes } from '@angular/router';

/** Phân hệ Cơ cấu tổ chức, lazy-load từ app.routes.ts. ORG-01: cây phòng ban. */
export default [
  {
    path: '',
    loadComponent: () => import('./departments/departments-page').then((m) => m.DepartmentsPage),
  },
] satisfies Routes;
