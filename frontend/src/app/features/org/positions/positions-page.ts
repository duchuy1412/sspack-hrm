import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, startWith } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { formatVndRange } from '../../../shared/format/money';
import { normalizeSearch } from '../../../shared/format/search';
import { RbacErrorMessages } from '../../admin/rbac.api';
import { JobGrade, OrgCatalogApi, Position } from '../org-catalog.api';

/** Mã lỗi chức danh dịch trong `org.errors`, lỗi chung trong `admin.errors`. */
const ERROR_PREFIXES = ['org.errors', 'admin.errors'];

/** Một lựa chọn trong ô lọc / ô chọn cấp bậc. */
export interface GradeOption {
  id: number;
  label: string;
  active: boolean;
}

/** Giữ export cũ cho nơi đang dùng; hàm nằm ở shared/format/search.ts. */
export { normalizeSearch };

/**
 * ORG-02: danh mục chức danh, mỗi chức danh gắn với một khung cấp bậc lương. Mọi người dùng xem được danh sách;
 * người có `org:manage` thấy thêm khung lương, thêm, sửa, đổi cấp bậc, ngừng hoạt động, xóa chức danh chưa dùng.
 */
@Component({
  selector: 'hrm-positions-page',
  imports: [FormsModule, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../../shared/ui/page-shared.scss', '../org-catalog.scss'],
  templateUrl: './positions-page.html',
})
export class PositionsPage {
  private readonly api = inject(OrgCatalogApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly transloco = inject(TranslocoService);
  private readonly auth = inject(AuthService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('editDialog');

  protected readonly canManage = computed(() => this.auth.hasPermission('org:manage'));

  protected readonly positions = signal<Position[]>([]);
  /** Chỉ tải khi có `org:manage` (API khung lương cần quyền này). */
  protected readonly grades = signal<JobGrade[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly includeInactive = signal(false);
  protected readonly query = signal('');
  protected readonly gradeFilter = signal<string>('');

  private readonly gradeById = computed(() => new Map(this.grades().map((g) => [g.id, g])));

  /** Ô lọc: cấp bậc lấy từ danh mục cấp bậc nếu có quyền, không thì từ chính danh sách chức danh. */
  protected readonly gradeOptions = computed<GradeOption[]>(() => {
    if (this.canManage()) {
      return this.grades().map((g) => ({
        id: g.id,
        label: `${g.code} – ${g.name}`,
        active: g.active,
      }));
    }
    const seen = new Map<number, GradeOption>();
    for (const p of this.positions()) {
      seen.set(p.jobGradeId, {
        id: p.jobGradeId,
        label: `${p.jobGradeCode} – ${p.jobGradeName}`,
        active: p.jobGradeActive,
      });
    }
    return [...seen.values()].sort((a, b) => a.label.localeCompare(b.label, 'vi'));
  });

  protected readonly visible = computed(() => {
    const q = normalizeSearch(this.query());
    const grade = this.gradeFilter();
    return this.positions().filter(
      (p) =>
        (grade === '' || p.jobGradeId === Number(grade)) &&
        (q === '' || normalizeSearch(`${p.code} ${p.name}`).includes(q)),
    );
  });

  // ---- hộp thoại thêm/sửa
  /** null = đóng; 'new' = thêm; còn lại là chức danh đang sửa. */
  protected readonly editing = signal<Position | 'new' | null>(null);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});
  protected readonly submitted = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9_-]{1,29}$/)]],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    jobGradeId: ['', [Validators.required]],
  });
  private readonly selectedGradeId = toSignal(
    this.form.controls.jobGradeId.valueChanges.pipe(startWith('')),
    { initialValue: '' },
  );

  /** Cấp bậc chọn được: đang hoạt động, cộng cấp bậc hiện tại của chức danh đang sửa (dù đã ngừng). */
  protected readonly gradeChoices = computed(() => {
    const editing = this.editing();
    const currentId = editing && editing !== 'new' ? editing.jobGradeId : null;
    return this.grades().filter((g) => g.active || g.id === currentId);
  });

  /** Khung lương của cấp bậc đang chọn trong hộp thoại. */
  protected readonly selectedRange = computed(() => {
    const grade = this.gradeById().get(Number(this.selectedGradeId()));
    return grade ? formatVndRange(grade.minSalary, grade.maxSalary) : null;
  });

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    if (this.canManage()) {
      this.api.listGrades(true).subscribe({
        next: (list) => this.grades.set(list),
        error: (err: unknown) => this.error.set(this.errors.describe(err, ERROR_PREFIXES)),
      });
    }
    this.api.listPositions(this.includeInactive()).subscribe({
      next: (list) => {
        this.positions.set(list);
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

  /** Khung lương của chức danh, chỉ có khi đã tải danh mục cấp bậc (người có `org:manage`). */
  protected rangeOf(position: Position): string {
    const grade = this.gradeById().get(position.jobGradeId);
    return grade ? formatVndRange(grade.minSalary, grade.maxSalary) : '';
  }

  // ================================================================= thêm, sửa

  protected openCreate(): void {
    this.editing.set('new');
    const firstActive = this.grades().find((g) => g.active);
    this.form.reset({
      code: '',
      name: '',
      jobGradeId: this.gradeFilter() || (firstActive ? String(firstActive.id) : ''),
    });
    this.openDialog();
  }

  protected openEdit(position: Position): void {
    this.editing.set(position);
    this.form.reset({
      code: position.code,
      name: position.name,
      jobGradeId: String(position.jobGradeId),
    });
    this.openDialog();
  }

  protected fieldError(field: 'code' | 'name' | 'jobGradeId'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || this.submitted())) {
      const key = control.hasError('required')
        ? `${field}Required`
        : control.hasError('pattern')
          ? 'codePattern'
          : 'nameTooLong';
      return this.transloco.translate(`org.positions.validation.${key}`);
    }
    return this.serverFieldErrors()[field] ?? null;
  }

  protected save(): void {
    const editing = this.editing();
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (!editing || this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    const input = {
      code: raw.code.trim().toUpperCase(),
      name: raw.name.trim(),
      jobGradeId: Number(raw.jobGradeId),
    };
    const request =
      editing === 'new'
        ? this.api.createPosition(input)
        : this.api.updatePosition(editing.id, input, editing.version);
    this.busy.set(true);
    this.dialogError.set(null);
    this.serverFieldErrors.set({});
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeDialog();
        this.notice.set(
          this.transloco.translate(
            editing === 'new' ? 'org.positions.created' : 'org.positions.updated',
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

  protected closeDialog(): void {
    this.dialog().nativeElement.close();
    this.editing.set(null);
  }

  private openDialog(): void {
    this.submitted.set(false);
    this.dialogError.set(null);
    this.serverFieldErrors.set({});
    this.dialog().nativeElement.showModal();
  }

  // ================================================================= trạng thái, xóa

  protected setActive(position: Position, active: boolean): void {
    if (
      !active &&
      !confirm(this.transloco.translate('org.positions.confirmDeactivate', { name: position.name }))
    ) {
      return;
    }
    this.run(this.api.setPositionActive(position.id, active, position.version), () =>
      this.transloco.translate(active ? 'org.positions.activated' : 'org.positions.deactivated', {
        name: position.name,
      }),
    );
  }

  protected remove(position: Position): void {
    if (
      !confirm(this.transloco.translate('org.positions.confirmDelete', { name: position.name }))
    ) {
      return;
    }
    this.run(this.api.deletePosition(position.id), () =>
      this.transloco.translate('org.positions.deleted', { name: position.name }),
    );
  }

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
