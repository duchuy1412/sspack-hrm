import { computed, signal } from '@angular/core';
import { of } from 'rxjs';

import { CurrentUser } from '../app/core/auth/auth.models';

/**
 * Quyền của các vai trò hệ thống, chép từ ma trận phân quyền trong migration V2 (SRS mục 2).
 * Dùng trong test để kiểm tra menu và route theo vai trò thật.
 */
export const ROLE_PERMISSIONS = {
  ADMIN: [
    'user:manage',
    'role:manage',
    'audit:read',
    'org:read',
    'employee:read',
    'payroll:parameter-read',
    'payroll:parameter-manage',
  ],
  HR: [
    'approval:configure',
    'org:read',
    'org:manage',
    'employee:read',
    'employee:manage',
    'employee:approve-change',
    'leave:read',
    'leave:approve',
    'leave:configure',
    'attendance:read',
    'attendance:manage',
    'payroll:manage',
    'payroll:parameter-read',
    'payslip:read',
    'recruitment:requisition',
    'recruitment:manage',
    'dashboard:read',
  ],
  MANAGER: [
    'org:read',
    'employee:read',
    'leave:read',
    'leave:approve',
    'attendance:read',
    'attendance:confirm',
    'recruitment:requisition',
    'dashboard:read',
  ],
  EMPLOYEE: [
    'org:read',
    'employee:read',
    'employee:propose-change',
    'leave:read',
    'leave:request',
    'attendance:read',
    'attendance:check-in',
    'payslip:read',
  ],
  INTERVIEWER: ['recruitment:evaluate'],
} as const;

export type SystemRole = keyof typeof ROLE_PERMISSIONS;

/** AuthService giả: đổi vai trò bằng {@link FakeAuth.signInAs}, các computed tự cập nhật như bản thật. */
export class FakeAuth {
  private readonly current = signal<CurrentUser | null>(null);

  readonly user = this.current.asReadonly();
  readonly isAuthenticated = computed(() => this.current() !== null);
  readonly permissions = computed(() => new Set(this.current()?.permissions ?? []));

  signInAs(...roles: SystemRole[]): this {
    this.current.set({
      id: 1,
      email: 'thu.nghiem@sspack.vn',
      preferredLocale: 'vi',
      roles: roles.map((code) => ({ code, name: code, dataScope: 'ALL' })),
      permissions: Array.from(new Set(roles.flatMap((r) => ROLE_PERMISSIONS[r]))),
    });
    return this;
  }

  signOut(): void {
    this.current.set(null);
  }

  hasPermission(code: string): boolean {
    return this.permissions().has(code);
  }

  hasAnyPermission(codes: readonly string[]): boolean {
    return codes.some((code) => this.permissions().has(code));
  }

  logout() {
    this.signOut();
    return of(undefined);
  }
}
