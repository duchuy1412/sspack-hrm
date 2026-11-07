import { inject } from '@angular/core';
import { RedirectFunction, Routes } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { requireAnyPermission } from '../../core/auth/permission.guard';

/** Vào /admin: mở tab đầu tiên người dùng có quyền. */
const firstAdminTab: RedirectFunction = () => {
  const auth = inject(AuthService);
  if (auth.hasPermission('role:manage')) {
    return 'roles';
  }
  return auth.hasPermission('user:manage') ? 'users' : 'audit';
};

/**
 * Phân hệ Quản trị: vai trò và gán vai trò (PLT-03), nhật ký thao tác (PLT-04). Lazy-load từ app.routes.ts.
 */
export default [
  {
    path: '',
    loadComponent: () => import('./admin-layout').then((m) => m.AdminLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: firstAdminTab },
      {
        path: 'roles',
        canActivate: [requireAnyPermission('role:manage')],
        loadComponent: () => import('./roles/roles-page').then((m) => m.RolesPage),
      },
      {
        path: 'roles/new',
        canActivate: [requireAnyPermission('role:manage')],
        loadComponent: () => import('./roles/role-editor').then((m) => m.RoleEditorPage),
      },
      {
        path: 'roles/:id',
        canActivate: [requireAnyPermission('role:manage')],
        loadComponent: () => import('./roles/role-editor').then((m) => m.RoleEditorPage),
      },
      {
        path: 'users',
        canActivate: [requireAnyPermission('user:manage')],
        loadComponent: () => import('./users/users-page').then((m) => m.UsersPage),
      },
      {
        path: 'audit',
        canActivate: [requireAnyPermission('audit:read')],
        loadComponent: () => import('./audit/audit-page').then((m) => m.AuditPage),
      },
    ],
  },
] satisfies Routes;
