import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { normalizeSearch } from '../../../shared/format/search';

/** Người trên sơ đồ (ORG-03), khớp `OrgChartController.MemberResponse`: chỉ thông tin công việc. */
export interface ChartMember {
  id: number;
  code: string;
  fullName: string;
  positionName: string | null;
  workEmail: string | null;
  /** null cho đến khi có ảnh hồ sơ (EMP-07); khi đó hiện chữ cái đầu. */
  avatarUrl: string | null;
  /** Người xem mở được hồ sơ đầy đủ. */
  profileVisible: boolean;
  self: boolean;
}

/** Phòng ban đang hoạt động, server trả phẳng theo thứ tự cây. */
export interface ChartDepartment {
  id: number;
  parentId: number | null;
  code: string;
  name: string;
  depth: number;
  members: ChartMember[];
}

export interface ChartNode {
  department: ChartDepartment;
  children: ChartNode[];
  /** Số người của phòng ban và mọi phòng ban cấp dưới. */
  total: number;
}

/** Kết quả tìm: người khớp theo thứ tự hiển thị và các phòng ban phải mở để thấy họ. */
export interface ChartSearch {
  matches: { member: ChartMember; departmentId: number }[];
  memberIds: Set<number>;
  openIds: Set<number>;
}

@Injectable({ providedIn: 'root' })
export class OrgChartApi {
  private readonly http = inject(HttpClient);

  chart(): Observable<ChartDepartment[]> {
    return this.http.get<ChartDepartment[]>('/api/v1/org-chart');
  }
}

/** Dựng cây; con xếp theo tên. Phòng ban có cha không nằm trong danh sách (đã ngừng) thành gốc. */
export function buildChart(departments: readonly ChartDepartment[]): ChartNode[] {
  const nodes = new Map<number, ChartNode>();
  for (const department of departments) {
    nodes.set(department.id, { department, children: [], total: 0 });
  }
  const roots: ChartNode[] = [];
  for (const node of nodes.values()) {
    const parentId = node.department.parentId;
    const parent = parentId === null ? undefined : nodes.get(parentId);
    (parent ? parent.children : roots).push(node);
  }
  const finish = (list: ChartNode[]): number => {
    list.sort((a, b) => a.department.name.localeCompare(b.department.name, 'vi'));
    let sum = 0;
    for (const n of list) {
      n.total = n.department.members.length + finish(n.children);
      sum += n.total;
    }
    return sum;
  };
  finish(roots);
  return roots;
}

/**
 * Tìm người theo họ tên hoặc mã (không phân biệt hoa thường, dấu). Trả null khi không có từ khóa. Thứ tự kết
 * quả là thứ tự đọc trên sơ đồ, để nút "kết quả sau" đi từ trên xuống.
 */
export function searchChart(roots: readonly ChartNode[], query: string): ChartSearch | null {
  const q = normalizeSearch(query);
  if (!q) {
    return null;
  }
  const result: ChartSearch = { matches: [], memberIds: new Set(), openIds: new Set() };
  const visit = (node: ChartNode): boolean => {
    let found = false;
    for (const member of node.department.members) {
      if (normalizeSearch(`${member.fullName} ${member.code}`).includes(q)) {
        result.matches.push({ member, departmentId: node.department.id });
        result.memberIds.add(member.id);
        found = true;
      }
    }
    for (const child of node.children) {
      found = visit(child) || found;
    }
    if (found) {
      result.openIds.add(node.department.id);
    }
    return found;
  };
  roots.forEach((r) => visit(r));
  return result;
}
