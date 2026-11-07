import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { RbacErrorMessages } from '../../admin/rbac.api';
import {
  Department,
  DepartmentNode,
  buildTree,
  canMoveTo,
  flattenTree,
  matchingIds,
} from './department.models';
import { DepartmentsApi } from './departments.api';

/** Mã lỗi phòng ban dịch trong `org.errors`, lỗi chung trong `admin.errors`. */
const ERROR_PREFIXES = ['org.errors', 'admin.errors'];

type EditMode =
  { kind: 'create'; parent: Department | null } | { kind: 'edit'; department: Department };

/**
 * ORG-01: cây phòng ban nhiều cấp. Mọi người dùng xem; người có `org:manage` thêm, sửa, kéo thả đổi cấp cha
 * (hoặc dùng "Chuyển đến…" bằng bàn phím, trên điện thoại), ngừng hoạt động và xóa phòng ban chưa dùng.
 */
@Component({
  selector: 'hrm-departments-page',
  imports: [NgTemplateOutlet, FormsModule, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../../shared/ui/page-shared.scss', './departments-page.scss'],
  templateUrl: './departments-page.html',
})
export class DepartmentsPage {
  private readonly api = inject(DepartmentsApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly transloco = inject(TranslocoService);
  private readonly editDialog = viewChild.required<ElementRef<HTMLDialogElement>>('editDialog');
  private readonly moveDialog = viewChild.required<ElementRef<HTMLDialogElement>>('moveDialog');

  private readonly auth = inject(AuthService);

  protected readonly canManage = computed(() => this.auth.hasPermission('org:manage'));

  protected readonly departments = signal<Department[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly includeInactive = signal(false);
  protected readonly query = signal('');
  private readonly collapsed = signal<ReadonlySet<number>>(new Set());

  protected readonly tree = computed(() => buildTree(this.departments()));
  protected readonly matches = computed(() => matchingIds(this.departments(), this.query()));

  // ---- kéo thả
  protected readonly dragging = signal<Department | null>(null);
  protected readonly dropTarget = signal<number | 'root' | null>(null);

  // ---- hộp thoại thêm/sửa
  protected readonly editMode = signal<EditMode | null>(null);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});
  protected readonly submitted = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9_-]{1,29}$/)]],
    name: ['', [Validators.required, Validators.maxLength(255)]],
  });

  // ---- hộp thoại chuyển
  protected readonly moving = signal<Department | null>(null);
  protected readonly moveTargetId = signal<string>('');
  protected readonly moveTargets = computed(() => {
    const dragged = this.moving();
    // Giữ cấp cha hiện tại (làm mờ) để danh sách thụt lề vẫn đọc được như cây
    return dragged
      ? flattenTree(this.tree())
          .filter((d) => canMoveTo(dragged, d) || d.id === dragged.parentId)
          .map((d) => ({ department: d, current: d.id === dragged.parentId }))
      : [];
  });

  constructor() {
    this.reload();
  }

  // ================================================================= cây

  protected reload(): void {
    this.loading.set(true);
    this.api.list(this.includeInactive()).subscribe({
      next: (list) => {
        this.departments.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(this.errors.describe(err, ERROR_PREFIXES));
        this.loading.set(false);
      },
    });
  }

  protected toggleInactive(value: boolean): void {
    this.includeInactive.set(value);
    this.reload();
  }

  protected isVisible(node: DepartmentNode): boolean {
    const keep = this.matches();
    return keep === null || keep.has(node.department.id);
  }

  /** Đang tìm kiếm thì mọi nhánh khớp đều mở. */
  protected isExpanded(node: DepartmentNode): boolean {
    return this.matches() !== null || !this.collapsed().has(node.department.id);
  }

  protected toggle(node: DepartmentNode): void {
    this.collapsed.update((current) => {
      const next = new Set(current);
      if (next.has(node.department.id)) {
        next.delete(node.department.id);
      } else {
        next.add(node.department.id);
      }
      return next;
    });
  }

  // ================================================================= kéo thả

  protected onDragStart(event: DragEvent, department: Department): void {
    if (!this.canManage()) {
      event.preventDefault();
      return;
    }
    this.dragging.set(department);
    event.dataTransfer?.setData('text/plain', String(department.id));
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
    }
  }

  protected onDragEnd(): void {
    this.dragging.set(null);
    this.dropTarget.set(null);
  }

  protected canDropOn(target: Department | null): boolean {
    const dragged = this.dragging();
    return dragged !== null && canMoveTo(dragged, target);
  }

  protected onDragOver(event: DragEvent, target: Department | null): void {
    if (this.canDropOn(target)) {
      event.preventDefault();
      this.dropTarget.set(target === null ? 'root' : target.id);
    }
  }

  protected onDragLeave(target: Department | null): void {
    if (this.dropTarget() === (target === null ? 'root' : target.id)) {
      this.dropTarget.set(null);
    }
  }

  protected onDrop(event: DragEvent, target: Department | null): void {
    event.preventDefault();
    const dragged = this.dragging();
    this.onDragEnd();
    if (dragged && canMoveTo(dragged, target)) {
      this.move(dragged, target);
    }
  }

  // ================================================================= chuyển bằng hộp thoại

  protected openMove(department: Department): void {
    this.moving.set(department);
    this.moveTargetId.set('');
    this.dialogError.set(null);
    this.moveDialog().nativeElement.showModal();
  }

  protected confirmMove(): void {
    const department = this.moving();
    const raw = this.moveTargetId();
    if (!department || raw === '') {
      return;
    }
    const target =
      raw === 'root' ? null : (this.departments().find((d) => d.id === Number(raw)) ?? null);
    this.closeMove();
    this.move(department, target);
  }

  protected closeMove(): void {
    this.moveDialog().nativeElement.close();
    this.moving.set(null);
  }

  private move(department: Department, target: Department | null): void {
    this.run(this.api.move(department.id, target?.id ?? null, department.version), () =>
      this.transloco.translate('org.departments.moved', {
        name: department.name,
        target: target?.name ?? this.transloco.translate('org.departments.rootLevel'),
      }),
    );
  }

  // ================================================================= thêm, sửa

  protected openCreate(parent: Department | null): void {
    this.editMode.set({ kind: 'create', parent });
    this.form.reset({ code: '', name: '' });
    this.openEdit();
  }

  protected openRename(department: Department): void {
    this.editMode.set({ kind: 'edit', department });
    this.form.reset({ code: department.code, name: department.name });
    this.openEdit();
  }

  protected fieldError(field: 'code' | 'name'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || this.submitted())) {
      if (control.hasError('required')) {
        return this.transloco.translate(`org.departments.validation.${field}Required`);
      }
      if (control.hasError('pattern')) {
        return this.transloco.translate('org.departments.validation.codePattern');
      }
      if (control.hasError('maxlength')) {
        return this.transloco.translate('org.departments.validation.nameTooLong');
      }
    }
    return this.serverFieldErrors()[field] ?? null;
  }

  protected save(): void {
    const mode = this.editMode();
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (!mode || this.form.invalid) {
      return;
    }
    const { code, name } = this.form.getRawValue();
    const request =
      mode.kind === 'create'
        ? this.api.create(code.trim().toUpperCase(), name.trim(), mode.parent?.id ?? null)
        : this.api.update(
            mode.department.id,
            code.trim().toUpperCase(),
            name.trim(),
            mode.department.version,
          );
    this.busy.set(true);
    this.dialogError.set(null);
    this.serverFieldErrors.set({});
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeEdit();
        this.notice.set(
          this.transloco.translate(
            mode.kind === 'create' ? 'org.departments.created' : 'org.departments.updated',
            { name: saved.name },
          ),
        );
        this.reload();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.dialogError.set(this.errors.describe(err, ERROR_PREFIXES));
        this.serverFieldErrors.set(this.errors.fieldErrors(err));
      },
    });
  }

  protected closeEdit(): void {
    this.editDialog().nativeElement.close();
    this.editMode.set(null);
  }

  private openEdit(): void {
    this.submitted.set(false);
    this.dialogError.set(null);
    this.serverFieldErrors.set({});
    this.editDialog().nativeElement.showModal();
  }

  // ================================================================= trạng thái, xóa

  protected setActive(department: Department, active: boolean): void {
    if (
      !active &&
      !confirm(
        this.transloco.translate('org.departments.confirmDeactivate', { name: department.name }),
      )
    ) {
      return;
    }
    this.run(this.api.setActive(department.id, active, department.version), () =>
      this.transloco.translate(
        active ? 'org.departments.activated' : 'org.departments.deactivated',
        {
          name: department.name,
        },
      ),
    );
  }

  protected remove(department: Department): void {
    if (
      !confirm(this.transloco.translate('org.departments.confirmDelete', { name: department.name }))
    ) {
      return;
    }
    this.run(this.api.delete(department.id), () =>
      this.transloco.translate('org.departments.deleted', { name: department.name }),
    );
  }

  /** Gọi API ghi, báo kết quả và tải lại cây; lỗi hiện ở thanh thông báo. */
  private run(request: Observable<unknown>, success: () => string): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set(success());
        this.reload();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(this.errors.describe(err, ERROR_PREFIXES));
        this.reload();
      },
    });
  }
}
