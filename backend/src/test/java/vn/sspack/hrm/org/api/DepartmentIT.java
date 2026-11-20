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

/** ORG-01 qua HTTP thật trên PostgreSQL 16: cây nhiều cấp, kéo thả đổi cấp cha, chặn xóa, quyền, audit. */
@IntegrationTest
class DepartmentIT {

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
    /** Tiền tố riêng cho mỗi test để mã phòng ban không trùng giữa các test. */
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
    @DisplayName("Nhân viên xem được cây (org:read) nhưng không sửa được (org:manage): 403")
    void employeeReadsButCannotManage() throws Exception {
        String employeeToken = login(testUsers.create("EMPLOYEE").email());
        long id = create(prefix + "-A", null);

        mvc.perform(get("/api/v1/departments").header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk());
        for (MockHttpServletRequestBuilder request : List.of(
                post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/departments/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/departments/" + id + "/parent").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/departments/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/departments/" + id))) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // ================================================================= cây nhiều cấp

    @Test
    @DisplayName("Tạo cây 6 cấp: path, cấp và số phòng ban con đúng; danh sách cha trước con")
    void createsDeepTree() throws Exception {
        Long parent = null;
        long[] ids = new long[6];
        for (int i = 0; i < 6; i++) {
            ids[i] = create(prefix + "-L" + i, parent);
            parent = ids[i];
        }

        authed(get("/api/v1/departments/" + ids[5]))
                .andExpect(jsonPath("$.depth").value(6))
                .andExpect(jsonPath("$.parentId").value(ids[4]))
                .andExpect(jsonPath("$.path").value("/%d/%d/%d/%d/%d/%d/".formatted(
                        ids[0], ids[1], ids[2], ids[3], ids[4], ids[5])));
        authed(get("/api/v1/departments/" + ids[0])).andExpect(jsonPath("$.childCount").value(1));

        List<Integer> listed = JsonPath.read(authed(get("/api/v1/departments")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.code =~ /" + prefix + "-L.*/)].depth");
        assertThat(listed).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("Mã trùng: 409; mã sai định dạng hoặc thiếu tên: 400")
    void validation() throws Exception {
        create(prefix + "-DUP", null);

        authed(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Trùng"}
                """.formatted((prefix + "-dup").toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_CODE_EXISTS"));
        authed(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "A B", "name": ""}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ================================================================= kéo thả

    @Test
    @DisplayName("Chuyển một nhánh sang cấp cha khác: path của cả nhánh con được cập nhật")
    void moveRewritesSubtree() throws Exception {
        long nm = create(prefix + "-NM", null);
        long xi = create(prefix + "-XI", nm);
        long offset = create(prefix + "-OFF", xi);
        long tray = create(prefix + "-TRAY", offset);
        long kvp = create(prefix + "-KVP", null);

        authed(put("/api/v1/departments/" + xi + "/parent").contentType(MediaType.APPLICATION_JSON).content("""
                {"parentId": %d, "version": %d}
                """.formatted(kvp, version(xi))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(kvp))
                .andExpect(jsonPath("$.path").value("/%d/%d/".formatted(kvp, xi)));

        assertThat(path(offset)).isEqualTo("/%d/%d/%d/".formatted(kvp, xi, offset));
        assertThat(path(tray)).isEqualTo("/%d/%d/%d/%d/".formatted(kvp, xi, offset, tray));
        authed(get("/api/v1/departments/" + nm)).andExpect(jsonPath("$.childCount").value(0));

        // Đưa lên cấp cao nhất
        authed(put("/api/v1/departments/" + offset + "/parent").contentType(MediaType.APPLICATION_JSON).content("""
                {"parentId": null, "version": %d}
                """.formatted(version(offset))))
                .andExpect(jsonPath("$.depth").value(1));
        assertThat(path(tray)).isEqualTo("/%d/%d/".formatted(offset, tray));
    }

    @Test
    @DisplayName("Chuyển vào phòng ban cấp dưới của chính nó: 409, cây không đổi")
    void rejectsCycle() throws Exception {
        long a = create(prefix + "-A", null);
        long b = create(prefix + "-B", a);
        long c = create(prefix + "-C", b);

        authed(put("/api/v1/departments/" + a + "/parent").contentType(MediaType.APPLICATION_JSON).content("""
                {"parentId": %d, "version": %d}
                """.formatted(c, version(a))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_MOVE_CYCLE"));

        assertThat(path(a)).isEqualTo("/%d/".formatted(a));
        assertThat(path(c)).isEqualTo("/%d/%d/%d/".formatted(a, b, c));
    }

    @Test
    @DisplayName("Sửa bằng version cũ: 409")
    void staleVersion() throws Exception {
        long a = create(prefix + "-A", null);
        int v = version(a);
        String body = """
                {"code": "%s", "name": "Lần %%d", "version": %d}
                """.formatted(prefix + "-A", v);

        authed(put("/api/v1/departments/" + a).contentType(MediaType.APPLICATION_JSON).content(body.formatted(1)))
                .andExpect(status().isOk());
        authed(put("/api/v1/departments/" + a).contentType(MediaType.APPLICATION_JSON).content(body.formatted(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // ================================================================= không xóa phòng ban còn nhân viên

    @Test
    @DisplayName("Không xóa phòng ban còn nhân viên hoặc còn phòng ban con; xóa được phòng ban trống")
    void deleteRules() throws Exception {
        long parent = create(prefix + "-P", null);
        long child = create(prefix + "-C", parent);
        long staffed = create(prefix + "-S", null);
        insertEmployee(staffed, "RESIGNED");

        authed(delete("/api/v1/departments/" + parent))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HAS_CHILDREN"))
                .andExpect(jsonPath("$.childCount").value(1));
        authed(delete("/api/v1/departments/" + staffed))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HAS_EMPLOYEES"))
                .andExpect(jsonPath("$.employeeCount").value(1));

        authed(delete("/api/v1/departments/" + child)).andExpect(status().isNoContent());
        authed(delete("/api/v1/departments/" + parent)).andExpect(status().isNoContent());
        authed(get("/api/v1/departments/" + parent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Ngừng hoạt động: chặn khi còn nhân viên đang làm; ẩn khỏi danh sách mặc định")
    void deactivate() throws Exception {
        long working = create(prefix + "-W", null);
        insertEmployee(working, "ACTIVE");
        long resigned = create(prefix + "-R", null);
        insertEmployee(resigned, "RESIGNED");

        authed(put("/api/v1/departments/" + working + "/status").contentType(MediaType.APPLICATION_JSON).content("""
                {"active": false, "version": %d}
                """.formatted(version(working))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HAS_ACTIVE_EMPLOYEES"));

        authed(put("/api/v1/departments/" + resigned + "/status").contentType(MediaType.APPLICATION_JSON).content("""
                {"active": false, "version": %d}
                """.formatted(version(resigned))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.employeeCount").value(1))
                .andExpect(jsonPath("$.workingEmployeeCount").value(0));

        String code = prefix + "-R";
        authed(get("/api/v1/departments"))
                .andExpect(jsonPath("$[?(@.code == '" + code + "')]").isEmpty());
        authed(get("/api/v1/departments").param("includeInactive", "true"))
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].active").value(false));
    }

    // ================================================================= audit

    @Test
    @DisplayName("Tạo, sửa, chuyển, xóa: mỗi thao tác đúng một dòng audit trên bảng departments")
    void audited() throws Exception {
        long target = create(prefix + "-T", null);
        long d = create(prefix + "-D", null);
        authed(put("/api/v1/departments/" + d).contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Đổi tên", "version": %d}
                """.formatted(prefix + "-D", version(d)))).andExpect(status().isOk());
        authed(put("/api/v1/departments/" + d + "/parent").contentType(MediaType.APPLICATION_JSON).content("""
                {"parentId": %d, "version": %d}
                """.formatted(target, version(d)))).andExpect(status().isOk());
        authed(delete("/api/v1/departments/" + d)).andExpect(status().isNoContent());

        List<String> actions = jdbc.sql("""
                        SELECT action FROM hrm.audit_logs
                        WHERE entity_type = 'departments' AND entity_id = :id AND user_id = :userId ORDER BY id
                        """)
                .param("id", d).param("userId", hrId).query(String.class).list();
        assertThat(actions).containsExactly("CREATE", "UPDATE", "UPDATE", "DELETE");

        String move = jdbc.sql("""
                        SELECT coalesce(old_value ->> 'parentId', '') || '>' || (new_value ->> 'parentId')
                        FROM hrm.audit_logs
                        WHERE entity_type = 'departments' AND entity_id = :id
                          AND new_value -> 'movedDescendants' IS NOT NULL
                        """)
                .param("id", d).query(String.class).single();
        assertThat(move).isEqualTo(">" + target);
    }


