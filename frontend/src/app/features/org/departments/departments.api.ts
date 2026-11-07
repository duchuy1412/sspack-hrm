import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Department } from './department.models';

const API = '/api/v1/departments';

/** Gọi API cây phòng ban (ORG-01). */
@Injectable({ providedIn: 'root' })
export class DepartmentsApi {
  private readonly http = inject(HttpClient);

  list(includeInactive: boolean): Observable<Department[]> {
    return this.http.get<Department[]>(API, { params: { includeInactive } });
  }

  create(code: string, name: string, parentId: number | null): Observable<Department> {
    return this.http.post<Department>(API, { code, name, parentId });
  }

  update(id: number, code: string, name: string, version: number): Observable<Department> {
    return this.http.put<Department>(`${API}/${id}`, { code, name, version });
  }

  move(id: number, parentId: number | null, version: number): Observable<Department> {
    return this.http.put<Department>(`${API}/${id}/parent`, { parentId, version });
  }

  setActive(id: number, active: boolean, version: number): Observable<Department> {
    return this.http.put<Department>(`${API}/${id}/status`, { active, version });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/${id}`);
  }
}
