package vn.sspack.hrm.platform.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import vn.sspack.hrm.support.IntegrationTest;
import vn.sspack.hrm.support.MutableClock;
import vn.sspack.hrm.support.TestUsers;

/** PLT-01 qua HTTP thật trên PostgreSQL 16: đăng nhập, khóa tài khoản, làm mới, đăng xuất, audit. */
@IntegrationTest
class AuthFlowIT {

    static final String COOKIE = "hrm_refresh";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers testUsers;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetClock() {
        clock.setInstant(Instant.now());
    }

    // ================================================================= đăng nhập

    @Test
    @DisplayName("Đăng nhập đúng: access token 15 phút, refresh token trong cookie HttpOnly Secure SameSite=Strict")
    void loginReturnsAccessTokenAndRefreshCookie() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");

        MvcResult result = login(user.email(), TestUsers.PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(COOKIE + "=")
                .contains("Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict")
                .as("không ghi nhớ đăng nhập: cookie phiên").doesNotContain("Max-Age");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    @DisplayName("Ghi nhớ đăng nhập: cookie refresh tồn tại 7 ngày")
    void rememberMeSetsSevenDayCookie() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");

        MvcResult result = login(user.email(), TestUsers.PASSWORD, true).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=604800");
    }

    @Test
    @DisplayName("Access token dùng được cho /me: vai trò, phạm vi dữ liệu, quyền")
    void meReturnsRolesScopesAndPermissions() throws Exception {
        TestUsers.Created user = testUsers.create("MANAGER", "EMPLOYEE");
        String accessToken = accessToken(login(user.email(), TestUsers.PASSWORD, false).andReturn());

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.roles[0].code").value("EMPLOYEE"))
                .andExpect(jsonPath("$.roles[0].dataScope").value("SELF"))
                .andExpect(jsonPath("$.roles[1].code").value("MANAGER"))
                .andExpect(jsonPath("$.roles[1].dataScope").value("DEPARTMENT_TREE"))
                .andExpect(jsonPath("$.permissions", hasItem("leave:approve")))
                .andExpect(jsonPath("$.permissions", hasItem("leave:request")))
                .andExpect(jsonPath("$.permissions", not(hasItem("payroll:manage"))));
    }

    @Test
    @DisplayName("Email không phân biệt hoa thường")
    void emailIsCaseInsensitive() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");

