import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page } from '../rbac.models';
import { AuditActor, AuditFilter, AuditLogEntry } from './audit.models';

const API = '/api/v1/audit-logs';

/** Gọi API nhật ký thao tác (PLT-04). */
@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly http = inject(HttpClient);

  search(filter: AuditFilter, page = 0, size = 20): Observable<Page<AuditLogEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filter)) {
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<Page<AuditLogEntry>>(API, { params });
  }

  actors(): Observable<AuditActor[]> {
    return this.http.get<AuditActor[]>(`${API}/actors`);
  }
}
