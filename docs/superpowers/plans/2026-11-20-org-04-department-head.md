# ORG-04 — Chỉ định trưởng đơn vị: kế hoạch triển khai

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR chỉ định trưởng cho từng phòng ban. Trưởng đơn vị (đi ngược lên cây khi phòng chưa có trưởng) là người duyệt cấp 1. `DEPARTMENT_TREE` tính theo phòng ban mình làm trưởng. Cây phòng ban và sơ đồ tổ chức hiển thị trưởng đơn vị.

**Architecture:** Cột `departments.head_employee_id` đã có từ V1. Module `org` giữ quy tắc chọn người duyệt: hàm thuần `ApproverChain` trong `org.domain`, bọc bởi `DepartmentHeadResolver` trong `org.application`. Thông tin nhân viên lấy qua service public mới `employee.application.EmployeeRefService`; `org` không đọc bảng `employees`. Cảnh báo về vai trò dùng `DataScopeService` sẵn có.

**Tech Stack:** Spring Boot 3.5, JPA, Flyway, Testcontainers, JUnit 5; Angular 21 standalone + signals, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-20-org-04-department-head-design.md`

## Global Constraints

- Nhánh `feat/ORG-04-department-head`, Conventional Commits, mỗi commit kết thúc bằng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Không sửa migration đã commit; thêm `V8__department_head.sql`.
- Module `org` không dùng repository hay entity của `employee`/`platform`; chỉ gọi service public.
- Mọi thao tác ghi có audit (`AuditEntity.DEPARTMENTS`, `Action.UPDATE`) với snapshot có khóa `headEmployeeId`. Có kiểm tra `@Version`.
- Lỗi dùng `ApiException` với mã ổn định và thông báo tiếng Việt. Chuỗi giao diện đặt trong `frontend/public/i18n/vi.json` và `en.json`.
- Trạng thái "đang làm việc" = `EmployeeStatus.WORKING` (PROBATION, ACTIVE, ON_LEAVE).
- Mã cảnh báo cố định: `NO_ACCOUNT`, `NO_MANAGER_SCOPE`. Phạm vi coi là "có quyền quản lý": `DEPARTMENT_TREE` hoặc `ALL`.
- Hoàn tất khi xanh: `cd backend && ./mvnw verify`, `cd frontend && npm test -- --watch=false && npm run build`.

## Review Focus

1. **Trưởng kiêm nhiệm ở phòng khác:** trên sơ đồ, người này vẫn đứng đầu phòng mình làm trưởng và không bị nhân đôi trong phòng gốc ở vị trí đầu → test ở Task 6 (`headFromAnotherDepartmentShownFirst`).
2. **Người gửi đơn là trưởng của nhiều cấp liên tiếp** (vừa trưởng tổ vừa trưởng xưởng): phải leo qua cả hai lên cấp trên nữa → test ở Task 2 (`skipsRequesterHeadingSeveralLevels`).
3. **Chỉ định trưởng là nhân viên của công ty khác** (id tồn tại nhưng khác `company_id`): trả 422 như không tồn tại, không lộ dữ liệu → test ở Task 4 (`rejectsEmployeeOfAnotherCompany`).
4. **MANAGER làm trưởng một phòng đã ngừng hoạt động:** vẫn thấy nhánh đó, vì chỉ được bỏ chỉ định chứ không tự mất → test ở Task 5 (`headOfInactiveDepartmentStillSeesIt`).
5. **Gửi lại cùng trưởng** (PUT với `employeeId` không đổi): không tăng version, không ghi audit → test ở Task 4 (`sameHeadIsNoOp`).

---

### Task 1: Dữ liệu và quy tắc của entity

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__department_head.sql`
- Modify: `backend/src/main/java/vn/sspack/hrm/org/domain/Department.java`
- Modify: `backend/src/main/java/vn/sspack/hrm/org/domain/DepartmentRuleException.java`
- Test: `backend/src/test/java/vn/sspack/hrm/org/domain/DepartmentTest.java`

