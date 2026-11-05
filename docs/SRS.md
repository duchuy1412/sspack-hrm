# SRS — Hệ thống HRM (MVP)

Nov 5, 2026 · @Ngo Duc Huy

## 1. Giới thiệu

Hệ thống HRM web cho doanh nghiệp vừa và nhỏ (50–2.000 nhân viên), xây dựng như một sản phẩm demo hoàn chỉnh để đưa vào CV và phỏng vấn cho vị trí HRM Developer.

**Mục tiêu sản phẩm**

- Bao phủ trọn vòng đời nhân sự: tuyển dụng → tiếp nhận → quản lý hồ sơ → chấm công, nghỉ phép → tính lương → nghỉ việc.
- Thể hiện năng lực fullstack: Spring Boot, Angular, PostgreSQL, Kafka, Docker, CI/CD.
- Có link demo công khai, tài khoản mẫu cho từng vai trò và dữ liệu mẫu trông như thật.

**Phạm vi**

| Mức ưu tiên | Phân hệ | Thời lượng dự kiến |
| --- | --- | --- |
| Ưu tiên 1 | Nền tảng, Cơ cấu tổ chức, Hồ sơ nhân viên, Nghỉ phép, Cổng tự phục vụ | 4–5 tuần |
| Ưu tiên 2 | Chấm công, Tính lương, Tuyển dụng, Dashboard HR | 4–5 tuần |
| Ưu tiên 3 | Đánh giá KPI, Đào tạo | Khi còn thời gian |

Ngoài phạm vi: kế toán, tích hợp máy chấm công phần cứng, tích hợp cổng BHXH điện tử, ứng dụng mobile native.

**Thuật ngữ**

| Thuật ngữ | Nghĩa |
| --- | --- |
| HRM | Human Resource Management — quản lý nhân sự |
| RBAC | Role-Based Access Control — phân quyền theo vai trò |
| ESS | Employee Self-Service — cổng tự phục vụ cho nhân viên |
| Quỹ phép | Số ngày phép năm còn lại của một nhân viên |
| Kỳ lương | Một tháng tính lương, có trạng thái Nháp → Đã chốt → Đã chi trả |
| Tham số lương | Giá trị theo quy định (mức giảm trừ, tỷ lệ BHXH, lương tối thiểu vùng) lưu kèm ngày hiệu lực |

## 2. Tổng quan hệ thống

Hệ thống có 5 vai trò; quyền được giới hạn theo cả chức năng và phạm vi dữ liệu (toàn công ty, phòng ban mình quản lý, hoặc chỉ bản thân).

**Vai trò người dùng**

| Vai trò | Mô tả | Phạm vi dữ liệu |
| --- | --- | --- |
| Quản trị hệ thống (Admin) | Cấu hình hệ thống, tài khoản, vai trò, tham số | Toàn hệ thống |
| Nhân sự (HR) | Quản lý hồ sơ, hợp đồng, chấm công, tính lương, tuyển dụng | Toàn công ty |
| Quản lý trực tiếp (Manager) | Duyệt đơn, xem nhân viên và bảng công của đơn vị | Phòng ban mình và cấp dưới |
| Nhân viên (Employee) | Tự phục vụ: hồ sơ, đơn từ, phiếu lương | Chỉ bản thân |
| Người phỏng vấn (Interviewer) | Xem hồ sơ ứng viên được giao, chấm điểm phỏng vấn | Ứng viên được giao |

Một người dùng có thể có nhiều vai trò (ví dụ một trưởng phòng vừa là Manager vừa là Employee).

**Ma trận phân quyền (rút gọn)**

| Chức năng | Admin | HR | Manager | Employee |
| --- | --- | --- | --- | --- |
| Quản lý tài khoản, vai trò | Toàn quyền | — | — | — |
| Cơ cấu tổ chức | Xem | Toàn quyền | Xem | Xem |
| Hồ sơ nhân viên | Xem | Toàn quyền | Xem (đơn vị mình) | Xem, đề xuất sửa (bản thân) |
| Đơn nghỉ phép | — | Xem tất cả, duyệt cấp cuối | Duyệt (đơn vị mình) | Tạo, hủy (bản thân) |
| Bảng công | — | Toàn quyền | Xem, xác nhận (đơn vị mình) | Xem (bản thân) |
| Kỳ lương, phiếu lương | — | Toàn quyền | — | Xem phiếu (bản thân) |
| Tham số lương | Toàn quyền | Xem | — | — |
| Tuyển dụng | — | Toàn quyền | Tạo yêu cầu tuyển dụng | — |

