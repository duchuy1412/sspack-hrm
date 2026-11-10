/** Chuẩn hóa để tìm không phân biệt hoa thường và dấu tiếng Việt: "ky su" khớp "Kỹ sư", "duc" khớp "Đức". */
export function normalizeSearch(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim();
}