**Interfaces:**
- Produces: `Department.getHeadEmployeeId(): Long`; `Department.assignHead(Long employeeId, OffsetDateTime now): boolean` (true khi có thay đổi); `DepartmentRuleException.headOnInactiveDepartment()`, `DepartmentRuleException.headNotWorking()`.

- [ ] **Step 1: Viết test thất bại trong `DepartmentTest`**

```java
@Test void assignHeadSetsAndClears() {
    Department d = activeDepartment();
    assertThat(d.assignHead(7L, NOW)).isTrue();
    assertThat(d.getHeadEmployeeId()).isEqualTo(7L);
    assertThat(d.assignHead(7L, NOW)).isFalse();
    assertThat(d.assignHead(null, NOW)).isTrue();
    assertThat(d.getHeadEmployeeId()).isNull();
}
@Test void inactiveDepartmentOnlyAllowsClearing() {
    Department d = inactiveDepartmentWithHead(7L);
    assertThatThrownBy(() -> d.assignHead(8L, NOW)).isInstanceOf(DepartmentRuleException.class)
            .extracting("code").isEqualTo("DEPARTMENT_HEAD_INACTIVE");
    assertThat(d.assignHead(null, NOW)).isTrue();
}
```

- [ ] **Step 2: Chạy và xác nhận thất bại**: `cd backend && ./mvnw -q test -Dtest=DepartmentTest` → lỗi biên dịch, `assignHead` chưa tồn tại.

- [ ] **Step 3: Cài đặt**
  - V8: `CREATE INDEX idx_departments_head ON hrm.departments (company_id, head_employee_id) WHERE head_employee_id IS NOT NULL;`
  - `Department`: thêm `@Column(name = "head_employee_id") private Long headEmployeeId;`. `assignHead` ném `headOnInactiveDepartment()` khi `!active && employeeId != null`, cập nhật `updatedAt` khi đổi.
  - Rule exception: `DEPARTMENT_HEAD_INACTIVE` (409) "Phòng ban đã ngừng hoạt động, không thể chỉ định trưởng đơn vị."; `DEPARTMENT_HEAD_NOT_WORKING` (422) "Nhân viên không tồn tại hoặc đã nghỉ việc." (dùng ở Task 4, hàm `public static`).

- [ ] **Step 4: Chạy test**: `./mvnw -q test -Dtest=DepartmentTest` → PASS.

- [ ] **Step 5: Commit** `feat(org): lưu trưởng đơn vị trên phòng ban (ORG-04)`

### Task 2: Quy tắc chọn người duyệt cấp 1

**Files:**
- Create: `backend/src/main/java/vn/sspack/hrm/org/domain/ApproverChain.java`
- Create: `backend/src/main/java/vn/sspack/hrm/employee/application/EmployeeRefService.java`
- Create: `backend/src/main/java/vn/sspack/hrm/org/application/DepartmentHeadResolver.java`
- Modify: `backend/src/main/java/vn/sspack/hrm/employee/infrastructure/persistence/EmployeeRepository.java` (thêm `List<Employee> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids)`)
- Test: `backend/src/test/java/vn/sspack/hrm/org/domain/ApproverChainTest.java`

**Interfaces:**
- Produces:
  - `ApproverChain.pick(List<Long> headsFromSelfToRoot, long requesterEmployeeId, Set<Long> workingEmployeeIds): Optional<Long>`. Danh sách theo thứ tự phòng ban của người gửi → gốc, phần tử `null` = phòng chưa có trưởng.
  - `Department.ancestorIdsFromSelf(): List<Long>`, tách `path` `/1/4/12/` thành `[12, 4, 1]`.
  - `EmployeeRefService.EmployeeRef(Long id, String code, String fullName, Long departmentId, Long userId, boolean working)`; `Map<Long, EmployeeRef> refs(long companyId, Collection<Long> ids)` (bỏ qua id không thuộc công ty); `List<EmployeeRef> working(long companyId)`.
  - `DepartmentHeadResolver.firstLevelApprover(long companyId, long departmentId, long requesterEmployeeId): Optional<Long>` (mã nhân viên); `Map<Long, Optional<Long>> effectiveApprovers(long companyId, List<Department> all)` (người duyệt cho nhân viên thường của từng phòng, requester = -1, dùng cho cây ở Task 4).

