import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { forkJoin, of } from 'rxjs';

import { DataScope } from '../../../core/auth/auth.models';
import { RbacApi, RbacErrorMessages } from '../rbac.api';
import { DATA_SCOPES, PermissionGroup, RoleDetail, groupByModule } from '../rbac.models';

/** Vai trò ADMIN luôn giữ quyền này (backend cũng chặn): khóa ô tích để Admin không tự khóa mình. */
const ADMIN_LOCKED_PERMISSION = 'role:manage';

/**
 * PLT-03: tạo hoặc sửa vai trò. Quyền hiện thành ô tích theo phân hệ, có ô chọn cả nhóm.
 * Route `roles/new` tạo mới; `roles/:id` sửa (tham số `id` vào qua withComponentInputBinding).
 */
@Component({
  selector: 'hrm-role-editor',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../admin-shared.scss', './role-editor.scss'],
  templateUrl: './role-editor.html',
})
export class RoleEditorPage implements OnInit {
  private readonly api = inject(RbacApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  /** Tham số route; không có khi tạo mới. */
  readonly id = input<string>();

  protected readonly scopes = DATA_SCOPES;
  protected readonly groups = signal<PermissionGroup[]>([]);
  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly role = signal<RoleDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});

  protected readonly isNew = computed(() => !this.id() || this.id() === 'new');
  protected readonly isAdminRole = computed(() => this.role()?.code === 'ADMIN');
  protected readonly permissionsMissing = computed(
    () => this.submitted() && this.selected().size === 0,
  );

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z][A-Za-z0-9_]{1,49}$/)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', [Validators.maxLength(255)]],
    dataScope: ['DEPARTMENT' as DataScope, [Validators.required]],
  });

  ngOnInit(): void {
    const roleId = this.isNew() ? null : Number(this.id());
    forkJoin({
      permissions: this.api.permissions(),
      role: roleId === null ? of(null) : this.api.role(roleId),
    }).subscribe({
      next: ({ permissions, role }) => {
        this.groups.set(groupByModule(permissions));
        if (role) {
          this.applyRole(role);
        } else {
          this.form.controls.code.enable();
        }
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(this.errors.describe(err));
        this.loading.set(false);
      },
    });
  }

  protected isChecked(code: string): boolean {
    return this.selected().has(code);
  }

  protected isLocked(code: string): boolean {
    return this.isAdminRole() && code === ADMIN_LOCKED_PERMISSION;
  }

  protected groupState(group: PermissionGroup): 'all' | 'some' | 'none' {
    const count = group.permissions.filter((p) => this.selected().has(p.code)).length;
    return count === 0 ? 'none' : count === group.permissions.length ? 'all' : 'some';
  }

  protected toggle(code: string, checked: boolean): void {
    if (this.isLocked(code)) {
      return;
    }
    this.selected.update((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(code);
      } else {
        next.delete(code);
      }
      return next;
    });
  }

  protected toggleGroup(group: PermissionGroup, checked: boolean): void {
    group.permissions.forEach((p) => this.toggle(p.code, checked));
  }

  protected fieldError(field: 'code' | 'name' | 'description' | 'dataScope'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || this.submitted())) {
      if (control.hasError('required')) {
        return this.transloco.translate(`admin.roles.validation.${field}Required`);
      }
      if (control.hasError('pattern')) {
        return this.transloco.translate('admin.roles.validation.codePattern');
      }
      if (control.hasError('maxlength')) {
        return this.transloco.translate('admin.roles.validation.tooLong', {
          max: control.errors?.['maxlength']?.requiredLength,
        });
      }
    }
    return this.serverFieldErrors()[field] ?? null;
  }

  protected save(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.selected().size === 0) {
      return;
    }
    const value = this.form.getRawValue();
    const payload = {
      name: value.name.trim(),
      description: value.description.trim() || null,
      dataScope: value.dataScope,
      permissions: Array.from(this.selected()),
    };
    const current = this.role();
    const request = current
      ? this.api.updateRole(current.id, { ...payload, version: current.version })
      : this.api.createRole({ ...payload, code: value.code.trim().toUpperCase() });

    this.saving.set(true);
    this.error.set(null);
    this.serverFieldErrors.set({});
    request.subscribe({
      next: () => void this.router.navigate(['/admin/roles']),
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(this.errors.describe(err));
        this.serverFieldErrors.set(this.errors.fieldErrors(err));
      },
    });
  }

  protected remove(): void {
    const current = this.role();
    if (
      !current ||
      !confirm(this.transloco.translate('admin.roles.confirmDelete', { name: current.name }))
    ) {
      return;
    }
    this.saving.set(true);
    this.api.deleteRole(current.id).subscribe({
      next: () => void this.router.navigate(['/admin/roles']),
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(this.errors.describe(err));
      },
    });
  }

  private applyRole(role: RoleDetail): void {
    this.role.set(role);
    this.selected.set(new Set(role.permissions));
    this.form.setValue({
      code: role.code,
      name: role.name,
      description: role.description ?? '',
      dataScope: role.dataScope,
    });
    this.form.controls.code.disable();
    if (role.system) {
      this.form.controls.dataScope.disable();
    }
  }
}
