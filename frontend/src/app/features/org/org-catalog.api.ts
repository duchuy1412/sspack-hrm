import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Cấp bậc kèm khung lương (ORG-02), khớp `org.api.JobGradeDtos.JobGradeResponse`. Tiền tính bằng đồng. */
export interface JobGrade {
  id: number;
  code: string;
  name: string;
  minSalary: number;
  maxSalary: number;
  active: boolean;
  /** Số chức danh thuộc cấp bậc, kể cả đã ngừng hoạt động. */
  positionCount: number;
  activePositionCount: number;
  version: number;
  updatedAt: string;
}

/** Chức danh (ORG-02), khớp `org.api.PositionDtos.PositionResponse`. Không có khung lương. */
export interface Position {
  id: number;
  code: string;
  name: string;
  jobGradeId: number;
  jobGradeCode: string;
  jobGradeName: string;
  jobGradeActive: boolean;
  active: boolean;
  employeeCount: number;
  workingEmployeeCount: number;
  version: number;
  updatedAt: string;
}

export interface JobGradeInput {
  code: string;
  name: string;
  minSalary: number;
  maxSalary: number;
}

export interface PositionInput {
  code: string;
  name: string;
  jobGradeId: number;
}

const GRADES = '/api/v1/job-grades';
const POSITIONS = '/api/v1/positions';

/** Gọi API danh mục cấp bậc và chức danh (ORG-02). */
@Injectable({ providedIn: 'root' })
export class OrgCatalogApi {
  private readonly http = inject(HttpClient);

  listGrades(includeInactive: boolean): Observable<JobGrade[]> {
    return this.http.get<JobGrade[]>(GRADES, { params: { includeInactive } });
  }

  createGrade(input: JobGradeInput): Observable<JobGrade> {
    return this.http.post<JobGrade>(GRADES, input);
  }

  updateGrade(id: number, input: JobGradeInput, version: number): Observable<JobGrade> {
    return this.http.put<JobGrade>(`${GRADES}/${id}`, { ...input, version });
  }

  setGradeActive(id: number, active: boolean, version: number): Observable<JobGrade> {
    return this.http.put<JobGrade>(`${GRADES}/${id}/status`, { active, version });
  }

  deleteGrade(id: number): Observable<void> {
    return this.http.delete<void>(`${GRADES}/${id}`);
  }

  listPositions(includeInactive: boolean): Observable<Position[]> {
    return this.http.get<Position[]>(POSITIONS, { params: { includeInactive } });
  }

  createPosition(input: PositionInput): Observable<Position> {
    return this.http.post<Position>(POSITIONS, input);
  }

  updatePosition(id: number, input: PositionInput, version: number): Observable<Position> {
    return this.http.put<Position>(`${POSITIONS}/${id}`, { ...input, version });
  }

  setPositionActive(id: number, active: boolean, version: number): Observable<Position> {
    return this.http.put<Position>(`${POSITIONS}/${id}/status`, { active, version });
  }

  deletePosition(id: number): Observable<void> {
    return this.http.delete<void>(`${POSITIONS}/${id}`);
  }
}