- [ ] **Step 1: Viết test thất bại `ApproverChainTest`**

```java
@Test void ownHead()                       { assertThat(pick(list(5L, 1L), 9, Set.of(5L, 1L))).contains(5L); }
@Test void climbsWhenNoHead()              { assertThat(pick(list(null, 1L), 9, Set.of(1L))).contains(1L); }
@Test void skipsRequesterAsHead()          { assertThat(pick(list(9L, 1L), 9, Set.of(9L, 1L))).contains(1L); }
@Test void skipsRequesterHeadingSeveralLevels() { assertThat(pick(list(9L, 9L, 1L), 9, Set.of(9L, 1L))).contains(1L); }
@Test void skipsHeadNotWorking()           { assertThat(pick(list(5L, 1L), 9, Set.of(1L))).contains(1L); }
@Test void emptyWhenNobody()               { assertThat(pick(list(null, 9L), 9, Set.of(9L))).isEmpty(); }
```
(`list(...)` = `Arrays.asList`, để chứa được `null`.) Thêm vào `DepartmentTest`: `ancestorIdsFromSelf` của path `/1/4/12/` là `[12, 4, 1]`.

- [ ] **Step 2: Chạy và xác nhận thất bại**: `./mvnw -q test -Dtest='ApproverChainTest,DepartmentTest'` → lỗi biên dịch.

- [ ] **Step 3: Cài đặt** `ApproverChain` (lớp `final`, hàm `static`), `ancestorIdsFromSelf`, `EmployeeRefService` (`@Transactional(readOnly = true)`, chỉ dùng `EmployeeRepository`), `DepartmentHeadResolver`. Resolver đọc chuỗi phòng ban bằng `findByCompanyIdAndIdIn`, đọc trạng thái các trưởng bằng một lần `refs(...)`, rồi gọi `ApproverChain.pick`. Phòng ban không thuộc công ty → `Optional.empty()`.

- [ ] **Step 4: Chạy test** → PASS.

- [ ] **Step 5: Commit** `feat(org): tìm người duyệt cấp 1 theo trưởng đơn vị, leo cây khi thiếu (ORG-04)`

### Task 3: Dữ liệu demo

**Files:**
- Modify: `backend/src/main/resources/db/dev/R__demo_workforce.sql` (thêm ở cuối)

- [ ] **Step 1:** Thêm câu `UPDATE departments d SET head_employee_id = e.id FROM (VALUES ('BGD','SP0001'),('NM','SP0002'),('NS','SP0003'),('KT','SP0005'),('KD','SP0007'),('XI','SP0010'),('XI-OFFSET','SP0012'),('XCD','SP0016'),('QC','SP0019'),('KHO','SP0022')) AS h(dept, emp) JOIN employees e ON e.employee_code = h.emp ...`, chỉ cập nhật khi `d.head_employee_id IS NULL` và cùng `company_id`. KD-MB, KD-MN, XI-FLEXO để trống.
- [ ] **Step 2:** Khởi động backend profile dev (preview_start "backend"), chạy `psql` trên cổng 5433: `SELECT code, head_employee_id FROM hrm.departments ORDER BY path;` → 10 phòng có trưởng. Khởi động lần hai không báo lỗi Flyway.
- [ ] **Step 3: Commit** `chore(dev): gán trưởng đơn vị cho dữ liệu demo (ORG-04)`

### Task 4: API chỉ định trưởng và cây phòng ban có thông tin trưởng

**Files:**
- Modify: `backend/src/main/java/vn/sspack/hrm/org/application/DepartmentService.java`
- Modify: `backend/src/main/java/vn/sspack/hrm/org/api/DepartmentController.java`, `DepartmentDtos.java`, `DepartmentApiMapper.java`
- Test: `backend/src/test/java/vn/sspack/hrm/org/api/DepartmentIT.java`

