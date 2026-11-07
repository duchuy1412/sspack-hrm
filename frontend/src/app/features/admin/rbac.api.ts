import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { Observable } from 'rxjs';

import { ApiProblem } from '../../core/auth/auth.models';
import {
  CreateRolePayload,
  Page,
  PermissionItem,
  RoleDetail,
  RoleSummary,
  UpdateRolePayload,
  UserWithRoles,
} from './rbac.models';

const API = '/api/v1';

/** Gọi API phân quyền (PLT-03). */
@Injectable({ providedIn: 'root' })
export class RbacApi {
  private readonly http = inject(HttpClient);

  permissions(): Observable<PermissionItem[]> {
    return this.http.get<PermissionItem[]>(`${API}/permissions`);
  }

  roles(page = 0, size = 50): Observable<Page<RoleSummary>> {
    return this.http.get<Page<RoleSummary>>(`${API}/roles`, { params: { page, size } });
  }

  role(id: number): Observable<RoleDetail> {
    return this.http.get<RoleDetail>(`${API}/roles/${id}`);
  }

  createRole(payload: CreateRolePayload): Observable<RoleDetail> {
    return this.http.post<RoleDetail>(`${API}/roles`, payload);
  }

  updateRole(id: number, payload: UpdateRolePayload): Observable<RoleDetail> {
    return this.http.put<RoleDetail>(`${API}/roles/${id}`, payload);
  }

  deleteRole(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/roles/${id}`);
  }

  users(query: string, page = 0, size = 20): Observable<Page<UserWithRoles>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http.get<Page<UserWithRoles>>(`${API}/users`, { params });
  }

  assignRoles(userId: number, roleIds: number[], version: number): Observable<UserWithRoles> {
    return this.http.put<UserWithRoles>(`${API}/users/${userId}/roles`, { roleIds, version });
  }
}

/** Lỗi API thành câu tiếng Việt: ưu tiên chuỗi i18n theo mã lỗi, rồi đến `detail` của backend. */
@Injectable({ providedIn: 'root' })
export class RbacErrorMessages {
  private readonly transloco = inject(TranslocoService);

  /** @param prefixes nhóm chuỗi i18n tra mã lỗi theo thứ tự, vd `['org.errors', 'admin.errors']` */
  describe(err: unknown, prefixes: readonly string[] = ['admin.errors']): string {
    if (!(err instanceof HttpErrorResponse)) {
      return this.transloco.translate('admin.errors.generic');
    }
    if (err.status === 0) {
      return this.transloco.translate('admin.errors.network');
    }
    const problem = err.error as ApiProblem | null;
    if (problem?.code) {
      for (const prefix of prefixes) {
        const key = `${prefix}.${problem.code}`;
        const translated = this.transloco.translate(
          key,
          problem as unknown as Record<string, unknown>,
        );
        if (translated !== key) {
          return translated;
        }
      }
    }
    return problem?.detail ?? this.transloco.translate('admin.errors.generic');
  }

  /** Lỗi từng trường (VALIDATION_FAILED) để hiện dưới ô nhập. */
  fieldErrors(err: unknown): Record<string, string> {
    if (err instanceof HttpErrorResponse) {
      return (err.error as ApiProblem | null)?.errors ?? {};
    }
    return {};
  }
}
