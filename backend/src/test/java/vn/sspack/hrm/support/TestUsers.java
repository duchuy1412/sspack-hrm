package vn.sspack.hrm.support;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Tạo tài khoản thử nghiệm với email duy nhất để các test không ảnh hưởng nhau. */
@Component
public class TestUsers {

    public static final String PASSWORD = "Matkhau@123";

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public TestUsers(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public record Created(long id, String email) {
    }

    public Created create(String... roleCodes) {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@sspack.vn";
        long id = jdbc.sql("""
                        INSERT INTO hrm.users (company_id, email, password_hash)
                        SELECT id, :email, :hash FROM hrm.companies WHERE code = 'SSPACK'
                        RETURNING id
                        """)
                .param("email", email)
                .param("hash", passwordEncoder.encode(PASSWORD))
                .query(Long.class)
                .single();
        for (String role : roleCodes) {
            jdbc.sql("""
                            INSERT INTO hrm.user_roles (user_id, role_id)
                            SELECT :userId, r.id FROM hrm.roles r WHERE r.code = :role
                            """)
                    .param("userId", id)
                    .param("role", role)
                    .update();
        }
        return new Created(id, email);
    }

    public void setStatus(long userId, String status) {
        jdbc.sql("UPDATE hrm.users SET status = :status WHERE id = :id")
                .param("status", status)
                .param("id", userId)
                .update();
    }
}