**Ràng buộc và giả định**

- Một công ty (single-tenant) trong MVP; thiết kế dữ liệu có cột `company_id` để mở rộng đa công ty sau.
- Giao diện tiếng Việt mặc định, có tiếng Anh; tiền tệ VND; múi giờ Asia/Ho\_Chi\_Minh.
- Công nghệ: Spring Boot 3 (Java 21), Angular, PostgreSQL, Kafka, Redis, MinIO lưu file, Docker.
- Quy định lương, thuế, BHXH được lưu thành tham số có ngày hiệu lực, không hardcode; giá trị ban đầu cần đối chiếu văn bản pháp luật hiện hành.
- Toàn bộ code, thiết kế và dữ liệu mẫu là mới; không dùng bất kỳ tài liệu, mã nguồn hay dữ liệu nào từ hệ thống của đơn vị cũ.

## 3. Yêu cầu chức năng — Ưu tiên 1

Năm phân hệ dưới đây tạo thành chuỗi nghiệp vụ khép kín đầu tiên có thể demo: tạo tổ chức → nhập nhân viên → nhân viên xin nghỉ → quản lý duyệt.

### 3.1 Nền tảng (PLT)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| PLT-01 | Là người dùng, tôi đăng nhập bằng email/mật khẩu để vào hệ thống | JWT access token 15 phút + refresh token 7 ngày; khóa tài khoản 15 phút sau 5 lần sai |
| PLT-02 | Là người dùng, tôi đặt lại mật khẩu qua email | Link có hiệu lực 30 phút, dùng một lần |
| PLT-03 | Là Admin, tôi tạo vai trò và gán quyền theo chức năng | Quyền kiểm tra ở cả API và giao diện; ẩn menu không có quyền |
| PLT-04 | Là Admin, tôi xem nhật ký thao tác | Ghi ai, lúc nào, bảng nào, giá trị trước/sau với mọi thao tác thêm/sửa/xóa |
| PLT-05 | Là người dùng, tôi nhận thông báo trong ứng dụng và email | Thông báo khi có đơn cần duyệt, đơn được duyệt/từ chối, phiếu lương mới |
| PLT-06 | Là HR, tôi cấu hình luồng phê duyệt cho từng loại đơn | Hỗ trợ 1–3 cấp; người duyệt theo vai trò (quản lý trực tiếp, trưởng phòng, HR) hoặc chỉ định |

### 3.2 Cơ cấu tổ chức (ORG)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| ORG-01 | Là HR, tôi tạo cây phòng ban nhiều cấp | Không giới hạn số cấp; kéo thả để đổi cấp cha; không xóa phòng ban còn nhân viên |
| ORG-02 | Là HR, tôi quản lý danh mục chức danh, cấp bậc | Mỗi chức danh gắn với một khung cấp bậc lương |
| ORG-03 | Là người dùng, tôi xem sơ đồ tổ chức | Hiển thị dạng cây, có ảnh, tên, chức danh; tìm kiếm nhân viên trên sơ đồ |
| ORG-04 | Là HR, tôi chỉ định trưởng đơn vị | Trưởng đơn vị tự động là người duyệt cấp 1 của nhân viên trong đơn vị |