    // ================================================================= ORG-04: trưởng đơn vị

    @Test
    @DisplayName("HR chỉ định rồi bỏ trưởng: version tăng, audit UPDATE có headEmployeeId trước/sau")
    void hrAssignsAndClearsHead() throws Exception {
        long dept = create(prefix + "-H", null);
        long emp = insertEmployee(dept, "ACTIVE");
        int v0 = version(dept);

        putHead(dept, emp, v0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.head.employeeId").value(emp))
                .andExpect(jsonPath("$.head.warnings[0]").value("NO_ACCOUNT"))
                .andExpect(jsonPath("$.version").value(v0 + 1));

        Long before = jdbc.sql("""
                        SELECT (old_value ->> 'headEmployeeId')::bigint FROM hrm.audit_logs
                        WHERE entity_type = 'departments' AND entity_id = :id AND action = 'UPDATE'
                        """).param("id", dept).query(Long.class).optional().orElse(null);
        assertThat(before).isNull();
        assertThat(auditUpdates(dept)).isEqualTo(1);
        assertThat(auditHead(dept, "new_value", 1)).isEqualTo(emp);

        putHead(dept, null, version(dept))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.head").doesNotExist());
        assertThat(auditUpdates(dept)).isEqualTo(2);
        assertThat(auditHead(dept, "old_value", 2)).isEqualTo(emp);
        assertThat(auditHead(dept, "new_value", 2)).isNull();
    }

    @Test
    @DisplayName("Nhân viên (EMPLOYEE) không chỉ định được trưởng: 403")
    void employeeCannotAssignHead() throws Exception {
        String employeeToken = login(testUsers.create("EMPLOYEE").email());
        long dept = create(prefix + "-H", null);
        mvc.perform(put("/api/v1/departments/" + dept + "/head").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\": null, \"version\": 0}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Nhân viên đã nghỉ việc: 422 DEPARTMENT_HEAD_NOT_WORKING")
    void rejectsResignedEmployee() throws Exception {
        long dept = create(prefix + "-H", null);
        long emp = insertEmployee(dept, "RESIGNED");
        putHead(dept, emp, version(dept))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HEAD_NOT_WORKING"));
    }

    @Test
    @DisplayName("Nhân viên của công ty khác: 422 DEPARTMENT_HEAD_NOT_WORKING")
    void rejectsEmployeeOfAnotherCompany() throws Exception {
        long dept = create(prefix + "-H", null);
        long foreign = insertForeignEmployee();
        putHead(dept, foreign, version(dept))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HEAD_NOT_WORKING"));
        putHead(dept, 999_999_999L, version(dept))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HEAD_NOT_WORKING"));
    }

    @Test
    @DisplayName("Phòng ban ngừng hoạt động: 409 DEPARTMENT_HEAD_INACTIVE")
    void rejectsOnInactiveDepartment() throws Exception {
        long dept = create(prefix + "-H", null);
        long emp = insertEmployee(create(prefix + "-X", null), "ACTIVE");
        authed(put("/api/v1/departments/" + dept + "/status").contentType(MediaType.APPLICATION_JSON).content("""
                {"active": false, "version": %d}
                """.formatted(version(dept)))).andExpect(status().isOk());
        putHead(dept, emp, version(dept))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HEAD_INACTIVE"));
    }

    @Test
    @DisplayName("Chỉ định bằng version cũ: 409 CONCURRENT_MODIFICATION")
    void staleVersionConflicts() throws Exception {
        long dept = create(prefix + "-H", null);
        long emp = insertEmployee(dept, "ACTIVE");
        int v = version(dept);
        putHead(dept, emp, v).andExpect(status().isOk());
        putHead(dept, null, v)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("Chỉ định lại đúng người đang là trưởng: không đổi version, không thêm audit")
    void sameHeadIsNoOp() throws Exception {
        long dept = create(prefix + "-H", null);
        long emp = insertEmployee(dept, "ACTIVE");
        putHead(dept, emp, version(dept)).andExpect(status().isOk());
        int v = version(dept);

        putHead(dept, emp, v)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(v));
        assertThat(version(dept)).isEqualTo(v);
        assertThat(auditUpdates(dept)).isEqualTo(1);
    }

    @Test
    @DisplayName("Cây: cảnh báo NO_ACCOUNT / NO_MANAGER_SCOPE và người duyệt hiệu lực lấy từ phòng ban cha")
    void listShowsWarningsAndEffectiveApprover() throws Exception {
        long parent = create(prefix + "-P", null);
        long child = create(prefix + "-C", parent);
        long noAccount = insertEmployee(parent, "ACTIVE");
        long plain = insertEmployee(child, "ACTIVE");
        jdbc.sql("UPDATE hrm.employees SET user_id = :u WHERE id = :id")
                .param("u", testUsers.create("EMPLOYEE").id()).param("id", plain).update();
        long manager = insertEmployee(child, "ACTIVE");
        jdbc.sql("UPDATE hrm.employees SET user_id = :u WHERE id = :id")
                .param("u", testUsers.create("MANAGER").id()).param("id", manager).update();

        putHead(parent, noAccount, version(parent)).andExpect(status().isOk());
        String body = authed(get("/api/v1/departments")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[?(@.id == " + parent + ")].head.warnings[0]"))
                .containsExactly("NO_ACCOUNT");
        // phòng con chưa có trưởng: người duyệt là trưởng phòng cha
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.id == " + child + ")].effectiveApprover.employeeId"))
                .containsExactly((int) noAccount);
        assertThat(JsonPath.<List<Object>>read(body, "$[?(@.id == " + child + ")].head")).containsOnlyNulls();

        putHead(child, plain, version(child))
                .andExpect(jsonPath("$.head.warnings[0]").value("NO_MANAGER_SCOPE"));
        putHead(child, manager, version(child))
                .andExpect(jsonPath("$.head.warnings").isEmpty())
                .andExpect(jsonPath("$.effectiveApprover.employeeId").value(manager))
                .andExpect(jsonPath("$.effectiveApprover.warnings").isEmpty());
        authed(get("/api/v1/departments/" + child))
                .andExpect(jsonPath("$.head.employeeId").value(manager));
    }

    @Test
    @DisplayName("Phòng ban ngừng hoạt động: chỉ định lại đúng trưởng hiện tại là no-op 200; trưởng nghỉ + inactive: 409")
    void inactiveDepartmentSameHeadIsNoOp() throws Exception {
        long dept = create(prefix + "-H", null);
        long emp = insertEmployee(create(prefix + "-O", null), "ACTIVE");
        putHead(dept, emp, version(dept)).andExpect(status().isOk());
        authed(put("/api/v1/departments/" + dept + "/status").contentType(MediaType.APPLICATION_JSON).content("""
                {"active": false, "version": %d}
                """.formatted(version(dept)))).andExpect(status().isOk());
        int v = version(dept);
        putHead(dept, emp, v).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(v));
        long resigned = insertEmployee(create(prefix + "-R", null), "RESIGNED");
        putHead(dept, resigned, v)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HEAD_INACTIVE"));
    }