**Interfaces:**
- Consumes: Task 1 (`assignHead`, rule exceptions), Task 2 (`EmployeeRefService`, `DepartmentHeadResolver.effectiveApprovers`), `DataScopeService.widestScope(companyId, userId)`.
- Produces:
  - `DepartmentService.HeadCommand(Long employeeId, int version)`; `DepartmentView assignHead(RequestActor actor, long id, HeadCommand command)`.
  - `DepartmentView` thêm `PersonRef head` và `PersonRef effectiveApprover`. `PersonRef(Long employeeId, String code, String fullName, List<String> warnings)`; `warnings` chỉ có giá trị cho `head`, với `effectiveApprover` thì là danh sách rỗng.
  - REST: `PUT /api/v1/departments/{id}/head`, body `HeadRequest(Long employeeId, @NotNull Integer version)`, `@PreAuthorize("hasAuthority('org:manage')")`. `DepartmentResponse` thêm `head` và `effectiveApprover` (có thể `null`).

- [ ] **Step 1: Viết test thất bại trong `DepartmentIT`**
  - `hrAssignsAndClearsHead`: PUT với nhân viên ACTIVE → 200, `head.employeeId` đúng, `version` tăng; có 1 dòng audit UPDATE trên `departments`, `before.headEmployeeId = null`. PUT `employeeId: null` → `head` null.
  - `employeeCannotAssignHead`: tài khoản EMPLOYEE → 403.
  - `rejectsResignedEmployee`: nhân viên RESIGNED → 422 `DEPARTMENT_HEAD_NOT_WORKING`.
  - `rejectsEmployeeOfAnotherCompany` → 422 cùng mã.
  - `rejectsOnInactiveDepartment` → 409 `DEPARTMENT_HEAD_INACTIVE`.
  - `staleVersionConflicts` → 409 theo mã xung đột phiên bản đang dùng.
  - `sameHeadIsNoOp`: PUT hai lần cùng người → `version` không đổi lần hai, audit vẫn chỉ 1 dòng.
  - `listShowsWarningsAndEffectiveApprover`: trưởng không có tài khoản → `warnings = ["NO_ACCOUNT"]`; trưởng có tài khoản vai trò EMPLOYEE → `["NO_MANAGER_SCOPE"]`; phòng con chưa có trưởng → `effectiveApprover` là trưởng phòng cha.

- [ ] **Step 2: Chạy và xác nhận thất bại**: `./mvnw -q verify -Dit.test=DepartmentIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` → FAIL (404 hoặc trường chưa có).

- [ ] **Step 3: Cài đặt**
  - `assignHead`: dùng `findForUpdate`, `checkVersion`. Nếu `employeeId != null` thì kiểm tra qua `refs` (phải tồn tại, `working`), không thì ném `headNotWorking()`. `snapshot` thêm `headEmployeeId`. Khi `assignHead` trả false thì không flush và không ghi audit.
  - `list`/`view`: gom mọi `headEmployeeId` và người duyệt hiệu lực vào một lần `refs(...)`. Gọi `widestScope` một lần cho mỗi `userId` khác nhau của các trưởng (số trưởng nhỏ, chấp nhận).

- [ ] **Step 4: Chạy test** → PASS. Chạy lại toàn bộ `DepartmentIT` để chắc không vỡ test cũ.

- [ ] **Step 5: Commit** `feat(org): API chỉ định trưởng đơn vị, cảnh báo thiếu tài khoản/vai trò (ORG-04)`

### Task 5: Phạm vi `DEPARTMENT_TREE` theo phòng ban mình làm trưởng

**Files:**
- Modify: `backend/src/main/java/vn/sspack/hrm/org/application/OrgDirectory.java`
- Modify: `backend/src/main/java/vn/sspack/hrm/org/infrastructure/persistence/DepartmentRepository.java` (thêm `List<Department> findByCompanyIdAndHeadEmployeeId(Long companyId, Long headEmployeeId)`)
- Modify: `backend/src/main/java/vn/sspack/hrm/employee/application/EmployeeAccess.java` (nhánh `DEPARTMENT_TREE` và Javadoc)
- Test: `backend/src/test/java/vn/sspack/hrm/employee/api/EmployeeIT.java`