        login(user.email().toUpperCase(), TestUsers.PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sai mật khẩu và email không tồn tại trả cùng một thông báo")
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");

        login(user.email(), "sai-mat-khau", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng."));
        login("khong-ton-tai@sspack.vn", "sai-mat-khau", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng."));
    }

    @Test
    @DisplayName("Thiếu dữ liệu: 400 với lỗi từng trường bằng tiếng Việt")
    void validationErrorsAreVietnamese() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"khong-phai-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").value("Email không đúng định dạng"))
                .andExpect(jsonPath("$.errors.password").value("Vui lòng nhập mật khẩu"));
    }

    // ================================================================= khóa tài khoản

    @Test
    @DisplayName("Sai 5 lần: khóa 15 phút (423), kể cả nhập đúng; hết 15 phút đăng nhập lại được")
    void fiveFailuresLockAccountForFifteenMinutes() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");

        for (int i = 1; i <= 4; i++) {
            login(user.email(), "sai-" + i, false).andExpect(status().isUnauthorized());
        }
        login(user.email(), "sai-5", false)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.detail").value(
                        "Tài khoản tạm khóa do đăng nhập sai nhiều lần. Vui lòng thử lại sau 15 phút."))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900));

        clock.advance(Duration.ofMinutes(14));
        login(user.email(), TestUsers.PASSWORD, false)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.detail").value(
                        "Tài khoản tạm khóa do đăng nhập sai nhiều lần. Vui lòng thử lại sau 1 phút."));

        clock.advance(Duration.ofMinutes(1));
        login(user.email(), TestUsers.PASSWORD, false).andExpect(status().isOk());
        assertThat(failedLoginCount(user.id())).isZero();
    }

    @Test
    @DisplayName("Tài khoản bị vô hiệu hóa: 403 khi nhập đúng mật khẩu")
    void disabledAccountCannotLogIn() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");
        testUsers.setStatus(user.id(), "DISABLED");

        login(user.email(), TestUsers.PASSWORD, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
    }

    // ================================================================= làm mới, đăng xuất

    @Test
    @DisplayName("Làm mới: xoay vòng refresh token, token cũ không dùng lại được")
    void refreshRotatesToken() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");
        String first = refreshCookie(login(user.email(), TestUsers.PASSWORD, false).andReturn());

        MvcResult refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String second = refreshCookie(refreshed);

        assertThat(second).isNotEqualTo(first);
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_EXPIRED"));
        refresh(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token đã thu hồi bị dùng lại sau ân hạn 30 giây: thu hồi mọi phiên và ghi audit")
    void reuseAfterGraceRevokesAllSessions() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");
        String stolen = refreshCookie(login(user.email(), TestUsers.PASSWORD, false).andReturn());
        String current = refreshCookie(refresh(stolen).andExpect(status().isOk()).andReturn());
        String otherDevice = refreshCookie(login(user.email(), TestUsers.PASSWORD, false).andReturn());

        clock.advance(Duration.ofSeconds(31));
        refresh(stolen).andExpect(status().isUnauthorized());

        refresh(current).andExpect(status().isUnauthorized());
        refresh(otherDevice).andExpect(status().isUnauthorized());
        assertThat(auditResults(user.id())).contains("REUSE_DETECTED");
    }

    @Test
    @DisplayName("Refresh token hết hạn sau 7 ngày")
    void refreshTokenExpiresAfterSevenDays() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");
        String token = refreshCookie(login(user.email(), TestUsers.PASSWORD, true).andReturn());

        clock.advance(Duration.ofDays(7));

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Không có cookie: 401")
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("Đăng xuất: thu hồi refresh token và xóa cookie")
    void logoutRevokesRefreshToken() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");
        String token = refreshCookie(login(user.email(), TestUsers.PASSWORD, false).andReturn());

        MvcResult result = mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, token)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        refresh(token).andExpect(status().isUnauthorized());
    }

    // ================================================================= audit

    @Test
    @DisplayName("Mỗi lần đăng nhập ghi audit LOGIN kèm kết quả và IP")
    void loginAttemptsAreAudited() throws Exception {
        TestUsers.Created user = testUsers.create("EMPLOYEE");

        login(user.email(), "sai", false);
        login(user.email(), TestUsers.PASSWORD, false);

        assertThat(auditResults(user.id())).containsExactly("FAILED", "SUCCESS");
        String ip = jdbc.sql("SELECT host(ip_address) FROM hrm.audit_logs WHERE user_id = :id LIMIT 1")
                .param("id", user.id()).query(String.class).single();
        assertThat(ip).isEqualTo("127.0.0.1");
    }

    // ================================================================= tiện ích

    private ResultActions login(String email, String password, boolean rememberMe) throws Exception {
        String body = """
                {"email":"%s","password":"%s","rememberMe":%s}""".formatted(email, password, rememberMe);
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refreshToken)));
    }

    private static String refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("cookie " + COOKIE).isNotNull();
        return cookie.getValue();
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private int failedLoginCount(long userId) {
        return jdbc.sql("SELECT failed_login_count FROM hrm.users WHERE id = :id")
                .param("id", userId).query(Integer.class).single();
    }

    private java.util.List<String> auditResults(long userId) {
        return jdbc.sql("""
                        SELECT new_value ->> 'result' FROM hrm.audit_logs
                        WHERE user_id = :id AND action = 'LOGIN' ORDER BY id
                        """)
                .param("id", userId).query(String.class).list();
    }
}
