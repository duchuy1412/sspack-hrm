import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type EmployeeStatus = 'PROBATION' | 'ACTIVE' | 'ON_LEAVE' | 'RESIGNED' | 'TERMINATED';
export type Gender = 'MALE' | 'FEMALE' | 'OTHER';

export const EMPLOYEE_STATUSES: readonly EmployeeStatus[] = [
  'PROBATION',
  'ACTIVE',
  'ON_LEAVE',
  'RESIGNED',
  'TERMINATED',
];
export const GENDERS: readonly Gender[] = ['MALE', 'FEMALE', 'OTHER'];
/** Trạng thái chọn được khi tạo hồ sơ. */
export const CREATE_STATUSES: readonly EmployeeStatus[] = ['PROBATION', 'ACTIVE'];

/** Một dòng danh sách (EMP-01), khớp `employee.api.EmployeeDtos.EmployeeSummaryResponse`. */
export interface EmployeeSummary {
  id: number;
  code: string;
  fullName: string;
  workEmail: string | null;
  departmentId: number;
  departmentName: string | null;
  positionId: number | null;
  positionName: string | null;
  /** `yyyy-MM-dd` */
  hireDate: string;
  status: EmployeeStatus;
}

/** Hồ sơ đầy đủ, khớp `EmployeeDtos.EmployeeResponse`. Trường cá nhân null khi `personalVisible` là false. */
export interface Employee {
  id: number;
  code: string;
  fullName: string;
  gender: Gender | null;
  dateOfBirth: string | null;
  workEmail: string | null;
  personalEmail: string | null;
  phone: string | null;
  address: string | null;
  departmentId: number;
  departmentName: string | null;
  departmentActive: boolean;
  positionId: number | null;
  positionName: string | null;
  positionActive: boolean;
  hireDate: string;
  status: EmployeeStatus;
  personalVisible: boolean;
  accountLinked: boolean;
  editable: boolean;
  allowedStatuses: EmployeeStatus[];
  version: number;
  updatedAt: string;
}

export interface EmployeeInput {
  code: string;
  fullName: string;
  gender: Gender | null;
  dateOfBirth: string | null;
  workEmail: string | null;
  personalEmail: string | null;
  phone: string | null;
  address: string | null;
  departmentId: number;
  positionId: number;
  hireDate: string;
  status: EmployeeStatus;
}

export interface EmployeeQuery {
  q: string;
  departmentId: number | null;
  statuses: readonly EmployeeStatus[];
  page: number;
  size: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const API = '/api/v1/employees';

/** Gọi API hồ sơ nhân viên (EMP-01). Server tự lọc theo phạm vi dữ liệu của người đăng nhập. */
@Injectable({ providedIn: 'root' })
export class EmployeesApi {
  private readonly http = inject(HttpClient);

  list(query: EmployeeQuery): Observable<Page<EmployeeSummary>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q.trim()) {
      params = params.set('q', query.q.trim());
    }
    if (query.departmentId !== null) {
      params = params.set('departmentId', query.departmentId);
    }
    for (const status of query.statuses) {
      params = params.append('status', status);
    }
    return this.http.get<Page<EmployeeSummary>>(API, { params });
  }

  get(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${API}/${id}`);
  }

  nextCode(): Observable<{ code: string }> {
    return this.http.get<{ code: string }>(`${API}/next-code`);
  }

  create(input: EmployeeInput): Observable<Employee> {
    return this.http.post<Employee>(API, input);
  }

  update(id: number, input: EmployeeInput, version: number): Observable<Employee> {
    return this.http.put<Employee>(`${API}/${id}`, { ...input, version });
  }
}

/** Chữ cái đầu của hai từ cuối trong họ tên: "Phạm Văn Đức" → "VĐ". */
export function initials(fullName: string): string {
  const words = fullName.trim().split(/\s+/).filter(Boolean);
  return words
    .slice(-2)
    .map((w) => w.charAt(0))
    .join('')
    .toUpperCase();
}
