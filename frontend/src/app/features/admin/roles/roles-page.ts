import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { HasPermissionDirective } from '../../../core/auth/has-permission.directive';
import { RbacApi, RbacErrorMessages } from '../rbac.api';
import { RoleSummary } from '../rbac.models';

/** PLT-03: danh sách vai trò, vai trò hệ thống lên trước. */
@Component({
  selector: 'hrm-roles-page',
  imports: [RouterLink, TranslocoPipe, DatePipe, HasPermissionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../../shared/ui/page-shared.scss'],
  template: `
    <div class="toolbar">
      <p class="muted">{{ 'admin.roles.intro' | transloco }}</p>
      <a *hrmHasPermission="'role:manage'" class="btn btn-primary" routerLink="new">
        {{ 'admin.roles.add' | transloco }}
      </a>
    </div>

    @if (error(); as message) {
      <p class="alert" role="alert">{{ message }}</p>
    }

    <section class="card table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">{{ 'admin.roles.columns.name' | transloco }}</th>
            <th scope="col">{{ 'admin.roles.columns.dataScope' | transloco }}</th>
            <th scope="col" class="num">{{ 'admin.roles.columns.permissions' | transloco }}</th>
            <th scope="col" class="num">{{ 'admin.roles.columns.users' | transloco }}</th>
            <th scope="col">{{ 'admin.roles.columns.type' | transloco }}</th>
            <th scope="col">{{ 'admin.roles.columns.updatedAt' | transloco }}</th>
          </tr>
        </thead>
        <tbody>
          @for (role of roles(); track role.id) {
            <tr>
              <td>
                <a [routerLink]="[role.id]" class="role-name">{{ role.name }}</a>
                <div class="muted">{{ role.code }}</div>
              </td>
              <td>{{ 'dataScope.' + role.dataScope | transloco }}</td>
              <td class="num">{{ role.permissionCount }}</td>
              <td class="num">{{ role.assignedUserCount }}</td>
              <td>
                <span class="chip" [class.chip-brand]="role.system">
                  {{ (role.system ? 'admin.roles.system' : 'admin.roles.custom') | transloco }}
                </span>
              </td>
              <td class="muted">{{ role.updatedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="muted">
                {{ (loading() ? 'admin.loading' : 'admin.roles.empty') | transloco }}
              </td>
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
  styles: `
    .role-name {
      font: var(--text-label);
    }
  `,
})
export class RolesPage {
  private readonly api = inject(RbacApi);
  private readonly errors = inject(RbacErrorMessages);

  protected readonly roles = signal<RoleSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Mỗi công ty chỉ có vài chục vai trò: lấy một trang 100 là đủ, không cần phân trang trên giao diện
    this.api.roles(0, 100).subscribe({
      next: (page) => {
        this.roles.set(page.content);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(this.errors.describe(err));
        this.loading.set(false);
      },
    });
  }
}
