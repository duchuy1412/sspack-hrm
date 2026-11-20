# ORG-04 — Chỉ định trưởng đơn vị (thiết kế)

Ngày: 20/11/2026 · Nhánh: `feat/ORG-04-department-head`

## Mục đích

User story ORG-04 (SRS 3.2): *Là HR, tôi chỉ định trưởng đơn vị — trưởng đơn vị tự động là người duyệt cấp 1 của nhân viên trong đơn vị.*

ORG-04 là nền cho mảng nghỉ phép (PLT-06 → LEV-01..03 → PLT-05) theo lộ trình đã chốt ngày 20/11/2026. Kèm theo đó là hai thay đổi đã quyết từ EMP-01 và ORG-03:

- phạm vi dữ liệu `DEPARTMENT_TREE` tính theo "các phòng ban mình làm trưởng", thay cho "phòng ban trong hồ sơ của mình";
- sơ đồ tổ chức đưa trưởng đơn vị lên đầu nhóm.

## Mục tiêu

1. HR (quyền `org:manage`) chỉ định hoặc bỏ chỉ định trưởng cho từng phòng ban.
2. Có một service public trả lời câu hỏi "ai là người duyệt cấp 1 của nhân viên X". PLT-06 sẽ dùng service này cho bước duyệt `DEPARTMENT_HEAD`.
3. `DEPARTMENT_TREE` = hồ sơ của chính mình + mọi nhánh phòng ban mình làm trưởng.
4. Cây phòng ban và sơ đồ tổ chức hiển thị trưởng đơn vị.

## Ngoài phạm vi

- Bộ máy phê duyệt, đơn từ, thông báo (PLT-06, LEV, PLT-05).
- `employees.manager_id` (quản lý trực tiếp): để nguyên cho PLT-06/EMP.
- Tự bỏ chỉ định khi trưởng đơn vị nghỉ việc. Việc này thuộc EMP-06; tạm thời resolver đã bỏ qua người không còn làm việc.
- Ủy quyền duyệt khi trưởng vắng mặt.

## Điều cần tránh

- Chỉ định trưởng làm thay đổi quyền một cách ngầm định.
- Module `org` ghi vào bảng của `platform` hoặc `employee`.
- Có trường hợp không tìm được người duyệt trong khi cây phòng ban vẫn còn trưởng ở cấp trên.

## Các phương án đã cân nhắc

| Phương án | Ưu | Nhược |
| --- | --- | --- |
| **A. Đặt resolver trong `org`, làm việc trên phòng ban (đã chọn)** | Quy tắc nằm cạnh dữ liệu cây (`path`); không thêm phụ thuộc mới giữa các module | PLT-06 phải tự đổi mã nhân viên sang tài khoản qua `employee` |
| B. Đặt resolver trong `employee` | Trả thẳng mã tài khoản | Logic phê duyệt dàn trải sang hồ sơ nhân viên; vẫn phải hỏi `org` về cây |
| C. Để PLT-06 tự tìm | ORG-04 nhỏ nhất | `approval` phải biết cấu trúc cây; ORG-04 không kiểm thử được tiêu chí chấp nhận |

Hai quyết định nghiệp vụ người dùng đã chọn:

- **Không tìm được trưởng → đi ngược lên cây.** Lấy trưởng của phòng ban tổ tiên gần nhất, luôn bỏ qua chính người gửi đơn. Phương án bị loại: báo lỗi; chuyển cho HR.
- **Trưởng đơn vị và vai trò được tách bạch.** Chỉ định không gán hay gỡ vai trò. Giao diện cảnh báo khi người được chỉ định chưa có tài khoản, hoặc chưa có vai trò với phạm vi `DEPARTMENT_TREE`/`ALL`. Phương án bị loại: tự gán MANAGER; bắt buộc có vai trò mới được chỉ định.

## Thiết kế

### Dữ liệu

- Cột `departments.head_employee_id` đã có trong V1 (có khóa ngoại sang `employees`). Migration `V8__department_head.sql` chỉ thêm index `(company_id, head_employee_id) WHERE head_employee_id IS NOT NULL`.
- Entity `Department` thêm `headEmployeeId` và phương thức `assignHead(Long employeeId, OffsetDateTime now)`. Truyền `null` để bỏ chỉ định. Có `@Version` sẵn.

### Ai được làm trưởng

- Nhân viên cùng công ty, trạng thái `PROBATION`, `ACTIVE` hoặc `ON_LEAVE`.
- Không bắt buộc thuộc phòng ban đó (cho phép kiêm nhiệm). Một người có thể làm trưởng nhiều phòng.
- Phòng ban đã ngừng hoạt động: chỉ cho phép bỏ chỉ định, không cho chỉ định mới.
- Việc kiểm tra nhân viên đi qua service public của `employee` (thêm hàm tra trạng thái làm việc theo id), không đọc bảng `employees` từ `org`.

### API

- `PUT /api/v1/departments/{id}/head`, body `{ "employeeId": 123 | null, "version": n }`, quyền `org:manage`. Trả về phòng ban đã cập nhật.
- Ghi audit `UPDATE` trên `departments` với giá trị trước/sau của `head_employee_id`.
- Lỗi theo RFC 7807, thông báo tiếng Việt: nhân viên không tồn tại hoặc đã nghỉ việc; phòng ban đã ngừng hoạt động; xung đột phiên bản.
- `GET /api/v1/departments` (cây) thêm cho mỗi phòng ban: `head { employeeId, code, fullName, warnings[] }`. `warnings` gồm `NO_ACCOUNT` và `NO_MANAGER_SCOPE`. Để tính `NO_MANAGER_SCOPE`, `org` hỏi `employee` lấy mã tài khoản, rồi gọi `platform.application.rbac.DataScopeService.widestScope`.