**Interfaces:**
- Produces: `OrgDirectory.headedSubtreeIds(long companyId, long employeeId): Set<Long>`, gồm hợp các nhánh của mọi phòng ban (kể cả đã ngừng hoạt động) mà người này làm trưởng.

- [ ] **Step 1: Viết test thất bại trong `EmployeeIT`** (sửa các test `DEPARTMENT_TREE` hiện có cho đúng quy tắc mới)
  - `managerSeesSubtreesTheyHead`: MANAGER làm trưởng phòng A (có con A1) thấy nhân viên của A và A1, không thấy phòng B.
  - `managerHeadingNothingSeesOnlySelf`: MANAGER thuộc phòng A nhưng không làm trưởng → danh sách chỉ có chính mình.
  - `managerHeadingTwoBranchesSeesBoth`.
  - `headOfInactiveDepartmentStillSeesIt`.
- [ ] **Step 2: Chạy và xác nhận thất bại** (`-Dit.test=EmployeeIT`).
- [ ] **Step 3: Cài đặt**: `DEPARTMENT_TREE -> self.map(e -> new Visibility(false, org.headedSubtreeIds(companyId, e.getId()), selfId)).orElse(Visibility.NONE)`. Bỏ ghi chú "tạm thời cho đến ORG-04".
- [ ] **Step 4: Chạy `EmployeeIT` và `OrgChartIT`** → PASS (sơ đồ dùng `profileVisible` nên có thể cần sửa kỳ vọng).
- [ ] **Step 5: Commit** `feat(employee): phạm vi DEPARTMENT_TREE theo phòng ban mình làm trưởng (ORG-04)`

### Task 6: Sơ đồ tổ chức đưa trưởng đơn vị lên đầu

**Files:**
- Modify: `backend/src/main/java/vn/sspack/hrm/org/application/OrgChartService.java`
- Modify: `backend/src/main/java/vn/sspack/hrm/org/api/OrgChartController.java` (DTO, nếu tách riêng)
- Test: `backend/src/test/java/vn/sspack/hrm/org/api/OrgChartIT.java`

**Interfaces:**
- Produces: `ChartDepartment` thêm `Long headEmployeeId`; `Member` thêm `boolean head`. Trong mỗi phòng, trưởng (nếu đang làm việc) là `members[0]`, có `head = true`. Trưởng kiêm nhiệm được chèn vào đầu phòng mình làm trưởng và vẫn xuất hiện ở phòng gốc với `head = false`.

- [ ] **Step 1: Viết test thất bại**: `headListedFirstWithFlag`; `headFromAnotherDepartmentShownFirst` (phòng X có trưởng thuộc phòng Y → `X.members[0].id` = trưởng, `head = true`; trong Y người đó có `head = false`, chỉ xuất hiện một lần); `resignedHeadNotShown`.
- [ ] **Step 2: Chạy và xác nhận thất bại** (`-Dit.test=OrgChartIT`).
- [ ] **Step 3: Cài đặt**: tra thẻ trưởng từ map id → `DirectoryCard` của `workingEmployees`. Sắp xếp phần còn lại như cũ.
- [ ] **Step 4: Chạy test** → PASS.
- [ ] **Step 5: Commit** `feat(org): sơ đồ tổ chức đưa trưởng đơn vị lên đầu (ORG-04)`

### Task 7: Giao diện chỉ định trưởng trên cây phòng ban

**Files:**
- Modify: `frontend/src/app/features/org/departments/department.models.ts` (thêm `PersonRef`, `head`, `effectiveApprover`, `type HeadWarning = 'NO_ACCOUNT' | 'NO_MANAGER_SCOPE'`)
- Modify: `frontend/src/app/features/org/departments/departments.api.ts` (`setHead(id: number, employeeId: number | null, version: number): Observable<Department>` → `PUT /api/v1/departments/{id}/head`)
- Modify: `departments-page.ts`, `departments-page.html`, `departments-page.scss`
- Modify: `frontend/public/i18n/vi.json`, `en.json` (khóa `org.departments.head.*`, `org.errors.DEPARTMENT_HEAD_INACTIVE`, `org.errors.DEPARTMENT_HEAD_NOT_WORKING`)
- Test: `frontend/src/app/features/org/departments/departments-page.spec.ts`

