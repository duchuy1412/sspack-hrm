import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, forkJoin, of } from 'rxjs';

import { formatDate, todayIso } from '../../../shared/format/date';
import { RbacErrorMessages } from '../../admin/rbac.api';
import { DepartmentsApi } from '../../org/departments/departments.api';
import { OrgCatalogApi, Position } from '../../org/org-catalog.api';
import {
  CREATE_STATUSES,
  Employee,
  EmployeeInput,
  EmployeeStatus,
  EmployeesApi,
  GENDERS,
  Gender,
} from '../employees.api';
import {
  DepartmentOption,
  EMPLOYEE_ERROR_PREFIXES,
  departmentOptions,
} from '../list/employees-page';

const CODE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{1,29}$/;
const PHONE_PATTERN = /^\+?[0-9 .()-]{9,20}$/;

/** Ngày sinh (nếu có) phải trước ngày vào làm và không ở tương lai; khớp quy tắc ở backend. */
export const birthDateValidator: ValidatorFn = (
  group: AbstractControl,
): ValidationErrors | null => {
  const birth: string = group.get('dateOfBirth')?.value ?? '';
  const hire: string = group.get('hireDate')?.value ?? '';
  if (!birth) {
    return null;
  }
  // Chuỗi yyyy-MM-dd so sánh được theo thứ tự từ điển
  if ((hire && birth >= hire) || birth > todayIso()) {
    return { birthDate: true };
  }
  return null;
};

type Field =
  | 'code'
  | 'fullName'
  | 'gender'
  | 'dateOfBirth'
  | 'workEmail'
  | 'personalEmail'
  | 'phone'
  | 'address'
  | 'departmentId'
  | 'positionId'
  | 'hireDate'
  | 'status';

/**
 * EMP-01: hồ sơ nhân viên (cá nhân, liên hệ, công việc). `/employees/new` tạo mới với mã gợi ý; `/employees/:id`
 * mở hồ sơ, sửa được khi server trả `editable` (có `employee:manage` và hồ sơ trong phạm vi), không thì chỉ xem.
 * Thông tin cá nhân chỉ có khi server trả `personalVisible`.
 */
@Component({
  selector: 'hrm-employee-profile-page',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: [
    '../../../shared/ui/page-shared.scss',
    '../employees.scss',
    './employee-profile-page.scss',
  ],
  templateUrl: './employee-profile-page.html',
})
export class EmployeeProfilePage {
  /** Id trong URL; không có khi tạo mới (`/employees/new`). */
  readonly id = input<string>();

  private readonly api = inject(EmployeesApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly catalog = inject(OrgCatalogApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);

  protected readonly isNew = computed(() => !this.id());
  protected readonly employee = signal<Employee | null>(null);
  protected readonly departments = signal<DepartmentOption[]>([]);
  protected readonly positions = signal<Position[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly submitted = signal(false);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});

  protected readonly genders = GENDERS;
  protected readonly formatDate = formatDate;
  protected readonly today = todayIso();

  /** Hiện biểu mẫu khi tạo mới hoặc khi được sửa; còn lại hiện dạng chỉ xem. */
  protected readonly editable = computed(
    () => this.isNew() || (this.employee()?.editable ?? false),
  );

  /** Phòng ban chọn được: đang hoạt động, cộng phòng ban hiện tại của hồ sơ (dù đã ngừng). */
  protected readonly departmentChoices = computed(() => {
    const currentId = this.employee()?.departmentId;
    return this.departments().filter((d) => d.active || d.id === currentId);
  });

  /** Chức danh chọn được: đang hoạt động, cộng chức danh hiện tại của hồ sơ (dù đã ngừng). */
  protected readonly positionChoices = computed(() => {
    const currentId = this.employee()?.positionId;
    return this.positions()
      .filter((p) => p.active || p.id === currentId)
      .sort((a, b) => a.name.localeCompare(b.name, 'vi'));
  });

