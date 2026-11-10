package vn.sspack.hrm.org.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * ORG-03 qua HTTP thật trên PostgreSQL 16: sơ đồ chỉ gồm phòng ban đang hoạt động và nhân viên đang làm việc,
 * chỉ thông tin công việc, cấp bậc cao đứng trước, mọi người dùng xem được cả công ty.
 */
@IntegrationTest
class OrgChartIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers testUsers;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    String hrToken;
    String prefix;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(Instant.now());
        hrToken = login(testUsers.create("HR").email());
        prefix = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    @Test
    @DisplayName("Nhân viên (phạm vi SELF) xem được cả sơ đồ, chỉ thông tin công việc; chỉ mở được hồ sơ của mình")
    void employeeSeesWholeChartWithWorkInfoOnly() throws Exception {
        long low = createPosition(prefix + "-NV", 6_000_000, 9_000_000);
        long high = createPosition(prefix + "-TP", 25_000_000, 40_000_000);
        long root = createDepartment(prefix + "-R", null);
        long child = createDepartment(prefix + "-C", root);
        long staff = createEmployee(prefix + "-01", "Bùi Văn Bình", child, low, "ACTIVE");
        createEmployee(prefix + "-02", "Âu Thị An", child, low, "PROBATION");
        createEmployee(prefix + "-03", "Trưởng Phòng Con", child, high, "ACTIVE");
        long left = createEmployee(prefix + "-04", "Người Đã Nghỉ", child, low, "ACTIVE");
        jdbc.sql("UPDATE hrm.employees SET status = 'RESIGNED' WHERE id = :id").param("id", left).update();

        TestUsers.Created staffUser = testUsers.create("EMPLOYEE");
        jdbc.sql("UPDATE hrm.employees SET user_id = :u WHERE id = :id")
                .param("u", staffUser.id()).param("id", staff).update();
        String body = as(login(staffUser.email()), get("/api/v1/org-chart"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> rootEntry = JsonPath.read(body, "$[?(@.id == " + root + ")]");
        assertThat(rootEntry).hasSize(1);
        assertThat((List<?>) rootEntry.get(0).get("members")).isEmpty();

        List<Map<String, Object>> members = JsonPath.read(body, "$[?(@.id == " + child + ")].members[*]");
        // Cấp bậc cao trước; cùng cấp theo tên tiếng Việt (Âu trước Bùi); người đã nghỉ không có
        assertThat(members).extracting(m -> m.get("fullName"))
                .containsExactly("Trưởng Phòng Con", "Âu Thị An", "Bùi Văn Bình");
        assertThat(members.get(0).get("positionName")).isEqualTo("Chức danh " + prefix + "-TP");
        assertThat(members).allSatisfy(m -> assertThat(m.keySet())
                .containsExactlyInAnyOrder("id", "code", "fullName", "positionName", "workEmail", "avatarUrl",
                        "profileVisible", "self"));
        assertThat(members).extracting(m -> m.get("self")).containsExactly(false, false, true);
        assertThat(members).extracting(m -> m.get("profileVisible")).containsExactly(false, false, true);
        assertThat(body).doesNotContain("0903000000").doesNotContain("1990-01-01");
    }

    @Test
    @DisplayName("Phòng ban ngừng hoạt động không có trên sơ đồ; HR mở được mọi hồ sơ")
    void inactiveDepartmentsHidden() throws Exception {
        long position = createPosition(prefix + "-P", 6_000_000, 9_000_000);
        long kept = createDepartment(prefix + "-K", null);
        long closed = createDepartment(prefix + "-X", null);
        createEmployee(prefix + "-01", "Người ở lại", kept, position, "ACTIVE");
        as(hrToken, put("/api/v1/departments/" + closed + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false, \"version\": %d}".formatted(jdbc.sql(
                        "SELECT version FROM hrm.departments WHERE id = :id").param("id", closed)
                        .query(Integer.class).single())))
                .andExpect(status().isOk());

        String body = as(hrToken, get("/api/v1/org-chart")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((List<?>) JsonPath.read(body, "$[?(@.id == " + closed + ")]")).isEmpty();
        List<Boolean> visible = JsonPath.read(body, "$[?(@.id == " + kept + ")].members[*].profileVisible");
        assertThat(visible).containsExactly(true);
    }

    @Test
    @DisplayName("Không có org:read: 403")
    void needsOrgRead() throws Exception {
        String token = login(testUsers.create("INTERVIEWER").email());
        as(token, get("/api/v1/org-chart")).andExpect(status().isForbidden());
    }

    // ================================================================= tiện ích

    private long createPosition(String code, long min, long max) throws Exception {
        String grade = as(hrToken, post("/api/v1/job-grades").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Cấp %s", "minSalary": %d, "maxSalary": %d}
                """.formatted(code + "G", code, min, max)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String body = as(hrToken, post("/api/v1/positions").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Chức danh %s", "jobGradeId": %d}
                """.formatted(code, code, ((Number) JsonPath.read(grade, "$.id")).longValue())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createDepartment(String code, Long parentId) throws Exception {
        String body = as(hrToken, post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Phòng %s", "parentId": %s}
                """.formatted(code, code, parentId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createEmployee(String code, String name, long departmentId, long positionId, String status)
            throws Exception {
        String body = as(hrToken, post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "fullName": "%s", "dateOfBirth": "1990-01-01", "phone": "0903000000",
                 "departmentId": %d, "positionId": %d, "hireDate": "2024-01-02", "status": "%s"}
                """.formatted(code, name, departmentId, positionId, status)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
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
