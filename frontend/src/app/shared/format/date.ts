/**
 * Định dạng ngày theo quy ước hiển thị của dự án (`dd/MM/yyyy`). Ngày từ API là chuỗi ISO `yyyy-MM-dd`
 * (`LocalDate`), nên tách chuỗi trực tiếp thay vì qua `Date` để không lệch ngày vì múi giờ trình duyệt.
 */

/** `2024-01-02` → `02/01/2024`; null, undefined hoặc chuỗi sai dạng → chuỗi rỗng. */
export function formatDate(iso: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
  return match ? `${match[3]}/${match[2]}/${match[1]}` : '';
}

/** Ngày hôm nay theo giờ Việt Nam, dạng `yyyy-MM-dd` (giá trị cho `<input type="date">`). */
export function todayIso(now: Date = new Date()): string {
  const vn = new Date(now.getTime() + 7 * 60 * 60 * 1000);
  return vn.toISOString().slice(0, 10);
}
