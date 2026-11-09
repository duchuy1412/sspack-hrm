import { formatVnd, formatVndRange, groupThousands, parseVnd } from './money';

describe('money (định dạng VND)', () => {
  it('ngăn nghìn bằng dấu chấm và thêm ₫', () => {
    expect(formatVnd(18450000)).toBe('18.450.000 ₫');
    expect(formatVnd(950)).toBe('950 ₫');
    expect(formatVnd(0)).toBe('0 ₫');
    expect(groupThousands(-1234567)).toBe('-1.234.567');
  });

  it('giá trị trống thành chuỗi rỗng', () => {
    expect(formatVnd(null)).toBe('');
    expect(formatVnd(undefined)).toBe('');
  });

  it('khung lương hai mức và một mức', () => {
    expect(formatVndRange(6000000, 9000000)).toBe('6.000.000 ₫ – 9.000.000 ₫');
    expect(formatVndRange(12000000, 12000000)).toBe('12.000.000 ₫');
  });

  it('đọc số gõ có dấu chấm, khoảng trắng, ký hiệu tiền', () => {
    expect(parseVnd('12.000.000')).toBe(12000000);
    expect(parseVnd(' 8 500 000 ₫')).toBe(8500000);
    expect(parseVnd('7000000đ')).toBe(7000000);
  });

  it('từ chối số thập phân, chữ, chuỗi rỗng', () => {
    expect(parseVnd('12000000,5')).toBeNull();
    expect(parseVnd('abc')).toBeNull();
    expect(parseVnd('-5')).toBeNull();
    expect(parseVnd('')).toBeNull();
    expect(parseVnd(null)).toBeNull();
  });
});
