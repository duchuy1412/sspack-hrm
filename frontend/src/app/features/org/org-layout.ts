import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { HasPermissionDirective } from '../../core/auth/has-permission.directive';

/**
 * Khung phân hệ Cơ cấu tổ chức: tiêu đề và tab. Phòng ban, chức danh mọi người xem được; cấp bậc (khung lương)
 * chỉ hiện với người có `org:manage`.
 */
@Component({
  selector: 'hrm-org-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoPipe, HasPermissionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="display">{{ 'org.title' | transloco }}</h1>
    <nav class="tabs" [attr.aria-label]="'org.title' | transloco">
      <a class="tab" routerLink="departments" routerLinkActive="active" ariaCurrentWhenActive="page">{{
        'org.tabs.departments' | transloco
      }}</a>
      <a class="tab" routerLink="positions" routerLinkActive="active" ariaCurrentWhenActive="page">{{
        'org.tabs.positions' | transloco
      }}</a>
      <a
        *hrmHasPermission="'org:manage'"
        class="tab"
        routerLink="job-grades"
        routerLinkActive="active"
        ariaCurrentWhenActive="page"
        >{{ 'org.tabs.jobGrades' | transloco }}</a
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
      flex-wrap: wrap;
      gap: var(--space-2) var(--space-6);
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
export class OrgLayout {}
