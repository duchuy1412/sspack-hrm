import { diffValues, formatValue } from './audit.models';

describe('diffValues (PLT-04)', () => {
  it('sửa: đánh dấu trường đổi, giữ thứ tự trường', () => {
    const rows = diffValues(
      { code: 'TRUONG_CA', name: 'Trưởng ca', description: null },
      { code: 'TRUONG_CA', name: 'Trưởng ca sản xuất', description: null },
    );

    expect(rows.map((r) => [r.field, r.changed])).toEqual([
      ['code', false],
      ['name', true],
      ['description', false],
    ]);
  });

  it('danh sách: tách phần tử được thêm và bị gỡ', () => {
    const [row] = diffValues(
      { permissions: ['leave:read', 'leave:approve'] },
      { permissions: ['leave:read', 'attendance:confirm'] },
    );

    expect(row.changed).toBe(true);
    expect(row.added).toEqual(['attendance:confirm']);
    expect(row.removed).toEqual(['leave:approve']);
  });

  it('tạo mới và xóa: một phía rỗng', () => {
    expect(diffValues(null, { code: 'X' })).toEqual([
      { field: 'code', before: undefined, after: 'X', changed: true, added: [], removed: [] },
    ]);
    expect(diffValues({ code: 'X' }, null)[0]).toMatchObject({
      before: 'X',
      after: undefined,
      changed: true,
    });
  });

  it('trường chỉ có ở bản sau nằm cuối', () => {
    expect(diffValues({ a: 1 }, { a: 1, b: 2 }).map((r) => r.field)).toEqual(['a', 'b']);
  });
});

describe('formatValue', () => {
  it('rỗng thành gạch ngang, danh sách nối bằng dấu phẩy', () => {
    expect(formatValue(null)).toBe('—');
    expect(formatValue(undefined)).toBe('—');
    expect(formatValue([])).toBe('—');
    expect(formatValue(['a', 'b'])).toBe('a, b');
    expect(formatValue(3)).toBe('3');
    expect(formatValue({ k: 1 })).toBe('{"k":1}');
  });
});