### 3.3 Hồ sơ nhân viên (EMP)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| EMP-01 | Là HR, tôi tạo và sửa hồ sơ nhân viên | Các nhóm thông tin: cá nhân, liên hệ, giấy tờ tùy thân, ngân hàng, người phụ thuộc, học vấn |
| EMP-02 | Là HR, tôi quản lý hợp đồng lao động | Loại hợp đồng (thử việc, xác định thời hạn, không xác định); cảnh báo trước 30 ngày khi sắp hết hạn |
| EMP-03 | Là HR, tôi ghi nhận quá trình công tác | Lịch sử điều chuyển, bổ nhiệm, tăng lương có ngày hiệu lực; không ghi đè lịch sử |
| EMP-04 | Là HR, tôi import nhân viên từ Excel | File mẫu tải về được; kiểm tra lỗi từng dòng và trả file báo lỗi; import 1.000 dòng dưới 30 giây |
| EMP-05 | Là HR, tôi tìm kiếm, lọc và xuất danh sách | Lọc theo phòng ban, chức danh, trạng thái, ngày vào làm; xuất Excel |
| EMP-06 | Là HR, tôi xử lý nghỉ việc | Ghi ngày nghỉ, lý do; khóa tài khoản vào ngày nghỉ; giữ hồ sơ để tra cứu |
| EMP-07 | Là HR, tôi đính kèm tài liệu vào hồ sơ | PDF/ảnh tối đa 10 MB mỗi file, lưu trên MinIO |

### 3.4 Nghỉ phép (LEV)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| LEV-01 | Là HR, tôi cấu hình loại nghỉ | Phép năm, nghỉ ốm, việc riêng có lương, không lương, thai sản; mỗi loại có cờ trừ quỹ phép, có lương hay không |
| LEV-02 | Là nhân viên, tôi tạo đơn nghỉ | Chọn ngày, nửa ngày sáng/chiều; hệ thống tự trừ cuối tuần và ngày lễ; báo trước nếu vượt quỹ phép |
| LEV-03 | Là quản lý, tôi duyệt hoặc từ chối đơn | Duyệt từ danh sách hoặc email; từ chối bắt buộc nhập lý do; duyệt hàng loạt |
| LEV-04 | Là nhân viên, tôi hủy đơn | Hủy tự do khi chưa duyệt; đã duyệt thì gửi yêu cầu hủy qua luồng duyệt |
| LEV-05 | Là người dùng, tôi xem lịch nghỉ của đơn vị | Lịch tháng hiển thị ai nghỉ ngày nào, theo quyền phạm vi dữ liệu |
| LEV-06 | Là HR, tôi quản lý danh mục ngày lễ theo năm | Ngày lễ không bị tính vào số ngày nghỉ |

**Quy tắc nghiệp vụ nghỉ phép**

- Quỹ phép năm mặc định 12 ngày, cộng thêm 1 ngày cho mỗi 5 năm làm việc; giá trị là tham số cấu hình được.
- Phép được cộng dồn hằng tháng (1 ngày/tháng) hoặc cấp đầu năm, tùy cấu hình.
- Phép năm cũ còn dư được dùng đến hết tháng 3 năm sau, sau đó hết hiệu lực (cấu hình được).
- Đơn chỉ trừ quỹ phép khi được duyệt cấp cuối; số ngày bị giữ tạm khi đơn đang chờ duyệt.
- Không cho phép hai đơn có ngày nghỉ chồng nhau.

&#91;embedded content: luồng duyệt đơn nghỉ · 1–3 cấp\]

Ở cả hai nhánh, nhân viên đều nhận thông báo; nếu bị từ chối, số ngày đang giữ tạm được hoàn lại ngay.

### 3.5 Cổng tự phục vụ (ESS)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| ESS-01 | Là nhân viên, tôi xem trang tổng quan của mình | Quỹ phép còn lại, đơn đang chờ, lịch nghỉ sắp tới, thông báo mới |
| ESS-02 | Là nhân viên, tôi xem và đề xuất sửa hồ sơ | Thay đổi được HR duyệt trước khi áp dụng; lưu lịch sử đề xuất |
| ESS-03 | Là nhân viên, tôi xem danh bạ công ty | Tìm theo tên, phòng ban; chỉ hiện thông tin liên hệ công việc |

## 4. Yêu cầu chức năng — Ưu tiên 2

Bốn phân hệ này là phần thể hiện chiều sâu kỹ thuật và nghiệp vụ; tính lương là điểm nhấn chính khi demo.

