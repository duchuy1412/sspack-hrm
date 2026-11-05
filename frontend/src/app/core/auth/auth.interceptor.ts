import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AUTH_API } from './auth.models';
import { AuthService } from './auth.service';

/** Endpoint xác thực tự xử lý lỗi của mình, không gắn token và không tự làm mới. */
const SKIP = [`${AUTH_API}/login`, `${AUTH_API}/refresh`, `${AUTH_API}/logout`];

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/**
 * Gắn access token vào request /api. Gặp 401 thì làm mới token (một lần cho mọi request đang chờ)
 * rồi gửi lại; làm mới thất bại thì đưa người dùng về trang đăng nhập.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || SKIP.some((url) => req.url.startsWith(url))) {
    return next(req);
  }

  const auth = inject(AuthService);
  return next(withToken(req, auth.accessToken())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return auth.refreshAccessToken().pipe(
        catchError(() => {
          auth.handleSessionExpired();
          return throwError(() => error);
        }),
        switchMap((token) => next(withToken(req, token))),
      );
    }),
  );
};
