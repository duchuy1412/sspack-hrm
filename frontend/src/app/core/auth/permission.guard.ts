import { inject } from '@angular/core';
import { CanActivateFn, RedirectFunction, Router } from '@angular/router';

import { NAV_ITEMS } from '../../layout/nav-items';
import { AuthService } from './auth.service';

/**
 * Chặn route khi người dùng không có ít nhất một quyền trong danh sách (PLT-03). Gõ thẳng URL cũng bị chặn,
 * không chỉ ẩn menu; API phía sau vẫn tự kiểm tra quyền.
 */
export function requireAnyPermission(...codes: readonly string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.hasAnyPermission(codes) ? true : inject(Router).createUrlTree(['/forbidden']);
  };
}

/** Trang mặc định sau đăng nhập: mục điều hướng đầu tiên người dùng được thấy. */
export const homeRedirect: RedirectFunction = () => {
  const auth = inject(AuthService);
  const first = NAV_ITEMS.find((item) => auth.hasAnyPermission(item.permissions));
  return first ? `/${first.path}` : '/forbidden';
};