### 4.1 Chấm công (ATT)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| ATT-01 | Là HR, tôi cấu hình ca làm việc | Giờ vào/ra, giờ nghỉ trưa, số phút cho phép đi muộn; gán ca theo phòng ban hoặc nhân viên |
| ATT-02 | Là nhân viên, tôi check-in/check-out trên web | Ghi thời điểm, IP; tùy chọn giới hạn theo dải IP văn phòng |
| ATT-03 | Là nhân viên, tôi gửi đơn giải trình quên chấm công | Đi qua luồng duyệt như đơn nghỉ |
| ATT-04 | Là HR, tôi import dữ liệu chấm công từ file máy chấm công | Định dạng Excel/CSV theo mẫu; báo lỗi từng dòng |
| ATT-05 | Là HR, tôi tổng hợp bảng công tháng | Tự tính ngày công, đi muộn, về sớm, nghỉ có/không lương, làm thêm giờ; kết hợp dữ liệu nghỉ phép đã duyệt |
| ATT-06 | Là quản lý, tôi xác nhận bảng công của đơn vị | Sau khi HR chốt, bảng công khóa và là đầu vào của kỳ lương |

### 4.2 Tính lương (PAY)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| PAY-01 | Là HR, tôi khai báo thành phần lương | Lương cơ bản, phụ cấp (chịu thuế / không chịu thuế), thưởng, khấu trừ; mỗi thành phần có công thức |
| PAY-02 | Là HR, tôi viết công thức bằng biểu thức | Ví dụ `LUONG_CB * NGAY_CONG / CONG_CHUAN`; kiểm tra cú pháp và chạy thử trên một nhân viên |
| PAY-03 | Là Admin, tôi quản lý tham số lương có ngày hiệu lực | Mức giảm trừ bản thân và người phụ thuộc, tỷ lệ đóng BHXH/BHYT/BHTN, mức trần đóng, lương tối thiểu vùng, biểu thuế lũy tiến |
| PAY-04 | Là HR, tôi tạo và chạy kỳ lương | Tính cho toàn công ty chạy bất đồng bộ qua Kafka; hiển thị tiến độ; 2.000 nhân viên dưới 2 phút |
| PAY-05 | Là HR, tôi xem, điều chỉnh và chốt bảng lương | Điều chỉnh thủ công có lý do và lưu vết; sau khi chốt không sửa được |
| PAY-06 | Là HR, tôi phát hành phiếu lương | Sinh PDF, gửi email hàng loạt; nhân viên xem trên ESS |
| PAY-07 | Là HR, tôi xuất báo cáo | Bảng lương tổng hợp, báo cáo thuế TNCN, báo cáo BHXH, file chuyển khoản ngân hàng |

**Quy tắc tính lương**

1. Lương theo ngày công = Lương cơ bản × Ngày công thực tế ÷ Công chuẩn của tháng.
2. Tổng thu nhập = Lương theo ngày công + Phụ cấp + Làm thêm giờ + Thưởng.
3. Bảo hiểm người lao động đóng = Lương đóng bảo hiểm (giới hạn bởi mức trần) × tỷ lệ BHXH, BHYT, BHTN tại ngày hiệu lực.
4. Thu nhập tính thuế = Tổng thu nhập − Thu nhập miễn thuế − Bảo hiểm − Giảm trừ bản thân − Giảm trừ người phụ thuộc.
5. Thuế TNCN tính lũy tiến từng phần theo biểu thuế đang hiệu lực.
6. Thực lĩnh = Tổng thu nhập − Bảo hiểm − Thuế TNCN − Khấu trừ khác.

Tất cả tỷ lệ, mức giảm trừ và biểu thuế lấy từ bảng tham số theo ngày cuối kỳ lương; giá trị ban đầu phải đối chiếu văn bản pháp luật hiện hành trước khi nhập.