  protected readonly statusChoices = computed<readonly EmployeeStatus[]>(() =>
    this.isNew() ? CREATE_STATUSES : (this.employee()?.allowedStatuses ?? []),
  );

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      code: ['', [Validators.required, Validators.pattern(CODE_PATTERN)]],
      fullName: ['', [Validators.required, Validators.maxLength(150)]],
      gender: [''],
      dateOfBirth: [''],
      workEmail: ['', [Validators.email, Validators.maxLength(255)]],
      personalEmail: ['', [Validators.email, Validators.maxLength(255)]],
      phone: ['', [Validators.pattern(PHONE_PATTERN)]],
      address: ['', [Validators.maxLength(500)]],
      departmentId: ['', [Validators.required]],
      positionId: ['', [Validators.required]],
      hireDate: ['', [Validators.required]],
      status: ['PROBATION', [Validators.required]],
    },
    { validators: birthDateValidator },
  );

  constructor() {
    const state = (this.router.getCurrentNavigation()?.extras.state ?? history.state) as {
      notice?: string;
    } | null;
    if (state?.notice) {
      this.notice.set(state.notice);
    }
    this.departmentsApi.list(true).subscribe({
      next: (list) => this.departments.set(departmentOptions(list)),
      error: () => this.departments.set([]),
    });
    this.catalog.listPositions(true).subscribe({
      next: (list) => this.positions.set(list),
      error: () => this.positions.set([]),
    });
    // Nạp lại khi id trong URL đổi (vd vừa tạo xong chuyển sang hồ sơ mới)
    effect(() => {
      const id = this.id();
      untracked(() => this.load(id));
    });
  }

  private load(id: string | undefined): void {
    this.loading.set(true);
    this.error.set(null);
    this.submitted.set(false);
    this.serverFieldErrors.set({});
    const request: Observable<{ employee: Employee | null; next: { code: string } | null }> = id
      ? forkJoin({ employee: this.api.get(Number(id)), next: of(null) })
      : forkJoin({ employee: of(null), next: this.api.nextCode() });
    request.subscribe({
      next: ({ employee, next }) => {
        this.employee.set(employee);
        if (employee) {
          this.fill(employee);
        } else {
          this.form.reset({ code: next?.code ?? '', status: 'PROBATION', hireDate: this.today });
        }
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(this.errors.describe(err, EMPLOYEE_ERROR_PREFIXES));
        this.loading.set(false);
      },
    });
  }

  private fill(e: Employee): void {
    this.form.reset({
      code: e.code,
      fullName: e.fullName,
      gender: e.gender ?? '',
      dateOfBirth: e.dateOfBirth ?? '',
      workEmail: e.workEmail ?? '',
      personalEmail: e.personalEmail ?? '',
      phone: e.phone ?? '',
      address: e.address ?? '',
      departmentId: String(e.departmentId),
      positionId: e.positionId === null ? '' : String(e.positionId),
      hireDate: e.hireDate,
      status: e.status,
    });
  }

  protected fieldError(field: Field): string | null {
    const control = this.form.controls[field];
    const show = control.touched || this.submitted();
    if (show && control.invalid) {
      const key = control.hasError('required')
        ? `${field}Required`
        : control.hasError('email')
          ? 'emailInvalid'
          : control.hasError('pattern')
            ? `${field}Pattern`
            : `${field}TooLong`;
      return this.transloco.translate(`employees.validation.${key}`);
    }
    if (
      field === 'dateOfBirth' &&
      this.form.hasError('birthDate') &&
      (control.touched || this.form.controls.hireDate.touched || this.submitted())
    ) {
      return this.transloco.translate('employees.validation.birthDate');
    }
    return this.serverFieldErrors()[field] ?? null;
  }

  protected save(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) {
      return;
    }
    const raw = this.form.getRawValue();
    const blank = (v: string) => (v.trim() === '' ? null : v.trim());
    const input: EmployeeInput = {
      code: raw.code.trim().toUpperCase(),
      fullName: raw.fullName.trim(),
      gender: (raw.gender || null) as Gender | null,
      dateOfBirth: raw.dateOfBirth || null,
      workEmail: blank(raw.workEmail),
      personalEmail: blank(raw.personalEmail),
      phone: blank(raw.phone),
      address: blank(raw.address),
      departmentId: Number(raw.departmentId),
      positionId: Number(raw.positionId),
      hireDate: raw.hireDate,
      status: raw.status as EmployeeStatus,
    };
    const current = this.employee();
    const request = current
      ? this.api.update(current.id, input, current.version)
      : this.api.create(input);
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.serverFieldErrors.set({});
    request.subscribe({
      next: (saved) => {
        this.busy.set(false);
        if (current) {
          this.employee.set(saved);
          this.fill(saved);
          this.submitted.set(false);
          this.notice.set(
            this.transloco.translate('employees.profile.updated', { name: saved.fullName }),
          );
        } else {
          void this.router.navigate(['/employees', saved.id], {
            state: {
              notice: this.transloco.translate('employees.profile.created', {
                name: saved.fullName,
              }),
            },
          });
        }
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(this.errors.describe(err, EMPLOYEE_ERROR_PREFIXES));
        this.serverFieldErrors.set(this.errors.fieldErrors(err));
      },
    });
  }

  protected reset(): void {
    const current = this.employee();
    if (current) {
      this.fill(current);
      this.submitted.set(false);
      this.serverFieldErrors.set({});
      this.error.set(null);
    }
  }
}
