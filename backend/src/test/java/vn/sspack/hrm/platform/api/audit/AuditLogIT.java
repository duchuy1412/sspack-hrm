package vn.sspack.hrm.platform.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import vn.sspack.hrm.shared.config.ClockConfig;
import vn.sspack.hrm.support.IntegrationTest;
import vn.sspack.hrm.support.MutableClock;
import vn.sspack.hrm.support.TestUsers;

/** PLT-04 qua HTTP thật trên PostgreSQL 16: ai, lúc nào, bảng nào, giá trị trước/sau; lọc; chỉ thêm dòng. */
@IntegrationTest
class AuditLogIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers testUsers;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    TestUsers.Created admin;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(Instant.now());
        admin = testUsers.create("ADMIN");
        adminToken = accessToken(login(admin.email()));
    }

    // ================================================================= quyền

    @Test
    @DisplayName("Không có audit:read: 403; chưa đăng nhập: 401")
    void requiresAuditRead() throws Exception {
        String hrToken = accessToken(login(testUsers.create("HR").email()));

        for (String url : new String[] {"/api/v1/audit-logs", "/api/v1/audit-logs/actors"}) {
            mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + hrToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    // ================================================================= tiêu chí chấp nhận

    @Test
    @DisplayName("Tạo rồi sửa vai trò: nhật ký ghi ai, lúc nào, bảng nào, giá trị trước/sau")
    void showsWhoWhenWhichTableAndValues() throws Exception {
        clock.setInstant(vnTime("2026-11-07T09:15:00"));
        String code = "R_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String created = authed(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Trưởng ca", "dataScope": "DEPARTMENT", "permissions": ["leave:read"]}
                """.formatted(code)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long roleId = ((Number) JsonPath.read(created, "$.id")).longValue();
        int version = JsonPath.read(created, "$.version");

        clock.setInstant(vnTime("2026-11-07T10:30:00"));
        authed(put("/api/v1/roles/" + roleId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Trưởng ca sản xuất", "dataScope": "DEPARTMENT",
                 "permissions": ["leave:read", "leave:approve"], "version": %d}
                """.formatted(version)))
                .andExpect(status().isOk());

        authed(get("/api/v1/audit-logs").param("entityType", "roles").param("entityId", String.valueOf(roleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                // mới nhất trước
                .andExpect(jsonPath("$.content[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.content[0].userEmail").value(admin.email()))
                .andExpect(jsonPath("$.content[0].entityType").value("roles"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-11-07T10:30:00+07:00"))
                .andExpect(jsonPath("$.content[0].oldValue.name").value("Trưởng ca"))
                .andExpect(jsonPath("$.content[0].newValue.name").value("Trưởng ca sản xuất"))
                .andExpect(jsonPath("$.content[0].newValue.permissions", hasItem("leave:approve")))
                .andExpect(jsonPath("$.content[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$.content[1].action").value("CREATE"))
                .andExpect(jsonPath("$.content[1].oldValue").doesNotExist())
                .andExpect(jsonPath("$.content[1].newValue.code").value(code));
    }

    @Test
    @DisplayName("Đăng xuất được ghi nhật ký LOGOUT")
    void logoutIsAudited() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");
        MvcResult result = login(user.email());
        String cookie = result.getResponse().getCookie("hrm_refresh").getValue();

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("hrm_refresh", cookie)))
                .andExpect(status().isNoContent());

        authed(get("/api/v1/audit-logs").param("userId", String.valueOf(user.id())).param("action", "LOGOUT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entityType").value("refresh_tokens"))
                .andExpect(jsonPath("$.content[0].newValue.email").value(user.email()));
    }

    // ================================================================= lọc, phân trang

    @Test
    @DisplayName("Lọc theo khoảng ngày giờ Việt Nam: gồm trọn ngày cuối, ranh giới nửa đêm đúng múi giờ")
    void filtersByVietnamDates() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");
        clock.setInstant(vnTime("2026-01-09T23:59:00"));
        login(user.email());
        clock.setInstant(vnTime("2026-01-10T00:01:00"));
        login(user.email());
        clock.setInstant(vnTime("2026-01-10T23:59:00"));
        login(user.email());

        String userId = String.valueOf(user.id());
        authed(get("/api/v1/audit-logs").param("userId", userId).param("from", "2026-01-10").param("to", "2026-01-10"))
                .andExpect(jsonPath("$.totalElements").value(2));
        authed(get("/api/v1/audit-logs").param("userId", userId).param("to", "2026-01-09"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-01-09T23:59:00+07:00"));
    }

    @Test
    @DisplayName("Phân trang và lọc theo hành động, người thực hiện")
    void paginatesAndFilters() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");
        for (int i = 0; i < 3; i++) {
            clock.setInstant(vnTime("2026-02-01T08:0" + i + ":00"));
            login(user.email());
        }

        authed(get("/api/v1/audit-logs").param("userId", String.valueOf(user.id())).param("action", "LOGIN")
                .param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-02-01T08:00:00+07:00"))
                .andExpect(jsonPath("$.content[0].newValue.result").value("SUCCESS"));

        authed(get("/api/v1/audit-logs/actors"))
                .andExpect(jsonPath("$[*].email", hasItem(user.email())));
    }

    @Test
    @DisplayName("Tham số lọc sai: 400 với mã lỗi rõ ràng")
    void rejectsInvalidFilters() throws Exception {
        authed(get("/api/v1/audit-logs").param("from", "2026-02-02").param("to", "2026-02-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_RANGE_INVALID"));
        authed(get("/api/v1/audit-logs").param("entityType", "salaries"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_ENTITY_UNKNOWN"));
        authed(get("/api/v1/audit-logs").param("action", "HACK"))
                .andExpect(status().isBadRequest());
    }

    // ================================================================= toàn vẹn

    @Test
    @DisplayName("Nhật ký chỉ thêm dòng: UPDATE, DELETE, TRUNCATE đều bị CSDL chặn")
    void auditLogIsAppendOnly() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE hrm.audit_logs SET action = 'DELETE'").update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("chỉ cho phép thêm dòng");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM hrm.audit_logs").update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE hrm.audit_logs").update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Không dòng nhật ký nào chứa mật khẩu hay token")
    void noSecretsInAuditLog() {
        long leaked = jdbc.sql("""
                        SELECT count(*) FROM hrm.audit_logs
                        WHERE coalesce(old_value::text, '') || coalesce(new_value::text, '') ~* '(password|token_hash|\\$2[aby]\\$)'
                        """)
                .query(Long.class).single();

        assertThat(leaked).isZero();
    }

    // ================================================================= tiện ích

    private static Instant vnTime(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ClockConfig.ZONE).toInstant();
    }

    private ResultActions authed(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
    }

    private MvcResult login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, TestUsers.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