### 4.3 Tuyển dụng (REC)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| REC-01 | Là quản lý, tôi tạo yêu cầu tuyển dụng | Vị trí, số lượng, mô tả, ngân sách; đi qua luồng duyệt |
| REC-02 | Là HR, tôi đăng tin tuyển dụng | Trang tuyển dụng công khai liệt kê vị trí đang mở; ứng viên nộp CV không cần tài khoản |
| REC-03 | Là HR, tôi quản lý ứng viên dạng kanban | Các cột: Mới → Sàng lọc → Phỏng vấn → Đề nghị → Nhận việc / Loại; kéo thả để chuyển vòng |
| REC-04 | Là HR, tôi lên lịch phỏng vấn | Gán người phỏng vấn, gửi email mời cho ứng viên và người phỏng vấn |
| REC-05 | Là người phỏng vấn, tôi chấm điểm ứng viên | Phiếu đánh giá theo tiêu chí, điểm 1–5 và nhận xét |
| REC-06 | Là HR, tôi chuyển ứng viên trúng tuyển thành nhân viên | Tự tạo hồ sơ nhân viên từ dữ liệu ứng viên; tạo hợp đồng thử việc |

### 4.4 Dashboard HR (DSH)

| Mã | User story | Tiêu chí chấp nhận |
| --- | --- | --- |
| DSH-01 | Là HR, tôi xem chỉ số nhân sự | Tổng nhân viên, tuyển mới, nghỉ việc, tỷ lệ nghỉ việc theo tháng |
| DSH-02 | Là HR, tôi xem cơ cấu nhân sự | Theo phòng ban, giới tính, độ tuổi, thâm niên, loại hợp đồng |
| DSH-03 | Là HR, tôi xem chi phí lương | Tổng quỹ lương 12 tháng, chi phí theo phòng ban |
| DSH-04 | Là quản lý, tôi xem dashboard đơn vị mình | Cùng chỉ số, giới hạn theo phạm vi dữ liệu |

## 5. Ưu tiên 3 (khi còn thời gian)

Hai phân hệ này chỉ mô tả ở mức tổng quan; user story chi tiết sẽ viết khi bắt đầu làm.

- **Đánh giá KPI (KPI):** khai báo bộ chỉ tiêu theo chức danh, chu kỳ đánh giá quý/năm, nhân viên tự đánh giá → quản lý đánh giá → HR chốt; kết quả có thể là đầu vào thưởng trong kỳ lương.
- **Đào tạo (TRN):** danh mục khóa học, lịch lớp, đăng ký và duyệt, điểm danh, lưu chứng chỉ vào hồ sơ nhân viên.

## 6. Yêu cầu phi chức năng

| Nhóm | Yêu cầu |
| --- | --- |
| Bảo mật | Mật khẩu băm bcrypt; HTTPS; phân quyền kiểm tra ở tầng API; dữ liệu nhạy cảm (số CCCD, số tài khoản, lương) chỉ trả về cho vai trò có quyền và được che bớt khi hiển thị; không mã hóa cột để báo cáo tổng hợp trực tiếp bằng SQL; chặn OWASP Top 10 |
| Hiệu năng | API danh sách phản hồi dưới 500 ms với 10.000 nhân viên; tính lương 2.000 nhân viên dưới 2 phút |
| Truy vết | Audit log cho mọi thao tác ghi; lưu tối thiểu 12 tháng |
| Toàn vẹn dữ liệu | Không xóa cứng nhân viên, hợp đồng, kỳ lương; dữ liệu đã chốt bị khóa |
| Đa ngôn ngữ | Tiếng Việt và tiếng Anh, đổi trong giao diện |
| Giao diện | Responsive, dùng tốt trên điện thoại cho các chức năng ESS và duyệt đơn |
| Kiểm thử | Unit test cho quy tắc nghiệp vụ (nghỉ phép, lương) đạt độ bao phủ ≥ 80%; E2E cho luồng chính |
| Triển khai | Chạy toàn bộ bằng `docker compose up`; CI/CD build, test, deploy tự động |
| Tài liệu | README có sơ đồ kiến trúc; Swagger/OpenAPI cho toàn bộ API |

## 7. Danh sách màn hình chính

