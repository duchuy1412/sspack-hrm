import { DataScope } from '../../core/auth/auth.models';

/** Kiểu dữ liệu API phân quyền (PLT-03), khớp DTO trong backend `platform.api.rbac`. */

export const DATA_SCOPES: readonly DataScope[] = [
  'ALL',
  'DEPARTMENT_TREE',
  'DEPARTMENT',
  'SELF',
  'ASSIGNED',
];

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PermissionItem {
  code: string;
  module: string;
  description: string | null;
}

export interface PermissionGroup {
  module: string;
  permissions: PermissionItem[];
}

export interface RoleSummary {
  id: number;
  code: string;
  name: string;
  description: string | null;
  dataScope: DataScope;
  system: boolean;
  permissionCount: number;
  assignedUserCount: number;
  updatedAt: string;
}

export interface RoleDetail {
  id: number;
  code: string;
  name: string;
  description: string | null;
  dataScope: DataScope;
  system: boolean;
  permissions: string[];
  assignedUserCount: number;
  version: number;
  updatedAt: string;
}

export interface RolePayload {
  name: string;
  description: string | null;
  dataScope: DataScope;
  permissions: string[];
}

export interface CreateRolePayload extends RolePayload {
  code: string;
}

export interface UpdateRolePayload extends RolePayload {
  version: number;
}

export interface RoleRef {
  id: number;
  code: string;
  name: string;
}

export interface UserWithRoles {
  id: number;
  email: string;
  status: string;
  roles: RoleRef[];
  version: number;
}

/** Nhóm quyền theo phân hệ, giữ thứ tự backend trả về (đã sắp theo phân hệ rồi mã). */
export function groupByModule(permissions: readonly PermissionItem[]): PermissionGroup[] {
  const groups = new Map<string, PermissionItem[]>();
  for (const permission of permissions) {
    const list = groups.get(permission.module) ?? [];
    list.push(permission);
    groups.set(permission.module, list);
  }
  return Array.from(groups, ([module, list]) => ({ module, permissions: list }));
}