    @Test
    @DisplayName("Cảnh báo về trưởng chỉ hiện với org:manage: EMPLOYEE nhận danh sách rỗng, HR nhận NO_ACCOUNT")
    void warningsOnlyForManagers() throws Exception {
        long dept = create(prefix + "-W", null);
        long emp = insertEmployee(dept, "ACTIVE");
        putHead(dept, emp, version(dept)).andExpect(status().isOk());
        String employeeToken = login(testUsers.create("EMPLOYEE").email());

        for (String url : List.of("/api/v1/departments", "/api/v1/departments/" + dept)) {
            String path = url.endsWith("departments") ? "$[?(@.id == " + dept + ")].head.warnings[0]"
                    : "$.head.warnings[0]";
            String hr = authed(get(url)).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<Object>read(hr, path)).isIn("NO_ACCOUNT", List.of("NO_ACCOUNT"));
            String emp_ = mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String all = url.endsWith("departments") ? "$[?(@.id == " + dept + ")].head.warnings[*]"
                    : "$.head.warnings[*]";
            assertThat(JsonPath.<List<Object>>read(emp_, all)).isEmpty();
            assertThat(JsonPath.<Object>read(emp_, url.endsWith("departments")
                    ? "$[?(@.id == " + dept + ")].head.employeeId[0]" : "$.head.employeeId")).isNotNull();
        }
    }

    // ================================================================= người duyệt cấp 1 (cho PLT-06)

    @Autowired
    vn.sspack.hrm.org.application.DepartmentHeadResolver resolver;

    @Test
    @DisplayName("Resolver: người gửi ở phòng con duyệt bởi trưởng phòng cha; tự làm trưởng thì leo lên; nghỉ việc bị bỏ qua")
    void firstLevelApprover() throws Exception {
        long grand = create(prefix + "-G", null);
        long parent = create(prefix + "-P", grand);
        long child = create(prefix + "-C", parent);
        long headG = insertEmployee(grand, "ACTIVE");
        long headP = insertEmployee(parent, "ACTIVE");
        long headC = insertEmployee(child, "ACTIVE");
        putHead(grand, headG, version(grand)).andExpect(status().isOk());
        putHead(parent, headP, version(parent)).andExpect(status().isOk());
        putHead(child, headC, version(child)).andExpect(status().isOk());
        long company = companyId();

        // người gửi là trưởng phòng con: duyệt bởi trưởng phòng cha
        assertThat(resolver.firstLevelApprover(company, child, headC)).contains(headP);
        // trưởng phòng con kiêm cả phòng cha: bỏ qua chính mình, leo lên cấp ông
        putHead(parent, headC, version(parent)).andExpect(status().isOk());
        assertThat(resolver.firstLevelApprover(company, child, headC)).contains(headG);
        // trưởng phòng ông cũng là người gửi và không còn ai: rỗng
        putHead(grand, headC, version(grand)).andExpect(status().isOk());
        assertThat(resolver.firstLevelApprover(company, child, headC)).isEmpty();
        // trả lại trưởng phòng cha, rồi cho nghỉ việc: bỏ qua, leo lên (trưởng phòng ông là headC => rỗng)
        putHead(parent, headP, version(parent)).andExpect(status().isOk());
        putHead(grand, headG, version(grand)).andExpect(status().isOk());
        jdbc.sql("UPDATE hrm.employees SET status = 'RESIGNED' WHERE id = :id").param("id", headP).update();
        assertThat(resolver.firstLevelApprover(company, child, headC)).contains(headG);
    }

    @Test
    @DisplayName("Resolver: phòng ban của công ty khác trả về rỗng")
    void firstLevelApproverOtherCompany() throws Exception {
        long dept = create(prefix + "-X", null);
        long emp = insertEmployee(dept, "ACTIVE");
        putHead(dept, emp, version(dept)).andExpect(status().isOk());
        long otherCompany = jdbc.sql("INSERT INTO hrm.companies (code, name) VALUES (:c, 'Công ty khác') RETURNING id")
                .param("c", "OTH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                .query(Long.class).single();
        assertThat(resolver.firstLevelApprover(otherCompany, dept, -1L)).isEmpty();
    }

    // ================================================================= tiện ích

    private ResultActions authed(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + hrToken));
    }

    private long create(String code, Long parentId) throws Exception {
        String body = authed(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "name": "Phòng %s", "parentId": %s}
                """.formatted(code, code, parentId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private int version(long id) {
        return jdbc.sql("SELECT version FROM hrm.departments WHERE id = :id").param("id", id)
                .query(Integer.class).single();
    }

    private String path(long id) {
        return jdbc.sql("SELECT path FROM hrm.departments WHERE id = :id").param("id", id)
                .query(String.class).single();
    }

    private long insertEmployee(long departmentId, String status) {
        return jdbc.sql("""
                        INSERT INTO hrm.employees (company_id, employee_code, full_name, department_id, hire_date, status)
                        SELECT company_id, :code, 'Nhân viên thử', id, DATE '2025-01-02', :status
                        FROM hrm.departments WHERE id = :departmentId
                        RETURNING id
                        """)
                .param("code", "E" + UUID.randomUUID().toString().substring(0, 8))
                .param("departmentId", departmentId)
                .param("status", status)
                .query(Long.class).single();
    }

    private ResultActions putHead(long deptId, Long employeeId, int version) throws Exception {
        return authed(put("/api/v1/departments/" + deptId + "/head").contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\": %s, \"version\": %d}".formatted(employeeId, version)));
    }

    /** headEmployeeId trong cột {@code column} của dòng audit UPDATE thứ {@code nth} (từ 1). */
    private Long auditHead(long deptId, String column, int nth) {
        return jdbc.sql("""
                        SELECT (%s ->> 'headEmployeeId')::bigint FROM hrm.audit_logs
                        WHERE entity_type = 'departments' AND entity_id = :id AND action = 'UPDATE'
                        ORDER BY id OFFSET :skip LIMIT 1
                        """.formatted(column)).param("id", deptId).param("skip", nth - 1)
                .query(Long.class).optional().orElse(null);
    }

    private long companyId() {
        return jdbc.sql("SELECT id FROM hrm.companies WHERE code = 'SSPACK'").query(Long.class).single();
    }

    private int auditUpdates(long deptId) {
        return jdbc.sql("""
                        SELECT count(*) FROM hrm.audit_logs
                        WHERE entity_type = 'departments' AND entity_id = :id AND action = 'UPDATE'
                        """).param("id", deptId).query(Integer.class).single();
    }

    private long insertForeignEmployee() {
        String code = "OTH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        long company = jdbc.sql("INSERT INTO hrm.companies (code, name) VALUES (:c, 'Công ty khác') RETURNING id")
                .param("c", code).query(Long.class).single();
        long dept = jdbc.sql("""
                        INSERT INTO hrm.departments (company_id, code, name, path)
                        VALUES (:company, :c, 'Phòng khác', '/0/') RETURNING id
                        """).param("company", company).param("c", code).query(Long.class).single();
        return jdbc.sql("""
                        INSERT INTO hrm.employees (company_id, employee_code, full_name, department_id, hire_date, status)
                        VALUES (:company, :c, 'Người công ty khác', :dept, DATE '2025-01-02', 'ACTIVE') RETURNING id
                        """).param("company", company).param("c", code).param("dept", dept)
                .query(Long.class).single();
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
