import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { HasPermissionDirective } from '../../core/auth/has-permission.directive';

/** Khung phân hệ Quản trị: tiêu đề và tab, mỗi tab chỉ hiện khi có quyền tương ứng. */
@Component({
  selector: 'hrm-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoPipe, HasPermissionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="display">{{ 'admin.title' | transloco }}</h1>
    <nav class="tabs" [attr.aria-label]="'admin.title' | transloco">
      <a
        *hrmHasPermission="'role:manage'"
        class="tab"
        routerLink="roles"
        routerLinkActive="active"
        ariaCurrentWhenActive="page"
        >{{ 'admin.tabs.roles' | transloco }}</a
      >
      <a
        *hrmHasPermission="'user:manage'"
        class="tab"
        routerLink="users"
        routerLinkActive="active"
        ariaCurrentWhenActive="page"
        >{{ 'admin.tabs.users' | transloco }}</a
      >
      <a
        *hrmHasPermission="'audit:read'"
        class="tab"
        routerLink="audit"
        routerLinkActive="active"
        ariaCurrentWhenActive="page"
        >{{ 'admin.tabs.audit' | transloco }}</a
      >
    </nav>
    <router-outlet />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--space-6);
    }
    .tabs {
      display: flex;
      gap: var(--space-6);
      border-bottom: 1px solid var(--line);
    }
    .tab {
      padding: var(--space-2) 0;
      margin-bottom: -1px;
      border-bottom: 2px solid transparent;
      color: var(--ink-muted);
      font: var(--text-subheading);
      text-decoration: none;
    }
    .tab.active {
      border-bottom-color: var(--brand-strong);
      color: var(--brand-strong);
    }
  `,
})
export class AdminLayout {}
