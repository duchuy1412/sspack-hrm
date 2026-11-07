import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';

import { AuthService } from './auth.service';

/**
 * Chỉ hiển thị phần tử khi người dùng có ít nhất một quyền trong danh sách (PLT-03).
 *
 * ```html
 * <button *hrmHasPermission="'role:manage'">Thêm vai trò</button>
 * <a *hrmHasPermission="['leave:approve', 'attendance:confirm']">Duyệt đơn</a>
 * ```
 */
@Directive({ selector: '[hrmHasPermission]' })
export class HasPermissionDirective {
  private readonly auth = inject(AuthService);
  private readonly template = inject(TemplateRef<unknown>);
  private readonly container = inject(ViewContainerRef);
  private shown = false;

  readonly hrmHasPermission = input.required<string | readonly string[]>();

  constructor() {
    effect(() => {
      const value = this.hrmHasPermission();
      const allowed = this.auth.hasAnyPermission(typeof value === 'string' ? [value] : value);
      if (allowed && !this.shown) {
        this.container.createEmbeddedView(this.template);
      } else if (!allowed && this.shown) {
        this.container.clear();
      }
      this.shown = allowed;
    });
  }
}
