# SS Pack HRM

Hệ thống quản lý nhân sự (HRM) cho SS Pack, doanh nghiệp bao bì. Sản phẩm demo cho CV: phải chạy được trọn luồng, có test, có tài liệu.
Tài liệu nguồn trong `docs/`: `docs/SRS.md` (yêu cầu, mã user story PLT/ORG/EMP/LEV/ATT/PAY/REC/DSH), `docs/schema.sql` (lược đồ CSDL gốc).

## Kiến trúc

- Monorepo: `backend/` (Spring Boot 3, Java 21, Maven wrapper), `frontend/` (Angular, standalone components), `infra/` (docker-compose, Nginx), `docs/`.
- Backend là **modular monolith**. Mỗi phân hệ một package dưới `vn.sspack.hrm`: `platform` (auth, rbac, audit, approval, notification, file), `org`, `employee`, `leave`, `attendance`, `payroll`, `recruitment`, `dashboard`, và `shared`.
- Trong mỗi module: `api` (controller + DTO), `application` (service, use case), `domain` (entity, quy tắc nghiệp vụ), `infrastructure` (repository, Kafka, MinIO).
- Module KHÔNG gọi repository hay entity của module khác. Giao tiếp qua service public của module đó (`<module>.api`/`application`) hoặc qua sự kiện (Spring `ApplicationEvent` trong process; Kafka cho việc nặng như tính lương, gửi email).
- CSDL: PostgreSQL 16, schema `hrm`. Mọi thay đổi schema bằng migration Flyway trong `backend/src/main/resources/db/migration` (`V1__init.sql` lấy từ `docs/schema.sql`). Không bao giờ sửa migration đã commit; thêm file mới.
- Hạ tầng local: `docker compose -f infra/docker-compose.yml up -d` (PostgreSQL, Kafka KRaft, Redis, MinIO, Mailpit).

## Quy ước code

- Java: Spring Data JPA, MapStruct cho mapping DTO, Bean Validation cho input, `record` cho DTO. Không trả entity ra API.
- REST: tiền tố `/api/v1`, danh từ số nhiều, phân trang `page`/`size`, lỗi theo RFC 7807 (`ProblemDetail`) với thông báo tiếng Việt.
- Phân quyền: kiểm tra ở API bằng `@PreAuthorize` với mã quyền dạng `module:action` (vd `leave:approve`) VÀ lọc theo phạm vi dữ liệu của vai trò (ALL, DEPARTMENT_TREE, SELF, ASSIGNED). Không chỉ ẩn ở giao diện.
- Mọi thao tác ghi dữ liệu nghiệp vụ ghi audit log. Bảng có cột `version` dùng `@Version`.
- Tiền VND dùng `BigDecimal` scale 0, không dùng `double`. Ngày dùng `LocalDate`, thời điểm dùng `OffsetDateTime`, múi giờ Asia/Ho_Chi_Minh.
- Tham số lương, thuế, bảo hiểm đọc từ bảng `payroll_parameters`/`tax_brackets` theo ngày hiệu lực. Không hardcode số liệu pháp lý trong code.
- Frontend: Angular standalone, signals, reactive forms, lazy-load mỗi phân hệ một route. Màu, chữ, khoảng cách lấy từ CSS variables của design system SS Pack (`frontend/src/styles/tokens.css`). Giao diện tiếng Việt; chuỗi để trong file i18n.
- Định dạng hiển thị: ngày `dd/MM/yyyy`, tiền `18.450.000 ₫`, số thập phân dùng dấu phẩy.

## Kiểm thử

- Mỗi quy tắc nghiệp vụ (tính ngày nghỉ, quỹ phép, công thức lương, thuế lũy tiến) có unit test. Mục tiêu bao phủ ≥ 80% cho `domain` và `application`.
- Test tích hợp dùng Testcontainers (PostgreSQL thật), không dùng H2.
- Trước khi báo xong một việc: chạy `./mvnw verify` trong `backend/` và `npm test -- --watch=false` + `npm run build` trong `frontend/`.

## Cách làm việc

- Mỗi tính năng một nhánh `feat/<mã-user-story>-<mô-tả-ngắn>`, commit theo Conventional Commits, mô tả PR liệt kê user story và tiêu chí chấp nhận đã đạt.
- Làm từng user story một; với việc lớn, trình bày kế hoạch trước khi sửa code.
- Không commit bí mật. Cấu hình local trong `.env` (đã gitignore); mẫu ở `.env.example`.
- Không dùng code, tài liệu hay dữ liệu từ bất kỳ hệ thống của tổ chức khác. Dữ liệu mẫu là hư cấu.

## Lệnh hay dùng

- Hạ tầng: `docker compose -f infra/docker-compose.yml up -d` / `down`
- Backend: `cd backend && ./mvnw spring-boot:run` (cổng 8080, Swagger tại `/swagger-ui.html`, debug cổng 5005 khi chạy profile `dev`)
- Frontend: `cd frontend && npm start` (cổng 4200, proxy `/api` sang 8080)
- Email local: Mailpit tại http://localhost:8025
- MinIO console: http://localhost:9001
