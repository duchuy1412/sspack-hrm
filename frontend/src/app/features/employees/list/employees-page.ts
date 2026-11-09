import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subject, debounceTime } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { formatDate } from '../../../shared/format/date';
import { RbacErrorMessages } from '../../admin/rbac.api';
import { Department, buildTree, flattenTree } from '../../org/departments/department.models';
import { DepartmentsApi } from '../../org/departments/departments.api';
import { EmployeeStatus, EmployeeSummary, EmployeesApi, initials } from '../employees.api';

/** Mã lỗi nhân viên dịch trong `employees.errors`, lỗi chung trong `admin.errors`. */
export const EMPLOYEE_ERROR_PREFIXES = ['employees.errors', 'org.errors', 'admin.errors'];

/** Ô lọc trạng thái: nhóm "đang làm việc", từng trạng thái, "đã nghỉ" và tất cả. */
export const STATUS_FILTERS: Record<string, readonly EmployeeStatus[]> = {
  working: ['PROBATION', 'ACTIVE', 'ON_LEAVE'],
  PROBATION: ['PROBATION'],
  ACTIVE: ['ACTIVE'],
  ON_LEAVE: ['ON_LEAVE'],
  left: ['RESIGNED', 'TERMINATED'],
  all: [],
};

export const PAGE_SIZE = 20;

/** Phòng ban trong ô chọn, thụt lề theo cấp. */
export interface DepartmentOption {
  id: number;
  label: string;
  active: boolean;
}

/** Danh sách phòng ban theo thứ tự cây, tên thụt lề theo cấp (dùng cho ô lọc và ô chọn). */
export function departmentOptions(departments: readonly Department[]): DepartmentOption[] {
  return flattenTree(buildTree(departments)).map((d) => ({
    id: d.id,
    label: `${'\u00a0'.repeat(Math.max(0, d.depth - 1) * 3)}${d.name}`,
    active: d.active,
  }));
}

/**
 * EMP-01: danh sách nhân viên theo mockup "Nhân viên". Server lọc theo phạm vi dữ liệu (HR thấy cả công ty,
 * quản lý thấy cây phòng ban của mình, nhân viên chỉ thấy mình). Tìm theo tên không dấu, mã, email; lọc phòng
 * ban (gồm cấp dưới) và trạng thái; phân trang ở server.
 */
@Component({
  selector: 'hrm-employees-page',
  imports: [FormsModule, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../../shared/ui/page-shared.scss', '../employees.scss'],
  templateUrl: './employees-page.html',
})
export class EmployeesPage {
  private readonly api = inject(EmployeesApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly auth = inject(AuthService);

  protected readonly canManage = computed(() => this.auth.hasPermission('employee:manage'));
  protected readonly statusFilters = Object.keys(STATUS_FILTERS);

  protected readonly query = signal('');
  protected readonly departmentFilter = signal('');
  protected readonly statusFilter = signal('working');
  protected readonly page = signal(0);

  protected readonly rows = signal<EmployeeSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly departments = signal<DepartmentOption[]>([]);

  /** "Hiển thị 1–20 trong 214": vị trí dòng đầu, dòng cuối của trang hiện tại. */
  protected readonly range = computed(() => {
    const from = this.total() === 0 ? 0 : this.page() * PAGE_SIZE + 1;
    return { from, to: this.page() * PAGE_SIZE + this.rows().length, total: this.total() };
  });

  protected readonly formatDate = formatDate;
  protected readonly initials = initials;

  private readonly typing = new Subject<void>();
  private requestSeq = 0;

  constructor() {
    this.typing.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.search());
    this.departmentsApi.list(true).subscribe({
      next: (list) => this.departments.set(departmentOptions(list)),
      error: () => this.departments.set([]),
    });
    this.load();
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    this.typing.next();
  }

  protected onDepartment(value: string): void {
    this.departmentFilter.set(value);
    this.search();
  }

  protected onStatus(value: string): void {
    this.statusFilter.set(value);
    this.search();
  }

  protected goTo(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.page()) {
      return;
    }
    this.page.set(page);
    this.load();
  }

  private search(): void {
    this.page.set(0);
    this.load();
  }

  private load(): void {
    const seq = ++this.requestSeq;
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list({
        q: this.query(),
        departmentId: this.departmentFilter() === '' ? null : Number(this.departmentFilter()),
        statuses: STATUS_FILTERS[this.statusFilter()] ?? [],
        page: this.page(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (result) => {
          // Bỏ kết quả của request cũ về muộn hơn request mới (gõ tìm nhanh)
          if (seq !== this.requestSeq) {
            return;
          }
          this.rows.set(result.content);
          this.total.set(result.totalElements);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          if (seq !== this.requestSeq) {
            return;
          }
          this.rows.set([]);
          this.error.set(this.errors.describe(err, EMPLOYEE_ERROR_PREFIXES));
          this.loading.set(false);
        },
      });
  }
}
