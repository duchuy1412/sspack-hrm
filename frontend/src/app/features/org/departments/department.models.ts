/** Kiểu dữ liệu và hàm thuần cho cây phòng ban (ORG-01), khớp `org.api.DepartmentDtos`. */

export interface Department {
  id: number;
  parentId: number | null;
  code: string;
  name: string;
  /** Id các cấp từ gốc tới chính nó, vd `/1/4/12/`. */
  path: string;
  depth: number;
  active: boolean;
  childCount: number;
  employeeCount: number;
  workingEmployeeCount: number;
  version: number;
  updatedAt: string;
}

export interface DepartmentNode {
  department: Department;
  children: DepartmentNode[];
}

/** Dựng cây từ danh sách phẳng; con xếp theo tên. Phòng ban có cha không nằm trong danh sách thành gốc. */
export function buildTree(departments: readonly Department[]): DepartmentNode[] {
  const nodes = new Map<number, DepartmentNode>();
  for (const department of departments) {
    nodes.set(department.id, { department, children: [] });
  }
  const roots: DepartmentNode[] = [];
  for (const node of nodes.values()) {
    const parent =
      node.department.parentId === null ? undefined : nodes.get(node.department.parentId);
    (parent ? parent.children : roots).push(node);
  }
  const sort = (list: DepartmentNode[]) => {
    list.sort((a, b) => a.department.name.localeCompare(b.department.name, 'vi'));
    list.forEach((n) => sort(n.children));
  };
  sort(roots);
  return roots;
}

/** Duyệt cây theo thứ tự hiển thị (cha trước, con theo tên), trả danh sách phẳng. */
export function flattenTree(nodes: readonly DepartmentNode[]): Department[] {
  return nodes.flatMap((n) => [n.department, ...flattenTree(n.children)]);
}

/**
 * Lọc cây theo tên hoặc mã (không phân biệt hoa thường, dấu): giữ phòng ban khớp cùng mọi cấp trên của nó.
 * Trả null khi không có từ khóa.
 */
export function matchingIds(departments: readonly Department[], query: string): Set<number> | null {
  const q = normalize(query);
  if (!q) {
    return null;
  }
  const byId = new Map(departments.map((d) => [d.id, d]));
  const keep = new Set<number>();
  for (const d of departments) {
    if (normalize(d.name).includes(q) || normalize(d.code).includes(q)) {
      for (const id of ancestorIds(d)) {
        if (byId.has(id)) {
          keep.add(id);
        }
      }
    }
  }
  return keep;
}

/** Id của chính nó và mọi cấp trên, đọc từ path. */
export function ancestorIds(department: Department): number[] {
  return department.path.split('/').filter(Boolean).map(Number);
}

/**
 * Có thả `dragged` vào dưới `target` được không: không vào chính nó, không vào cấp dưới của nó, không vào
 * phòng ban ngừng hoạt động, không thả lại vào cấp cha hiện tại. `target` null = cấp cao nhất.
 */
export function canMoveTo(dragged: Department, target: Department | null): boolean {
  if (target === null) {
    return dragged.parentId !== null;
  }
  return target.active && !target.path.startsWith(dragged.path) && target.id !== dragged.parentId;
}

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim();
}
