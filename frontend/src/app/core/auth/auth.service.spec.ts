import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AUTH_API, CurrentUser } from './auth.models';
import { AuthService } from './auth.service';

const USER: CurrentUser = {
  id: 7,
  email: 'minhanh.tran@sspack.vn',
  preferredLocale: 'vi',
  roles: [{ code: 'MANAGER', name: 'Quản lý trực tiếp', dataScope: 'DEPARTMENT_TREE' }],
  permissions: ['leave:approve', 'leave:read'],
};

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('đăng nhập: giữ access token trong bộ nhớ và nạp người dùng hiện tại', () => {
    let result: CurrentUser | undefined;
    auth.login('minhanh.tran@sspack.vn', 'Demo@2026', true).subscribe((u) => (result = u));

    const login = http.expectOne(`${AUTH_API}/login`);
    expect(login.request.body).toEqual({
      email: 'minhanh.tran@sspack.vn',
      password: 'Demo@2026',
      rememberMe: true,
    });
    login.flush({ accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${AUTH_API}/me`).flush(USER);

    expect(result).toEqual(USER);
    expect(auth.accessToken()).toBe('jwt-1');
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.hasPermission('leave:approve')).toBe(true);
    expect(auth.hasPermission('payroll:manage')).toBe(false);
  });

  it('nhiều yêu cầu làm mới cùng lúc chỉ gọi /refresh một lần', () => {
    const tokens: string[] = [];
    auth.refreshAccessToken().subscribe((t) => tokens.push(t));
    auth.refreshAccessToken().subscribe((t) => tokens.push(t));

    http
      .expectOne(`${AUTH_API}/refresh`)
      .flush({ accessToken: 'jwt-2', tokenType: 'Bearer', expiresIn: 900 });

    expect(tokens).toEqual(['jwt-2', 'jwt-2']);
    expect(auth.accessToken()).toBe('jwt-2');
  });

  it('khôi phục phiên: không có cookie hợp lệ thì im lặng và vẫn chưa đăng nhập', async () => {
    vi.useFakeTimers();
    const done = auth.restoreSession();

    http
      .expectOne(`${AUTH_API}/refresh`)
      .flush({ code: 'AUTH_SESSION_EXPIRED' }, { status: 401, statusText: '' });
    await vi.advanceTimersByTimeAsync(300); // thử lại một lần (tab khác có thể vừa xoay vòng cookie)
    http
      .expectOne(`${AUTH_API}/refresh`)
      .flush({ code: 'AUTH_SESSION_EXPIRED' }, { status: 401, statusText: '' });
    await done;
    vi.useRealTimers();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.accessToken()).toBeNull();
  });

  it('đăng xuất: gọi /logout, xóa phiên và về trang đăng nhập', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login('a@sspack.vn', 'x', false).subscribe();
    http
      .expectOne(`${AUTH_API}/login`)
      .flush({ accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${AUTH_API}/me`).flush(USER);

    auth.logout().subscribe();
    http.expectOne(`${AUTH_API}/logout`).flush(null, { status: 204, statusText: '' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
