package vn.sspack.hrm.org.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
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
 * ORG-02 qua HTTP thật trên PostgreSQL 16: cấp bậc kèm khung lương, chức danh bắt buộc gắn cấp bậc,
 * chặn xóa/ngừng khi còn dùng, quyền xem khung lương, audit.
 */
@IntegrationTest
class OrgCatalogIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers testUsers;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    String hrToken;
    long hrId;
    /** Tiền tố riêng cho mỗi test để mã không trùng giữa các test. */
    String prefix;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(Instant.now());
        TestUsers.Created hr = testUsers.create("HR");
        hrId = hr.id();
        hrToken = login(hr.email());
        prefix = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    // ================================================================= quyền

    @Test
    @DisplayName("Nhân viên xem được chức danh nhưng không xem được khung lương, không sửa được gì: 403")
    void employeePermissions() throws Exception {
        String employeeToken = login(testUsers.create("EMPLOYEE").email());
        long grade = createGrade(prefix + "-G", 1_000_000, 2_000_000);
        long position = createPosition(prefix + "-P", grade);

        mvc.perform(get("/api/v1/positions").header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/positions/" + position).header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobGradeCode").value(prefix + "-G"))
                .andExpect(jsonPath("$.minSalary").doesNotExist())
                .andExpect(jsonPath("$.maxSalary").doesNotExist());

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/job-grades"),
                get("/api/v1/job-grades/" + grade),
                post("/api/v1/job-grades").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/job-grades/" + grade).contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/job-grades/" + grade + "/status").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/job-grades/" + grade),
                post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/positions/" + position).contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/positions/" + position + "/status").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/positions/" + position))) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // ================================================================= cấp bậc

    @Test
    @DisplayName("Cấp bậc: tạo, danh sách theo khung lương tăng dần, mã trùng 409, khung ngược 400")
    void gradeCrudAndValidation() throws Exception {
        long high = createGrade(prefix + "-HI", 25_000_000, 40_000_000);
        long low = createGrade(prefix.toLowerCase() + "-lo", 6_000_000, 9_000_000);

        authed(get("/api/v1/job-grades/" + low))
                .andExpect(jsonPath("$.code").value(prefix + "-LO"))
                .andExpect(jsonPath("$.minSalary").value(6_000_000))
                .andExpect(jsonPath("$.maxSalary").value(9_000_000))
                .andExpect(jsonPath("$.positionCount").value(0));
        List<Integer> ids = JsonPath.read(authed(get("/api/v1/job-grades")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.code =~ /" + prefix + "-.*/)].id");
        assertThat(ids).containsExactly((int) low, (int) high);

        authed(post("/api/v1/job-grades").contentType(MediaType.APPLICATION_JSON).content(gradeJson(
                prefix + "-HI", 1, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_GRADE_CODE_EXISTS"));
        authed(post("/api/v1/job-grades").contentType(MediaType.APPLICATION_JSON).content(gradeJson(
                prefix + "-X", 9_000_000, 6_000_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_GRADE_RANGE_INVALID"));
        authed(post("/api/v1/job-grades").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "A B", "name": "", "minSalary": 1500.5, "maxSalary": -1}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.minSalary").exists())
                .andExpect(jsonPath("$.errors.maxSalary").exists());

        authed(put("/api/v1/job-grades/" + low).contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Phổ thông", "minSalary": 6500000, "maxSalary": 9500000, "version": %d}
                """.formatted(prefix + "-LO", version("job_grades", low))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Phổ thông"))
                .andExpect(jsonPath("$.minSalary").value(6_500_000));
    }

    @Test
    @DisplayName("Cấp bậc còn chức danh: không ngừng được khi chức danh đang hoạt động, không xóa được khi còn chức danh")
    void gradeInUse() throws Exception {
        long grade = createGrade(prefix + "-G", 1_000_000, 2_000_000);
        long position = createPosition(prefix + "-P", grade);

        setGradeActive(grade, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_GRADE_HAS_ACTIVE_POSITIONS"))
                .andExpect(jsonPath("$.positionCount").value(1));
        authed(delete("/api/v1/job-grades/" + grade))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_GRADE_HAS_POSITIONS"));

        setPositionActive(position, false).andExpect(status().isOk());
        setGradeActive(grade, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.positionCount").value(1))
                .andExpect(jsonPath("$.activePositionCount").value(0));

        // Cấp bậc đã ngừng: chức danh không hoạt động lại được, chức danh mới không gắn vào được
        setPositionActive(position, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSITION_GRADE_INACTIVE"));
        authed(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON).content(positionJson(
                prefix + "-NEW", grade)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSITION_GRADE_INACTIVE"));

        String code = prefix + "-G";
        authed(get("/api/v1/job-grades")).andExpect(jsonPath("$[?(@.code == '" + code + "')]").isEmpty());
        authed(get("/api/v1/job-grades").param("includeInactive", "true"))
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].active").value(false));

        authed(delete("/api/v1/positions/" + position)).andExpect(status().isNoContent());
        authed(delete("/api/v1/job-grades/" + grade)).andExpect(status().isNoContent());
        authed(get("/api/v1/job-grades/" + grade))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_GRADE_NOT_FOUND"));
    }

    // ================================================================= chức danh

    @Test
    @DisplayName("Chức danh bắt buộc gắn cấp bậc: thiếu 400, cấp bậc không tồn tại 400, mã trùng 409")
    void positionRequiresGrade() throws Exception {
        long grade = createGrade(prefix + "-G", 1_000_000, 2_000_000);
        createPosition(prefix + "-P", grade);

        authed(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Không cấp bậc"}
                """.formatted(prefix + "-NG")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.jobGradeId").exists());
        authed(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON).content(positionJson(
                prefix + "-NG", 999_999_999)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POSITION_GRADE_NOT_FOUND"));
        authed(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON).content(positionJson(
                (prefix + "-p").toLowerCase(), grade)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSITION_CODE_EXISTS"));
    }

    @Test
    @DisplayName("Đổi cấp bậc, lọc theo từ khóa và cấp bậc; sửa trùng phiên bản 409")
    void positionUpdateAndFilter() throws Exception {
        long g1 = createGrade(prefix + "-G1", 1_000_000, 2_000_000);
        long g2 = createGrade(prefix + "-G2", 2_000_000, 3_000_000);
        long qc = createPosition(prefix + "-QC", g1);
        createPosition(prefix + "-KHO", g1);
        int staleVersion = version("positions", qc);

        authed(put("/api/v1/positions/" + qc).contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Kỹ sư chất lượng", "jobGradeId": %d, "version": %d}
                """.formatted(prefix + "-QC", g2, staleVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobGradeId").value(g2))
                .andExpect(jsonPath("$.jobGradeCode").value(prefix + "-G2"));
        authed(put("/api/v1/positions/" + qc).contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Sửa đè", "jobGradeId": %d, "version": %d}
                """.formatted(prefix + "-QC", g2, staleVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        authed(get("/api/v1/positions").param("jobGradeId", String.valueOf(g1)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value(prefix + "-KHO"));
        authed(get("/api/v1/positions").param("q", "kỹ sư chất"))
                .andExpect(jsonPath("$[?(@.code == '" + prefix + "-QC')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.code == '" + prefix + "-KHO')]").isEmpty());
        authed(get("/api/v1/job-grades/" + g1)).andExpect(jsonPath("$.positionCount").value(1));
    }

    @Test
    @DisplayName("Chức danh đã có nhân viên (kể cả đã nghỉ) không xóa được; ngừng hoạt động thì được")
    void positionWithEmployees() throws Exception {
        long grade = createGrade(prefix + "-G", 1_000_000, 2_000_000);
        long staffed = createPosition(prefix + "-S", grade);
        insertEmployee(staffed, "ACTIVE");
        insertEmployee(staffed, "RESIGNED");

        authed(get("/api/v1/positions/" + staffed))
                .andExpect(jsonPath("$.employeeCount").value(2))
                .andExpect(jsonPath("$.workingEmployeeCount").value(1));
        authed(delete("/api/v1/positions/" + staffed))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSITION_HAS_EMPLOYEES"))
                .andExpect(jsonPath("$.employeeCount").value(2));
        setPositionActive(staffed, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ================================================================= audit

    @Test
    @DisplayName("Mỗi thao tác ghi đúng một dòng audit trên bảng job_grades / positions, kèm giá trị trước/sau")
    void audited() throws Exception {
        long grade = createGrade(prefix + "-G", 1_000_000, 2_000_000);
        authed(put("/api/v1/job-grades/" + grade).contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Đổi", "minSalary": 1500000, "maxSalary": 2000000, "version": %d}
                """.formatted(prefix + "-G", version("job_grades", grade)))).andExpect(status().isOk());
        long position = createPosition(prefix + "-P", grade);
        setPositionActive(position, false).andExpect(status().isOk());
        authed(delete("/api/v1/positions/" + position)).andExpect(status().isNoContent());
        authed(delete("/api/v1/job-grades/" + grade)).andExpect(status().isNoContent());

        assertThat(auditActions("job_grades", grade)).containsExactly("CREATE", "UPDATE", "DELETE");
        assertThat(auditActions("positions", position)).containsExactly("CREATE", "UPDATE", "DELETE");

        String salaryChange = jdbc.sql("""
                        SELECT (old_value ->> 'minSalary') || '>' || (new_value ->> 'minSalary')
                        FROM hrm.audit_logs
                        WHERE entity_type = 'job_grades' AND entity_id = :id AND action = 'UPDATE'
                        """)
                .param("id", grade).query(String.class).single();
        assertThat(salaryChange).isEqualTo("1000000>1500000");
    }

    // ================================================================= tiện ích

    private ResultActions authed(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + hrToken));
    }

    private static String gradeJson(String code, long min, long max) {
        return """
                {"code": "%s", "name": "Cấp %s", "minSalary": %d, "maxSalary": %d}
                """.formatted(code, code, min, max);
    }

    private static String positionJson(String code, long gradeId) {
        return """
                {"code": "%s", "name": "Chức danh %s", "jobGradeId": %d}
                """.formatted(code, code, gradeId);
    }

    private long createGrade(String code, long min, long max) throws Exception {
        String body = authed(post("/api/v1/job-grades").contentType(MediaType.APPLICATION_JSON)
                .content(gradeJson(code, min, max)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createPosition(String code, long gradeId) throws Exception {
        String body = authed(post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON)
                .content(positionJson(code, gradeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions setGradeActive(long id, boolean active) throws Exception {
        return authed(put("/api/v1/job-grades/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("""
                {"active": %s, "version": %d}
                """.formatted(active, version("job_grades", id))));
    }

    private ResultActions setPositionActive(long id, boolean active) throws Exception {
        return authed(put("/api/v1/positions/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("""
                {"active": %s, "version": %d}
                """.formatted(active, version("positions", id))));
    }

    /** {@code table} chỉ là hằng trong test. */
    private int version(String table, long id) {
        return jdbc.sql("SELECT version FROM hrm." + table + " WHERE id = :id").param("id", id)
                .query(Integer.class).single();
    }

    private List<String> auditActions(String table, long id) {
        return jdbc.sql("""
                        SELECT action FROM hrm.audit_logs
                        WHERE entity_type = :table AND entity_id = :id AND user_id = :userId ORDER BY id
                        """)
                .param("table", table).param("id", id).param("userId", hrId).query(String.class).list();
    }

    private void insertEmployee(long positionId, String status) throws Exception {
        String department = authed(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Phòng thử"}
                """.formatted(prefix + "-D" + UUID.randomUUID().toString().substring(0, 4).toUpperCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        jdbc.sql("""
                        INSERT INTO hrm.employees
                            (company_id, employee_code, full_name, department_id, position_id, hire_date, status)
                        SELECT company_id, :code, 'Nhân viên thử', :departmentId, id, DATE '2025-01-02', :status
                        FROM hrm.positions WHERE id = :positionId
                        """)
                .param("code", "E" + UUID.randomUUID().toString().substring(0, 8))
                .param("departmentId", ((Number) JsonPath.read(department, "$.id")).longValue())
                .param("positionId", positionId)
                .param("status", status)
                .update();
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
