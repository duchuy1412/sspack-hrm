import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { authInterceptor } from './auth.interceptor';
import { AUTH_API } from './auth.models';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  function signIn(token: string): void {
    auth.login('a@sspack.vn', 'x', false).subscribe();
    http
      .expectOne(`${AUTH_API}/login`)
      .flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 900 });
    const me = http.expectOne(`${AUTH_API}/me`);
    expect(me.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    me.flush({ id: 1, email: 'a@sspack.vn', preferredLocale: 'vi', roles: [], permissions: [] });
  }

  it('gắn Bearer token vào request /api, không gắn cho endpoint đăng nhập', () => {
    signIn('jwt-1');

    client.get('/api/v1/employees').subscribe();

    expect(http.expectOne('/api/v1/employees').request.headers.get('Authorization')).toBe(
      'Bearer jwt-1',
    );
  });

  it('không gắn token cho request ngoài /api', () => {
    signIn('jwt-1');

    client.get('i18n/vi.json').subscribe();

    expect(http.expectOne('i18n/vi.json').request.headers.has('Authorization')).toBe(false);
  });

  it('gặp 401: làm mới token rồi gửi lại request với token mới', () => {
    signIn('jwt-old');
    let body: unknown;
    client.get('/api/v1/employees').subscribe((b) => (body = b));

    http.expectOne('/api/v1/employees').flush(null, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne(`${AUTH_API}/refresh`)
      .flush({ accessToken: 'jwt-new', tokenType: 'Bearer', expiresIn: 900 });
    const retried = http.expectOne('/api/v1/employees');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer jwt-new');
    retried.flush([{ id: 1 }]);

    expect(body).toEqual([{ id: 1 }]);
  });

  it('làm mới thất bại: kết thúc phiên và chuyển về đăng nhập', async () => {
    vi.useFakeTimers();
    signIn('jwt-old');
    const expired = vi.spyOn(auth, 'handleSessionExpired').mockImplementation(() => undefined);
    let failed = false;
    client.get('/api/v1/employees').subscribe({ error: () => (failed = true) });

    http.expectOne('/api/v1/employees').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${AUTH_API}/refresh`).flush(null, { status: 401, statusText: '' });
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne(`${AUTH_API}/refresh`).flush(null, { status: 401, statusText: '' });
    vi.useRealTimers();

    expect(failed).toBe(true);
    expect(expired).toHaveBeenCalled();
    expect(auth.accessToken()).toBeNull();
  });

  it('lỗi khác 401 được trả nguyên cho nơi gọi', () => {
    signIn('jwt-1');
    let status = 0;
    client.get('/api/v1/employees').subscribe({ error: (e) => (status = e.status) });

    http.expectOne('/api/v1/employees').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
  });
});
