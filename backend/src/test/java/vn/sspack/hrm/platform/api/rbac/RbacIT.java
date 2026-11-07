package vn.sspack.hrm.platform.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

/** PLT-03 qua HTTP thật trên PostgreSQL 16: quyền kiểm tra ở API, CRUD vai trò, gán vai trò, audit. */
@IntegrationTest
class RbacIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers testUsers;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    String adminToken;
    long adminId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(Instant.now());
        TestUsers.Created admin = testUsers.create("ADMIN");
        adminId = admin.id();
        adminToken = login(admin.email());
    }

    // ================================================================= kiểm tra quyền ở API

    @Test
    @DisplayName("Không có role:manage / user:manage: mọi API phân quyền trả 403")
    void nonAdminIsForbidden() throws Exception {
        String hrToken = login(testUsers.create("HR", "EMPLOYEE").email());

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/permissions"),
                get("/api/v1/roles"),
                get("/api/v1/roles/1"),
                post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/roles/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/roles/1"),
                get("/api/v1/users"),
                put("/api/v1/users/1/roles").contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + hrToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    @DisplayName("Chưa đăng nhập: 401")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
    }

    // ================================================================= đọc

    @Test
    @DisplayName("Danh mục quyền nhóm theo phân hệ; danh sách vai trò có vai trò hệ thống lên trước")
    void listsPermissionsAndRoles() throws Exception {
        authed(get("/api/v1/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(26))
                .andExpect(jsonPath("$[?(@.code == 'leave:approve')].module").value("leave"));

        authed(get("/api/v1/roles").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[0].code").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].system").value(true))
                .andExpect(jsonPath("$.content[0].permissionCount").value(7))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    // ================================================================= tạo, sửa, xóa

    @Test
    @DisplayName("Tạo, sửa, xóa vai trò tùy chỉnh; mỗi thao tác ghi đúng một dòng audit")
    void roleLifecycleIsAudited() throws Exception {
        String code = uniqueCode();
        String created = authed(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Trưởng ca", "description": "Duyệt đơn trong ca",
                 "dataScope": "DEPARTMENT", "permissions": ["leave:read", "leave:approve"]}
                """.formatted(code.toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.system").value(false))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("leave:read", "leave:approve")))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        int version = JsonPath.read(created, "$.version");

        authed(put("/api/v1/roles/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Trưởng ca sản xuất", "dataScope": "DEPARTMENT_TREE",
                 "permissions": ["leave:read", "attendance:confirm"], "version": %d}
                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trưởng ca sản xuất"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("leave:read", "attendance:confirm")))
                .andExpect(jsonPath("$.version").value(version + 1));

        authed(delete("/api/v1/roles/" + id)).andExpect(status().isNoContent());
        authed(get("/api/v1/roles/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));

        List<String> actions = jdbc.sql("""
                        SELECT action FROM hrm.audit_logs
                        WHERE entity_type = 'role' AND entity_id = :id AND user_id = :userId ORDER BY id
                        """)
                .param("id", id).param("userId", adminId).query(String.class).list();
        assertThat(actions).containsExactly("CREATE", "UPDATE", "DELETE");

        String updateAudit = jdbc.sql("""
                        SELECT old_value::text || '|' || new_value::text FROM hrm.audit_logs
                        WHERE entity_type = 'role' AND entity_id = :id AND action = 'UPDATE'
                        """)
                .param("id", id).query(String.class).single();
        assertThat(updateAudit).contains("leave:approve", "attendance:confirm", "Trưởng ca sản xuất");
    }

    @Test
    @DisplayName("Mã trùng: 409; quyền không tồn tại: 400; thiếu trường: 400 kèm lỗi từng trường")
    void createValidation() throws Exception {
        authed(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "hr", "name": "Trùng", "dataScope": "ALL", "permissions": ["leave:read"]}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_CODE_EXISTS"));

        authed(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Sai quyền", "dataScope": "SELF", "permissions": ["leave:fly"]}
                """.formatted(uniqueCode())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_UNKNOWN"))
                .andExpect(jsonPath("$.permissions[0]").value("leave:fly"));

        authed(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "x", "name": "", "permissions": []}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.dataScope").exists())
                .andExpect(jsonPath("$.errors.permissions").exists());
    }

    @Test
    @DisplayName("Sửa bằng version cũ: 409 và dữ liệu không đổi")
    void staleVersionIsRejected() throws Exception {
        long id = createRole(uniqueCode());
        String body = """
                {"name": "Lần %d", "dataScope": "SELF", "permissions": ["leave:read"], "version": 0}
                """;

        authed(put("/api/v1/roles/" + id).contentType(MediaType.APPLICATION_JSON).content(body.formatted(1)))
                .andExpect(status().isOk());
        authed(put("/api/v1/roles/" + id).contentType(MediaType.APPLICATION_JSON).content(body.formatted(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        authed(get("/api/v1/roles/" + id)).andExpect(jsonPath("$.name").value("Lần 1"));
    }

    @Test
    @DisplayName("Vai trò hệ thống không xóa được; ADMIN không bỏ được role:manage")
    void systemRolesAreProtected() throws Exception {
        long adminRoleId = roleId("ADMIN");
        authed(delete("/api/v1/roles/" + roleId("INTERVIEWER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_SYSTEM_NOT_DELETABLE"));

        int version = JsonPath.read(authed(get("/api/v1/roles/" + adminRoleId)).andReturn()
                .getResponse().getContentAsString(), "$.version");
        authed(put("/api/v1/roles/" + adminRoleId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Quản trị hệ thống", "dataScope": "ALL", "permissions": ["user:manage"], "version": %d}
                """.formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ADMIN_MUST_MANAGE_ROLES"));
    }

    @Test
    @DisplayName("Vai trò đang gán cho người dùng không xóa được")
    void assignedRoleIsNotDeletable() throws Exception {
        String code = uniqueCode();
        long id = createRole(code);
        testUsers.create(code);

        authed(delete("/api/v1/roles/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_IN_USE"))
                .andExpect(jsonPath("$.assignedUserCount").value(1));
    }

    // ================================================================= gán vai trò

    @Test
    @DisplayName("Gán vai trò mới cho tài khoản: lần đăng nhập sau token mang quyền của vai trò đó")
    void assignedRoleGrantsPermissionsOnNextToken() throws Exception {
        String code = uniqueCode();
        long customRoleId = createRole(code);
        TestUsers.Created user = testUsers.create("EMPLOYEE");

        String listed = authed(get("/api/v1/users").param("q", user.email().substring(0, 13)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(user.email()))
                .andExpect(jsonPath("$.content[0].roles[0].code").value("EMPLOYEE"))
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(listed, "$.content[0].version");

        authed(put("/api/v1/users/" + user.id() + "/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"roleIds": [%d, %d], "version": %d}
                """.formatted(roleId("EMPLOYEE"), customRoleId, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[*].code", containsInAnyOrder("EMPLOYEE", code)));

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + login(user.email())))
                .andExpect(jsonPath("$.permissions", hasItem("leave:approve")));

        String audit = jdbc.sql("""
                        SELECT new_value::text FROM hrm.audit_logs
                        WHERE entity_type = 'user' AND entity_id = :id AND action = 'UPDATE'
                        """)
                .param("id", user.id()).query(String.class).single();
        assertThat(audit).contains(code);
    }

    @Test
    @DisplayName("Admin không tự gỡ vai trò Admin của mình; vai trò không tồn tại: 400")
    void assignRolesGuards() throws Exception {
        authed(put("/api/v1/users/" + adminId + "/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"roleIds": [%d], "version": %d}
                """.formatted(roleId("EMPLOYEE"), userVersion(adminId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_SELF_LOCKOUT"));

        TestUsers.Created user = testUsers.create("EMPLOYEE");
        authed(put("/api/v1/users/" + user.id() + "/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"roleIds": [999999], "version": 0}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_UNKNOWN"));

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + login(user.email())))
                .andExpect(jsonPath("$.permissions", not(hasItem("leave:approve"))));
    }

    // ================================================================= tiện ích

    private ResultActions authed(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, TestUsers.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private long createRole(String code) throws Exception {
        String body = authed(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Thử %s", "dataScope": "DEPARTMENT", "permissions": ["leave:approve"]}
                """.formatted(code, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long roleId(String code) {
        return jdbc.sql("SELECT id FROM hrm.roles WHERE code = :code").param("code", code).query(Long.class).single();
    }

    /** Đăng nhập làm tăng version của tài khoản nên đọc từ CSDL. */
    private int userVersion(long userId) {
        return jdbc.sql("SELECT version FROM hrm.users WHERE id = :id").param("id", userId).query(Integer.class)
                .single();
    }

    private static String uniqueCode() {
        return "R_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
