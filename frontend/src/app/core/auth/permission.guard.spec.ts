import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { FakeAuth } from '../../../testing/fake-auth';
import { AuthService } from './auth.service';
import { homeRedirect, requireAnyPermission } from './permission.guard';

describe('permission guard (PLT-03)', () => {
  let auth: FakeAuth;

  beforeEach(() => {
    auth = new FakeAuth();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  function check(...codes: string[]) {
    return TestBed.runInInjectionContext(() =>
      requireAnyPermission(...codes)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  function home() {
    return TestBed.runInInjectionContext(() =>
      homeRedirect({} as Parameters<typeof homeRedirect>[0]),
    );
  }

  it('có một trong các quyền: cho vào', () => {
    auth.signInAs('MANAGER');

    expect(check('leave:approve', 'attendance:confirm')).toBe(true);
  });

  it('gõ thẳng URL không có quyền: chuyển sang /forbidden', () => {
    auth.signInAs('EMPLOYEE');

    const result = check('role:manage');

    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/forbidden');
  });

  it('trang mặc định là mục điều hướng đầu tiên được phép', () => {
    auth.signInAs('HR');
    expect(home()).toBe('/dashboard');

    auth.signInAs('EMPLOYEE');
    expect(home()).toBe('/employees');

    auth.signInAs('INTERVIEWER');
    expect(home()).toBe('/recruitment');
  });

  it('không có quyền nào: về /forbidden', () => {
    auth.signOut();

    expect(home()).toBe('/forbidden');
  });
});
