import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { authGuard, guestGuard, safeReturnUrl } from './auth.guards';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  let authenticated = false;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: () => authenticated } },
      ],
    });
  });

  function runAuthGuard(url: string) {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  it('chưa đăng nhập: chuyển về /login và nhớ trang đích', () => {
    authenticated = false;

    const result = runAuthGuard('/leave');

    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/login?returnUrl=%2Fleave');
  });

  it('đã đăng nhập: cho vào trang', () => {
    authenticated = true;

    expect(runAuthGuard('/leave')).toBe(true);
  });

  it('đã đăng nhập thì /login chuyển về trang chính', () => {
    authenticated = true;

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect((result as UrlTree).toString()).toBe('/');
  });

  it('chỉ chấp nhận returnUrl nội bộ', () => {
    expect(safeReturnUrl('/employees?page=2')).toBe('/employees?page=2');
    expect(safeReturnUrl('https://evil.example')).toBe('/');
    expect(safeReturnUrl('//evil.example')).toBe('/');
    expect(safeReturnUrl('/login')).toBe('/');
    expect(safeReturnUrl(undefined)).toBe('/');
  });
});
