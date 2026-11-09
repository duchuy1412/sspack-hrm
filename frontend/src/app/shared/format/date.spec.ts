import { formatDate, todayIso } from './date';

describe('formatDate', () => {
  it('đổi ngày ISO sang dd/MM/yyyy', () => {
    expect(formatDate('2024-01-02')).toBe('02/01/2024');
    expect(formatDate('2026-11-09T08:00:00+07:00')).toBe('09/11/2026');
  });

  it('giá trị rỗng hoặc sai dạng thành chuỗi rỗng', () => {
    expect(formatDate(null)).toBe('');
    expect(formatDate(undefined)).toBe('');
    expect(formatDate('09/11/2026')).toBe('');
  });
});

describe('todayIso', () => {
  it('lấy ngày theo giờ Việt Nam (UTC+7)', () => {
    expect(todayIso(new Date('2026-11-08T16:59:00Z'))).toBe('2026-11-08');
    expect(todayIso(new Date('2026-11-08T17:00:00Z'))).toBe('2026-11-09');
  });
});