**Interfaces:**
- Consumes: Task 4 REST. Danh sách nhân viên cho ô chọn lấy từ API nhân viên hiện có, lọc trạng thái đang làm việc (`employees.api.ts`, `size` đủ lớn cho một công ty demo). Tìm kiếm dùng `normalizeSearch` (`shared/format/search.ts`).

- [ ] **Step 1: Viết test thất bại trong spec**
  - Hàng phòng ban hiện "Trưởng: Trần Minh Anh". Phòng chưa có trưởng hiện "Trưởng: — (duyệt bởi …)" theo `effectiveApprover`.
  - Hiện biểu tượng cảnh báo có `title` lấy từ i18n với `NO_ACCOUNT` và `NO_MANAGER_SCOPE`.
  - Hộp thoại "Chỉ định trưởng": gõ "minh anh" (không dấu) lọc ra đúng người, bấm Lưu gọi `setHead(id, employeeId, version)`. "Bỏ chỉ định" gọi `setHead(id, null, version)`. Lỗi 422 hiển thị thông báo từ `org.errors.DEPARTMENT_HEAD_NOT_WORKING`.
  - Phòng ngừng hoạt động: nút chỉ định bị ẩn, chỉ còn "Bỏ chỉ định" khi đang có trưởng.
- [ ] **Step 2: Chạy và xác nhận thất bại**: `cd frontend && npm test -- --watch=false --include='**/departments-page.spec.ts'`.
- [ ] **Step 3: Cài đặt** theo mẫu `<dialog>` sẵn có (`editDialog`, `moveDialog`). Signals cho danh sách và chuỗi tìm kiếm; màu cảnh báo lấy từ token trong `tokens.css`.
- [ ] **Step 4: Chạy spec** → PASS.
- [ ] **Step 5: Commit** `feat(org): giao diện chỉ định trưởng đơn vị trên cây phòng ban (ORG-04)`

### Task 8: Giao diện sơ đồ hiển thị trưởng đơn vị

**Files:**
- Modify: `frontend/src/app/features/org/chart/org-chart.models.ts` (`ChartMember.head: boolean`, `ChartDepartment.headEmployeeId: number | null`)
- Modify: `org-chart-page.html`, `org-chart-page.scss`, i18n (`org.chart.headBadge`: "Trưởng đơn vị" / "Head")
- Test: `org-chart-page.spec.ts`

- [ ] **Step 1: Viết test thất bại**: thẻ có `head = true` hiện nhãn "Trưởng đơn vị" và đứng đầu phòng; thẻ khác không có nhãn.
- [ ] **Step 2: Chạy và xác nhận thất bại** (`--include='**/org-chart-page.spec.ts'`).
- [ ] **Step 3: Cài đặt**: giữ nguyên thứ tự server trả (không sắp lại ở client).
- [ ] **Step 4: Chạy spec** → PASS.
- [ ] **Step 5: Commit** `feat(org): nhãn trưởng đơn vị trên sơ đồ tổ chức (ORG-04)`

### Task 9: Kiểm tra toàn bộ và thử trên app

- [ ] **Step 1:** `cd backend && ./mvnw verify` → BUILD SUCCESS. Nếu PR #10 đã merge vào nhánh thì thêm dòng "All coverage checks have been met".
- [ ] **Step 2:** `cd frontend && npm test -- --watch=false && npm run build` → xanh.
- [ ] **Step 3:** Chạy app (preview_start "backend", "frontend"), đăng nhập HR `thuha`: chỉ định trưởng cho XI-FLEXO, thấy cảnh báo và người duyệt hiệu lực. Đăng nhập `minhanh`: danh sách nhân viên chỉ gồm Xưởng In và các tổ. Sơ đồ có nhãn trưởng. Chụp màn hình làm bằng chứng.
- [ ] **Step 4:** Cập nhật memory tiến độ; push, mở PR liệt kê ORG-04 và tiêu chí chấp nhận. Ghi rõ "người duyệt cấp 1" mới có ở mức service, thấy được trọn vẹn khi làm PLT-06/LEV-03.
