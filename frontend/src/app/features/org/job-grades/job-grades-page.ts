import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable } from 'rxjs';

import { formatVndRange, groupThousands, parseVnd } from '../../../shared/format/money';
import { RbacErrorMessages } from '../../admin/rbac.api';
import { JobGrade, OrgCatalogApi } from '../org-catalog.api';

/** Mã lỗi cấp bậc dịch trong `org.errors`, lỗi chung trong `admin.errors`. */
const ERROR_PREFIXES = ['org.errors', 'admin.errors'];

/** Giới hạn cột NUMERIC(15,0). */
const MAX_DIGITS = 15;

/** Số tiền gõ vào phải là số đồng nguyên, lớn hơn 0, tối đa 15 chữ số. */
const moneyValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const raw = String(control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  const value = parseVnd(raw);
  if (value === null || value <= 0 || String(value).length > MAX_DIGITS) {
    return { money: true };
  }
  return null;
};

/** Lương tối thiểu không lớn hơn tối đa. */
const rangeValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const min = parseVnd(group.get('minSalary')?.value);
  const max = parseVnd(group.get('maxSalary')?.value);
  return min !== null && max !== null && min > max ? { range: true } : null;
};

type Field = 'code' | 'name' | 'minSalary' | 'maxSalary';

/**
 * ORG-02: danh mục cấp bậc kèm khung lương. Chỉ người có `org:manage` vào được (route guard và API).
 * Thêm, sửa khung lương, ngừng hoạt động khi không còn chức danh đang dùng, xóa cấp bậc chưa có chức danh.
 */
@Component({
  selector: 'hrm-job-grades-page',
  imports: [ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../../shared/ui/page-shared.scss', '../org-catalog.scss'],
  templateUrl: './job-grades-page.html',
})
export class JobGradesPage {
  private readonly api = inject(OrgCatalogApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly transloco = inject(TranslocoService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('editDialog');

  protected readonly formatRange = formatVndRange;

  protected readonly grades = signal<JobGrade[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly includeInactive = signal(false);

  /** null = đóng; 'new' = thêm; còn lại là cấp bậc đang sửa. */
  protected readonly editing = signal<JobGrade | 'new' | null>(null);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});
  protected readonly submitted = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$/)]],
      name: ['', [Validators.required, Validators.maxLength(100)]],
      minSalary: ['', [Validators.required, moneyValidator]],
      maxSalary: ['', [Validators.required, moneyValidator]],
    },
    { validators: rangeValidator },
  );

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.listGrades(this.includeInactive()).subscribe({
      next: (list) => {
        this.grades.set(list);
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

  // ================================================================= thêm, sửa

  protected openCreate(): void {
    this.editing.set('new');
    this.form.reset({ code: '', name: '', minSalary: '', maxSalary: '' });
    this.openDialog();
  }

  protected openEdit(grade: JobGrade): void {
    this.editing.set(grade);
    this.form.reset({
      code: grade.code,
      name: grade.name,
      minSalary: groupThousands(grade.minSalary),
      maxSalary: groupThousands(grade.maxSalary),
    });
    this.openDialog();
  }

  /** Rời ô tiền: hiện lại số có dấu chấm ngăn nghìn cho dễ đọc. */
  protected tidyMoney(field: 'minSalary' | 'maxSalary'): void {
    const control = this.form.controls[field];
    const value = parseVnd(control.value);
    if (value !== null) {
      control.setValue(groupThousands(value));
    }
  }

  /** Xem trước khung lương trong hộp thoại khi cả hai ô hợp lệ. */
  protected preview(): string | null {
    const min = parseVnd(this.form.controls.minSalary.value);
    const max = parseVnd(this.form.controls.maxSalary.value);
    return min !== null && max !== null && min <= max ? formatVndRange(min, max) : null;
  }

  protected fieldError(field: Field): string | null {
    const control = this.form.controls[field];
    const shown = control.touched || this.submitted();
    if (control.invalid && shown) {
      const key = control.hasError('required')
        ? `${field}Required`
        : control.hasError('pattern')
          ? 'codePattern'
          : control.hasError('maxlength')
            ? 'nameTooLong'
            : 'money';
      return this.transloco.translate(`org.jobGrades.validation.${key}`);
    }
    if (field === 'maxSalary' && this.form.hasError('range') && shown) {
      return this.transloco.translate('org.jobGrades.validation.range');
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
      minSalary: parseVnd(raw.minSalary)!,
      maxSalary: parseVnd(raw.maxSalary)!,
    };
    const request =
      editing === 'new'
        ? this.api.createGrade(input)
        : this.api.updateGrade(editing.id, input, editing.version);
    this.busy.set(true);
    this.dialogError.set(null);
    this.serverFieldErrors.set({});
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.closeDialog();
        this.notice.set(
          this.transloco.translate(
            editing === 'new' ? 'org.jobGrades.created' : 'org.jobGrades.updated',
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

  protected setActive(grade: JobGrade, active: boolean): void {
    if (
      !active &&
      !confirm(this.transloco.translate('org.jobGrades.confirmDeactivate', { name: grade.name }))
    ) {
      return;
    }
    this.run(this.api.setGradeActive(grade.id, active, grade.version), () =>
      this.transloco.translate(active ? 'org.jobGrades.activated' : 'org.jobGrades.deactivated', {
        name: grade.name,
      }),
    );
  }

  protected remove(grade: JobGrade): void {
    if (!confirm(this.transloco.translate('org.jobGrades.confirmDelete', { name: grade.name }))) {
      return;
    }
    this.run(this.api.deleteGrade(grade.id), () =>
      this.transloco.translate('org.jobGrades.deleted', { name: grade.name }),
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
