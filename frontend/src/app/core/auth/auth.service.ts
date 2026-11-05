import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  Observable,
  catchError,
  finalize,
  firstValueFrom,
  map,
  of,
  retry,
  shareReplay,
  switchMap,
  tap,
  throwError,
  timer,
} from 'rxjs';

import { AUTH_API, AccessTokenResponse, CurrentUser } from './auth.models';

/**
 * Phiên đăng nhập (PLT-01).
 *
 * Access token chỉ giữ trong bộ nhớ (signal), không ghi localStorage; refresh token nằm trong cookie
 * HttpOnly do backend đặt, JavaScript không đọc được. Tải lại trang thì phiên được khôi phục bằng
 * {@link restoreSession} (gọi /refresh với cookie).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  private readonly currentUser = signal<CurrentUser | null>(null);
  private refreshInFlight: Observable<string> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.token() !== null && this.currentUser() !== null);
  readonly permissions = computed(() => new Set(this.currentUser()?.permissions ?? []));

  accessToken(): string | null {
    return this.token();
  }

  hasPermission(code: string): boolean {
    return this.permissions().has(code);
  }

  login(email: string, password: string, rememberMe: boolean): Observable<CurrentUser> {
    return this.http
      .post<AccessTokenResponse>(`${AUTH_API}/login`, { email, password, rememberMe })
      .pipe(
        tap((res) => this.token.set(res.accessToken)),
        switchMap(() => this.loadCurrentUser()),
      );
  }

  /**
   * Lấy access token mới bằng cookie refresh. Nhiều request cùng gặp 401 chỉ gây ra MỘT lần gọi /refresh.
   * Nếu tab khác vừa xoay vòng cookie đúng lúc đó, backend trả 401 trong khoảng ân hạn: thử lại một lần
   * (cookie mới đã có trong trình duyệt).
   */
  refreshAccessToken(): Observable<string> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<AccessTokenResponse>(`${AUTH_API}/refresh`, null).pipe(
        retry({
          count: 1,
          delay: (err: unknown) =>
            err instanceof HttpErrorResponse && err.status === 401
              ? timer(300)
              : throwError(() => err),
        }),
        tap((res) => this.token.set(res.accessToken)),
        map((res) => res.accessToken),
        catchError((err: unknown) => {
          this.clearSession();
          return throwError(() => err);
        }),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay(1),
      );
    }
    return this.refreshInFlight;
  }

  /** Gọi lúc khởi động ứng dụng: khôi phục phiên nếu còn cookie hợp lệ, im lặng nếu không. */
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.refreshAccessToken().pipe(
        switchMap(() => this.loadCurrentUser()),
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${AUTH_API}/logout`, null).pipe(
      catchError(() => of(undefined)),
      map(() => undefined),
      finalize(() => {
        this.clearSession();
        void this.router.navigate(['/login']);
      }),
    );
  }

  /** Phiên hết hạn giữa chừng: xóa phiên và đưa về trang đăng nhập, nhớ trang đang mở. */
  handleSessionExpired(): void {
    const returnUrl = this.router.url;
    this.clearSession();
    void this.router.navigate(['/login'], {
      queryParams: returnUrl && !returnUrl.startsWith('/login') ? { returnUrl } : {},
    });
  }

  private loadCurrentUser(): Observable<CurrentUser> {
    return this.http
      .get<CurrentUser>(`${AUTH_API}/me`)
      .pipe(tap((user) => this.currentUser.set(user)));
  }

  private clearSession(): void {
    this.token.set(null);
    this.currentUser.set(null);
  }
}
