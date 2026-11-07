/** Kiểu dữ liệu API nhật ký thao tác (PLT-04), khớp `platform.api.audit`. */

export const AUDIT_ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'EXPORT'] as const;
export type AuditAction = (typeof AUDIT_ACTIONS)[number];

/** Bảng đang được ghi nhật ký; khớp enum `AuditEntity` ở backend. */
export const AUDIT_ENTITIES = ['roles', 'users', 'refresh_tokens', 'departments'] as const;

export type JsonValue =
  string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type JsonObject = { [key: string]: JsonValue };

export interface AuditLogEntry {
  id: number;
  createdAt: string;
  userId: number | null;
  userEmail: string | null;
  action: AuditAction;
  entityType: string;
  entityId: number | null;
  oldValue: JsonObject | null;
  newValue: JsonObject | null;
  ipAddress: string | null;
}

export interface AuditActor {
  id: number;
  email: string;
}

export interface AuditFilter {
  from: string;
  to: string;
  userId: string;
  entityType: string;
  action: string;
}

/** Một dòng so sánh trước/sau. Với danh sách, `added`/`removed` cho biết phần tử được thêm/gỡ. */
export interface DiffRow {
  field: string;
  before: JsonValue | undefined;
  after: JsonValue | undefined;
  changed: boolean;
  added: JsonValue[];
  removed: JsonValue[];
}

/**
 * So sánh giá trị trước/sau theo từng trường cấp một, giữ thứ tự trường như backend ghi
 * (trường của bản trước rồi trường chỉ có ở bản sau).
 */
export function diffValues(before: JsonObject | null, after: JsonObject | null): DiffRow[] {
  const fields = [...Object.keys(before ?? {}), ...Object.keys(after ?? {})].filter(
    (field, index, all) => all.indexOf(field) === index,
  );
  return fields.map((field) => {
    const b = before ? before[field] : undefined;
    const a = after ? after[field] : undefined;
    const added = Array.isArray(a) ? a.filter((x) => !containsValue(b, x)) : [];
    const removed = Array.isArray(b) ? b.filter((x) => !containsValue(a, x)) : [];
    return { field, before: b, after: a, changed: !sameValue(b, a), added, removed };
  });
}

/** Giá trị JSON thành chữ để hiển thị; rỗng hiển thị bằng gạch ngang. */
export function formatValue(value: JsonValue | undefined): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? '—' : value.map((v) => formatValue(v)).join(', ');
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function containsValue(list: JsonValue | undefined, value: JsonValue): boolean {
  return Array.isArray(list) && list.some((x) => sameValue(x, value));
}

function sameValue(a: JsonValue | undefined, b: JsonValue | undefined): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
