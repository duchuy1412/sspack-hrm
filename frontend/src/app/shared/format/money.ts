/**
 * Định dạng và đọc tiền VND theo quy ước hiển thị của dự án: `18.450.000 ₫` (dấu chấm ngăn nghìn,
 * không có phần lẻ). Tự viết thay vì `Intl` để kết quả giống nhau trên mọi trình duyệt và trong test.
 */

/** Ngăn nghìn bằng dấu chấm: 18450000 → `18.450.000`. */
export function groupThousands(value: number | string): string {
  const digits = String(value).replace(/^-/, '');
  const sign = String(value).startsWith('-') ? '-' : '';
  return sign + digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** 18450000 → `18.450.000 ₫`; null/undefined → chuỗi rỗng. */
export function formatVnd(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '';
  }
  return `${groupThousands(Math.trunc(value))} ₫`;
}

/** Khung lương: `6.000.000 ₫ – 9.000.000 ₫`; một mức thì chỉ hiện một số. */
export function formatVndRange(min: number, max: number): string {
  return min === max ? formatVnd(min) : `${formatVnd(min)} – ${formatVnd(max)}`;
}

/**
 * Đọc số tiền người dùng gõ: bỏ dấu chấm ngăn nghìn, khoảng trắng và ký hiệu ₫. Trả null nếu rỗng hoặc
 * không phải số nguyên không âm (vd có dấu phẩy thập phân, chữ cái).
 */
export function parseVnd(input: string | null | undefined): number | null {
  const cleaned = (input ?? '').replace(/[.\s₫]/g, '').replace(/đ$/i, '');
  if (cleaned === '' || !/^\d+$/.test(cleaned)) {
    return null;
  }
  return Number(cleaned);
}