| Mã | Màn hình | Vai trò dùng | Ưu tiên |
| --- | --- | --- | --- |
| SC-01 | Đăng nhập, quên mật khẩu | Tất cả | 1 |
| SC-02 | Trang tổng quan nhân viên (ESS) | Employee | 1 |
| SC-03 | Cây phòng ban và sơ đồ tổ chức | HR, tất cả (xem) | 1 |
| SC-04 | Danh sách nhân viên (lọc, tìm, xuất) | HR, Manager | 1 |
| SC-05 | Chi tiết hồ sơ nhân viên (các tab: cá nhân, hợp đồng, quá trình, tài liệu) | HR | 1 |
| SC-06 | Import nhân viên từ Excel | HR | 1 |
| SC-07 | Tạo đơn nghỉ và lịch sử đơn | Employee | 1 |
| SC-08 | Hộp duyệt đơn | Manager, HR | 1 |
| SC-09 | Lịch nghỉ đơn vị | Tất cả | 1 |
| SC-10 | Cấu hình: vai trò, luồng duyệt, loại nghỉ, ngày lễ | Admin, HR | 1 |
| SC-11 | Check-in/out và bảng công cá nhân | Employee | 2 |
| SC-12 | Bảng công tháng toàn công ty | HR, Manager | 2 |
| SC-13 | Cấu hình thành phần lương và công thức | HR | 2 |
| SC-14 | Kỳ lương: chạy, xem, điều chỉnh, chốt | HR | 2 |
| SC-15 | Phiếu lương | Employee | 2 |
| SC-16 | Kanban ứng viên | HR | 2 |
| SC-17 | Trang tuyển dụng công khai | Ứng viên | 2 |
| SC-18 | Dashboard HR | HR, Manager | 2 |
| SC-19 | Nhật ký thao tác | Admin | 1 |

## 8. Yêu cầu cho bản demo, lộ trình và câu hỏi mở

Bản demo đạt yêu cầu khi người xem tự đăng nhập bằng tài khoản mẫu và đi hết một luồng trọn vẹn trong 5 phút mà không cần hướng dẫn.

**Yêu cầu cho bản demo**

- [ ] Link demo công khai, có tài khoản mẫu cho Admin, HR, Manager, Employee hiển thị ngay ở trang đăng nhập
- [ ] Dữ liệu mẫu: 1 công ty, khoảng 8 phòng ban, 200 nhân viên, 6 tháng chấm công và lương
- [ ] Dữ liệu demo tự khôi phục về trạng thái ban đầu mỗi đêm
- [ ] README: giới thiệu, ảnh chụp màn hình, sơ đồ kiến trúc, cách chạy local
- [ ] Swagger công khai; badge CI và độ bao phủ test trên GitHub
- [ ] Bài blog trên huynd.me mô tả quá trình xây dựng và các quyết định kỹ thuật

**Lộ trình dự kiến**

1. Tuần 1: SRS, ERD, design system, mockup màn hình chính.
2. Tuần 2–3: Nền tảng (đăng nhập, phân quyền, audit, luồng duyệt) và Cơ cấu tổ chức.
3. Tuần 4–5: Hồ sơ nhân viên, Nghỉ phép, ESS; deploy bản demo đầu tiên.
4. Tuần 6–7: Chấm công và Tính lương.
5. Tuần 8–9: Tuyển dụng và Dashboard.
6. Tuần 10: Hoàn thiện, kiểm thử, quét bảo mật, viết README và blog.

**Quyết định đã chốt và câu hỏi mở**

- Đã chốt: bản demo chỉ hỗ trợ một công ty (single-tenant); multi-tenant mở rộng sau, dữ liệu vẫn giữ cột `company_id`.
- Đã chốt: giao diện tiếng Việt là mặc định, tiếng Anh là ngôn ngữ phụ.
- Đã chốt: phương án triển khai — không dùng AWS; ngân sách duy trì dưới 10 USD/năm; Oracle Cloud Always Free (một máy ARM 2 OCPU / 12 GB chạy Docker Compose) + Cloudflare (DNS, HTTPS, Pages cho frontend) + subdomain của huynd.me; CI/CD bằng GitHub Actions và GitHub Container Registry.
