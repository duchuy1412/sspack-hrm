package vn.sspack.hrm.employee.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import vn.sspack.hrm.support.IntegrationTest;
import vn.sspack.hrm.support.MutableClock;
import vn.sspack.hrm.support.TestUsers;

/**
 * EMP-01 qua HTTP thật trên PostgreSQL 16: tạo, sửa hồ sơ; tìm không dấu; lọc theo phạm vi dữ liệu (ALL,
 * DEPARTMENT_TREE, SELF); ẩn thông tin cá nhân; phiên bản; audit.
 */
@IntegrationTest
class EmployeeIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers testUsers;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    String hrToken;
    /** Tiền tố riêng cho mỗi test: mã nhân viên, phòng ban, email không trùng giữa các test. */
    String prefix;
    long position;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(Instant.now());
        hrToken = login(testUsers.create("HR").email());
        prefix = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        position = createPosition(prefix + "-P");
    }

    // ================================================================= quyền

    @Test
    @DisplayName("Thiếu employee:manage: tạo, sửa, lấy mã gợi ý đều 403 trước khi kiểm dữ liệu")
    void writeNeedsManage() throws Exception {
        String managerToken = login(testUsers.create("MANAGER", "EMPLOYEE").email());
        long department = createDepartment(prefix + "-D", null);
        long employee = createEmployee(prefix + "-01", "Nhân viên thử", department, null);

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/employees/next-code"),
                post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/employees/" + employee).contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // ================================================================= tạo, sửa

    @Test
    @DisplayName("HR tạo hồ sơ: chuẩn hóa dữ liệu, tìm theo tên không dấu, mã gợi ý tăng dần")
    void createAndSearch() throws Exception {
        long department = createDepartment(prefix + "-D", null);
        String body = authed(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "fullName": "  Nguyễn   Thị Hằng ", "gender": "FEMALE", "dateOfBirth": "1996-08-17",
                 "workEmail": "%s", "personalEmail": "hang@example.com", "phone": "0903 110 006",
                 "address": "Thủ Dầu Một, Bình Dương", "departmentId": %d, "positionId": %d,
                 "hireDate": "2026-10-14", "status": "PROBATION"}
                """.formatted(prefix.toLowerCase() + "-01", prefix + ".Hang@SSPack.vn", department, position)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(prefix + "-01"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Thị Hằng"))
                .andExpect(jsonPath("$.workEmail").value(prefix.toLowerCase() + ".hang@sspack.vn"))
                .andExpect(jsonPath("$.phone").value("0903110006"))
                .andExpect(jsonPath("$.departmentName").value("Phòng " + prefix + "-D"))
                .andExpect(jsonPath("$.positionName").value("Chức danh " + prefix + "-P"))
                .andExpect(jsonPath("$.personalVisible").value(true))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.accountLinked").value(false))
                .andExpect(jsonPath("$.allowedStatuses", Matchers.containsInAnyOrder("PROBATION", "ACTIVE",
                        "ON_LEAVE")))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        authed(get("/api/v1/employees").param("q", "nguyen thi hang " ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].positionName")
                        .value("Chức danh " + prefix + "-P"));
        authed(get("/api/v1/employees").param("q", prefix.toLowerCase() + "-01"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        authed(get("/api/v1/employees").param("q", prefix + ".hang@"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Mã gợi ý: lớn hơn mọi mã dạng SP<số> đang có
        createEmployee("SP" + (900000 + Math.abs(prefix.hashCode() % 90000)), "Nhân viên mã số", department, null);
        String next = JsonPath.read(authed(get("/api/v1/employees/next-code")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.code");
        assertThat(next).startsWith("SP");
        assertThat(Long.parseLong(next.substring(2))).isGreaterThan(900000);
    }

    @Test
    @DisplayName("Kiểm tra dữ liệu: trường bắt buộc 400, mã trùng và email trùng 409, trạng thái khi tạo 400")
    void validation() throws Exception {
        long department = createDepartment(prefix + "-D", null);
        createEmployee(prefix + "-01", "Người thứ nhất", department, prefix + ".one@sspack.vn");

        authed(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "A B", "fullName": "", "workEmail": "khong-phai-email", "phone": "abc"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.workEmail").exists())
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.departmentId").exists())
                .andExpect(jsonPath("$.errors.positionId").exists())
                .andExpect(jsonPath("$.errors.hireDate").exists())
                .andExpect(jsonPath("$.errors.status").exists());

        authed(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content(employeeJson(prefix + "-01", "Trùng mã", department, null, "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CODE_EXISTS"));
        authed(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content(employeeJson(prefix + "-02", "Trùng email", department, prefix.toUpperCase()
                        + ".ONE@sspack.vn", "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_WORK_EMAIL_EXISTS"));
        authed(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content(employeeJson(prefix + "-03", "Tạo đã nghỉ", department, null, "RESIGNED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_STATUS_NOT_ALLOWED"));
        authed(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content(employeeJson(prefix + "-04", "Phòng ban lạ", 999_999_999L, null, "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Phòng ban ngừng hoạt động: không gán người mới, người đang ở đó vẫn sửa được thông tin khác")
    void inactiveDepartment() throws Exception {
        long department = createDepartment(prefix + "-D", null);
        long other = createDepartment(prefix + "-E", null);
        long employee = createEmployee(prefix + "-01", "Người ở lại", department, null);
        // Cho nhân viên nghỉ qua SQL (EMP-06 chưa có) để ngừng hoạt động được phòng ban
        jdbc.sql("UPDATE hrm.employees SET status = 'RESIGNED' WHERE id = :id").param("id", employee).update();
        authed(put("/api/v1/departments/" + department + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false, \"version\": %d}".formatted(version("departments", department))))
                .andExpect(status().isOk());

        authed(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content(employeeJson(prefix + "-02", "Người mới", department, null, "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_DEPARTMENT_INACTIVE"));

        authed(put("/api/v1/employees/" + employee).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(prefix + "-01", "Người ở lại (đã nghỉ)", department, "RESIGNED",
                        version("employees", employee))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Người ở lại (đã nghỉ)"))
                .andExpect(jsonPath("$.departmentActive").value(false))
                .andExpect(jsonPath("$.allowedStatuses", Matchers.contains("RESIGNED")));
        authed(put("/api/v1/employees/" + employee).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(prefix + "-01", "Người ở lại", other, "ACTIVE", version("employees", employee))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_STATUS_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Sửa hồ sơ: đổi phòng ban, trạng thái; phiên bản cũ 409; ghi audit trước/sau")
    void updateVersionAndAudit() throws Exception {
        long department = createDepartment(prefix + "-D", null);
        long other = createDepartment(prefix + "-E", null);
        long employee = createEmployee(prefix + "-01", "Người được sửa", department, null);
        int version = version("employees", employee);

        authed(put("/api/v1/employees/" + employee).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(prefix + "-01", "Người được sửa", other, "ON_LEAVE", version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(other))
                .andExpect(jsonPath("$.status").value("ON_LEAVE"))
                .andExpect(jsonPath("$.version").value(version + 1));
        authed(put("/api/v1/employees/" + employee).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(prefix + "-01", "Sửa trên bản cũ", department, "ACTIVE", version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        List<String> actions = jdbc.sql("""
                        SELECT action FROM hrm.audit_logs
                        WHERE entity_type = 'employees' AND entity_id = :id ORDER BY id
                        """)
                .param("id", employee).query(String.class).list();
        assertThat(actions).containsExactly("CREATE", "UPDATE");
        String change = jdbc.sql("""
                        SELECT (old_value ->> 'status') || '>' || (new_value ->> 'status')
                        FROM hrm.audit_logs
                        WHERE entity_type = 'employees' AND entity_id = :id AND action = 'UPDATE'
                        """)
                .param("id", employee).query(String.class).single();
        assertThat(change).isEqualTo("ACTIVE>ON_LEAVE");
    }

    // ================================================================= phạm vi dữ liệu

    @Test
    @DisplayName("Phạm vi: HR thấy cả công ty; quản lý thấy cây phòng ban của mình; nhân viên chỉ thấy mình")
    void dataScope() throws Exception {
        long root = createDepartment(prefix + "-R", null);
        long child = createDepartment(prefix + "-C", root);
        long outside = createDepartment(prefix + "-O", null);

        TestUsers.Created managerUser = testUsers.create("MANAGER", "EMPLOYEE");
        TestUsers.Created staffUser = testUsers.create("EMPLOYEE");
        TestUsers.Created orphanUser = testUsers.create("EMPLOYEE");
        long manager = createEmployee(prefix + "-01", "Quản lý", root, null);
        long staff = createEmployee(prefix + "-02", "Nhân viên tổ con", child, null);
        long colleague = createEmployee(prefix + "-03", "Đồng nghiệp", child, null);
        long stranger = createEmployee(prefix + "-04", "Người phòng khác", outside, null);
        link(manager, managerUser.id());
        link(staff, staffUser.id());
        String managerToken = login(managerUser.email());
        String staffToken = login(staffUser.email());
        String orphanToken = login(orphanUser.email());

        // HR: cả công ty; lọc phòng ban gồm cả phòng ban con
        assertThat(ids(hrToken, "")).containsExactlyInAnyOrder(manager, staff, colleague, stranger);
        assertThat(ids(hrToken, "&departmentId=" + root)).containsExactlyInAnyOrder(manager, staff, colleague);

        // Quản lý: phòng ban của mình và cấp dưới; người ngoài 404; không thấy thông tin cá nhân của người khác
        assertThat(ids(managerToken, "")).containsExactlyInAnyOrder(manager, staff, colleague);
        as(managerToken, get("/api/v1/employees/" + stranger)).andExpect(status().isNotFound());
        as(managerToken, get("/api/v1/employees/" + colleague))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalVisible").value(false))
                .andExpect(jsonPath("$.dateOfBirth").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.phone").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.editable").value(false));
        as(managerToken, get("/api/v1/employees/" + manager))
                .andExpect(jsonPath("$.personalVisible").value(true))
                .andExpect(jsonPath("$.phone").value("0903000000"))
                .andExpect(jsonPath("$.accountLinked").value(true));

        // Nhân viên: chỉ chính mình
        assertThat(ids(staffToken, "")).containsExactly(staff);
        as(staffToken, get("/api/v1/employees/" + colleague)).andExpect(status().isNotFound());
        as(staffToken, get("/api/v1/employees/" + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalVisible").value(true))
                .andExpect(jsonPath("$.editable").value(false));

        // Tài khoản không gắn hồ sơ, phạm vi SELF: không thấy ai
        assertThat(ids(orphanToken, "")).isEmpty();
    }

    // ================================================================= tiện ích

    private List<Long> ids(String token, String extraQuery) throws Exception {
        String body = as(token, get("/api/v1/employees?size=100&q=" + prefix + extraQuery))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private void link(long employeeId, long userId) {
        jdbc.sql("UPDATE hrm.employees SET user_id = :userId WHERE id = :id")
                .param("userId", userId).param("id", employeeId).update();
    }

    private long createPosition(String code) throws Exception {
        String grade = authed(post("/api/v1/job-grades").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Cấp thử", "minSalary": 6000000, "maxSalary": 9000000}
                """.formatted(code + "G")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String body = authed(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Chức danh %s", "jobGradeId": %d}
                """.formatted(code, code, ((Number) JsonPath.read(grade, "$.id")).longValue())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createDepartment(String code, Long parentId) throws Exception {
        String body = authed(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Phòng %s", "parentId": %s}
                """.formatted(code, code, parentId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createEmployee(String code, String name, long departmentId, String workEmail) throws Exception {
        String body = authed(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content(employeeJson(code, name, departmentId, workEmail, "ACTIVE")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String employeeJson(String code, String name, long departmentId, String workEmail, String status) {
        return """
                {"code": "%s", "fullName": "%s", "dateOfBirth": "1990-01-01", "phone": "0903000000",
                 "workEmail": %s, "departmentId": %d, "positionId": %d, "hireDate": "2024-01-02", "status": "%s"}
                """.formatted(code, name, workEmail == null ? "null" : "\"" + workEmail + "\"", departmentId,
                position, status);
    }

    private String updateJson(String code, String name, long departmentId, String status, int version) {
        String json = employeeJson(code, name, departmentId, null, status).trim();
        return json.substring(0, json.length() - 1) + ", \"version\": " + version + "}";
    }

    private int version(String table, long id) {
        return jdbc.sql("SELECT version FROM hrm." + table + " WHERE id = :id").param("id", id)
                .query(Integer.class).single();
    }

    private ResultActions authed(MockHttpServletRequestBuilder request) throws Exception {
        return as(hrToken, request);
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, TestUsers.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
