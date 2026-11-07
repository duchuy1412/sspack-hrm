import { Department, buildTree, canMoveTo, flattenTree, matchingIds } from './department.models';

function dept(id: number, path: string, name: string, extra: Partial<Department> = {}): Department {
  const ids = path.split('/').filter(Boolean).map(Number);
  return {
    id,
    parentId: ids.length > 1 ? ids[ids.length - 2] : null,
    code: name.toUpperCase().replace(/\s+/g, '-'),
    name,
    path,
    depth: ids.length,
    active: true,
    childCount: 0,
    employeeCount: 0,
    workingEmployeeCount: 0,
    version: 0,
    updatedAt: '2026-11-07T09:00:00+07:00',
    ...extra,
  };
}

const NM = dept(1, '/1/', 'Nhà máy');
const XI = dept(2, '/1/2/', 'Xưởng In');
const OFFSET = dept(3, '/1/2/3/', 'Tổ In offset');
const KHO = dept(4, '/1/4/', 'Kho vận');
const KVP = dept(5, '/5/', 'Khối Văn phòng');
const D12 = dept(12, '/12/', 'Đội 12');
const ALL = [NM, XI, OFFSET, KHO, KVP, D12];

describe('buildTree (ORG-01)', () => {
  it('dựng cây nhiều cấp, con xếp theo tên tiếng Việt', () => {
    const roots = buildTree(ALL);

    expect(roots.map((r) => r.department.name)).toEqual(['Đội 12', 'Khối Văn phòng', 'Nhà máy']);
    const nm = roots.find((r) => r.department.id === 1)!;
    expect(nm.children.map((c) => c.department.name)).toEqual(['Kho vận', 'Xưởng In']);
    expect(nm.children[1].children[0].department.name).toBe('Tổ In offset');
  });

  it('flattenTree: thứ tự hiển thị, cha trước con', () => {
    expect(flattenTree(buildTree(ALL)).map((d) => d.id)).toEqual([12, 5, 1, 4, 2, 3]);
  });
});

describe('matchingIds', () => {
  it('giữ phòng ban khớp cùng mọi cấp trên; bỏ dấu khi so', () => {
    expect(matchingIds(ALL, 'offset')).toEqual(new Set([1, 2, 3]));
    expect(matchingIds(ALL, 'xuong in')).toEqual(new Set([1, 2]));
    expect(matchingIds(ALL, 'doi')).toEqual(new Set([12]));
  });

  it('không có từ khóa: không lọc', () => {
    expect(matchingIds(ALL, '  ')).toBeNull();
  });
});

describe('canMoveTo', () => {
  it('không thả vào chính nó, cấp dưới của nó hay cấp cha hiện tại', () => {
    expect(canMoveTo(XI, XI)).toBe(false);
    expect(canMoveTo(NM, OFFSET)).toBe(false);
    expect(canMoveTo(XI, NM)).toBe(false);
    expect(canMoveTo(XI, KVP)).toBe(true);
  });

  it('id có tiền tố giống nhau không bị coi là cấp dưới', () => {
    expect(canMoveTo(NM, D12)).toBe(true);
  });

  it('không thả vào phòng ban ngừng hoạt động', () => {
    expect(canMoveTo(XI, { ...KVP, active: false })).toBe(false);
  });

  it('cấp cao nhất: chỉ khi đang có cấp cha', () => {
    expect(canMoveTo(XI, null)).toBe(true);
    expect(canMoveTo(NM, null)).toBe(false);
  });
});
