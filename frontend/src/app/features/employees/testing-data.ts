import { Department } from '../org/departments/department.models';
import { Position } from '../org/org-catalog.api';
import { Employee, EmployeeSummary } from './employees.api';

/** Dữ liệu hư cấu dùng chung cho spec của phân hệ Nhân viên. */
export function department(id: number, name: string, path: string, active = true): Department {
  return {
    id,
    parentId: null,
    code: `D${id}`,
    name,
    path,
    depth: path.split('/').filter(Boolean).length,
    active,
    childCount: 0,
    employeeCount: 0,
    workingEmployeeCount: 0,
    version: 0,
    updatedAt: '2026-11-09T09:00:00+07:00',
  };
}

export const DEPARTMENTS: Department[] = [
  { ...department(1, 'Nhà máy Bình Dương', '/1/'), childCount: 1 },
  { ...department(2, 'Xưởng In', '/1/2/'), parentId: 1 },
  department(3, 'Phòng cũ', '/3/', false),
];

export function position(id: number, code: string, name: string, active = true): Position {
  return {
    id,
    code,
    name,
    jobGradeId: 1,
    jobGradeCode: 'G2',
    jobGradeName: 'Công nhân lành nghề',
    jobGradeActive: true,
    active,
    employeeCount: 0,
    workingEmployeeCount: 0,
    version: 0,
    updatedAt: '2026-11-09T09:00:00+07:00',
  };
}

export const POSITIONS: Position[] = [
  position(10, 'CN-IN', 'Công nhân vận hành máy in'),
  position(11, 'QUAN-DOC', 'Quản đốc xưởng'),
  position(12, 'CU', 'Chức danh cũ', false),
];

export function summary(
  id: number,
  code: string,
  fullName: string,
  extra: Partial<EmployeeSummary> = {},
): EmployeeSummary {
  return {
    id,
    code,
    fullName,
    workEmail: `${code.toLowerCase()}@sspack.vn`,
    departmentId: 2,
    departmentName: 'Xưởng In',
    positionId: 10,
    positionName: 'Công nhân vận hành máy in',
    hireDate: '2021-08-15',
    status: 'ACTIVE',
    ...extra,
  };
}

export function employee(extra: Partial<Employee> = {}): Employee {
  return {
    id: 11,
    code: 'SP0011',
    fullName: 'Phạm Văn Đức',
    gender: 'MALE',
    dateOfBirth: '1991-05-15',
    workEmail: 'vanduc.pham@sspack.vn',
    personalEmail: null,
    phone: '0903110011',
    address: null,
    departmentId: 2,
    departmentName: 'Xưởng In',
    departmentActive: true,
    positionId: 10,
    positionName: 'Công nhân vận hành máy in',
    positionActive: true,
    hireDate: '2021-08-15',
    status: 'ACTIVE',
    personalVisible: true,
    accountLinked: true,
    editable: true,
    allowedStatuses: ['PROBATION', 'ACTIVE', 'ON_LEAVE'],
    version: 2,
    updatedAt: '2026-11-09T09:00:00+07:00',
    ...extra,
  };
}