### Người duyệt cấp 1

`org.application.DepartmentHeadResolver` (public):

```java
Optional<Long> firstLevelApprover(long companyId, long departmentId, long requesterEmployeeId)
```

1. Đọc `path` của phòng ban (`/1/4/12/`), tách ra chuỗi phòng ban từ chính nó lên gốc.
2. Lần lượt xét từng phòng ban. Nhận trưởng đầu tiên thỏa: có trưởng, trưởng ≠ người gửi, trưởng đang làm việc. Trạng thái làm việc lấy theo lô từ `employee`.
3. Đi hết đến gốc mà không có ai thì trả `Optional.empty()`. PLT-06 sẽ quyết cách xử lý (dự kiến báo lỗi cấu hình luồng duyệt).

Phòng ban đã ngừng hoạt động vẫn được xét. Trường hợp này hiếm, vì ngừng hoạt động đòi hỏi không còn nhân viên.

Đây là quy tắc nghiệp vụ nên có unit test đầy đủ: có trưởng; chưa có trưởng; người gửi là trưởng; trưởng đã nghỉ; leo nhiều cấp; không có ai.

### Phạm vi dữ liệu `DEPARTMENT_TREE`

`EmployeeAccess` thay nhánh `DEPARTMENT_TREE`: hợp các `subtreeIds` của mọi phòng ban có `head_employee_id = hồ sơ của người xem` (thêm `OrgDirectory.headedSubtreeIds(companyId, employeeId)`), cộng hồ sơ của chính mình.

Người có vai trò MANAGER mà không làm trưởng phòng nào thì chỉ thấy hồ sơ của mình. Cập nhật Javadoc và các test `EmployeeIT` liên quan.

### Giao diện

```
Cây phòng ban
▸ Xưởng In            XI   Trưởng: Trần Minh Anh            12 NV   [Chỉ định trưởng] …
  ▸ Tổ Offset         XI-OFFSET  Trưởng: — (duyệt bởi Trần Minh Anh)
  ▸ Tổ Flexo          XI-FLEXO   Trưởng: Mai Thị Thu ⚠ Chưa có vai trò Quản lý
```

- Thêm thao tác "Chỉ định trưởng" vào hàng phòng ban. Thao tác mở hộp thoại có ô chọn nhân viên tìm theo tên hoặc mã (dùng `normalizeSearch`, danh sách lấy từ API nhân viên đang làm việc), kèm nút "Bỏ chỉ định".
- Phòng ban chưa có trưởng hiển thị người duyệt thực tế lấy từ resolver (trường `effectiveApprover` trong API cây), để HR thấy ngay hệ quả của quy tắc leo cây.
- Cảnh báo ⚠ có tooltip hướng dẫn sang Quản trị → Tài khoản để gán vai trò.
- Sơ đồ tổ chức: trưởng đơn vị đứng đầu danh sách thành viên của phòng, có nhãn "Trưởng đơn vị". Trưởng kiêm nhiệm (không thuộc phòng) vẫn hiện ở đầu phòng mình làm trưởng.
- Chuỗi giao diện thêm vào `vi.json`/`en.json`. Màu và khoảng cách lấy từ `tokens.css`.

### Dữ liệu demo (chỉ profile dev)

Thêm vào cuối `R__demo_workforce.sql` (file repeatable, chạy lại an toàn) câu lệnh gán trưởng khi phòng ban chưa có trưởng:

| Phòng ban | Trưởng |
| --- | --- |
| BGD | SP0001 |
| NM | SP0002 |
| NS | SP0003 |
| KT | SP0005 |
| KD | SP0007 |
| XI | SP0010 (`minhanh`) |
| XI-OFFSET | SP0012 |
| XCD | SP0016 |
| QC | SP0019 |
| KHO | SP0022 |

Các phòng KD-MB, KD-MN, XI-FLEXO để trống để demo việc leo cây.

### Kiểm thử

- Unit: `DepartmentHeadResolver` (các trường hợp ở trên); `Department.assignHead` (phòng ban ngừng hoạt động).
- Tích hợp (Testcontainers): API chỉ định/bỏ chỉ định, quyền 403, nhân viên đã nghỉ (400/422), xung đột phiên bản, có ghi audit; `DEPARTMENT_TREE` mới; sơ đồ đưa trưởng lên đầu.
- Frontend (Vitest): hộp thoại chỉ định, hiển thị cảnh báo, sơ đồ sắp trưởng lên đầu.
- Trước khi báo xong: `./mvnw verify` (gồm cổng JaCoCo 80% nếu PR #10 đã merge), `npm test -- --watch=false`, `npm run build`, thử trên app.

## Để người làm tự quyết

- Tên lớp và DTO cụ thể, cách gom truy vấn để tránh N+1 khi dựng cây và tính cảnh báo.
- Bố cục chi tiết của hộp thoại, theo các hộp thoại sẵn có trong `departments-page`.
