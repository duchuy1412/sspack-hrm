package vn.sspack.hrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.sspack.hrm.support.IntegrationTest;

/**
 * Kiểm tra migration Flyway chạy được trên PostgreSQL 16 thật và tạo đúng lược đồ trong docs/schema.sql.
 */
@IntegrationTest
class MigrationIT {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsAreApplied() {
        // Bản ghi đầu tiên có thể là dấu "tạo schema hrm" (không có version) do create-schemas=true.
        List<MigrationInfo> versioned = Arrays.stream(flyway.info().applied())
                .filter(m -> m.getVersion() != null)
                .toList();

        assertThat(versioned).extracting(m -> m.getVersion().getVersion()).contains("1", "2", "3");
        assertThat(versioned).allMatch(m -> m.getState() == MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void schemaHrmContainsAllTablesFromSchemaSql() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'hrm' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(tables).isEqualTo(50);
    }

    @Test
    void referenceDataSeedsSystemRolesWithPermissions() {
        List<String> roles = jdbc.queryForList(
                "SELECT code FROM hrm.roles WHERE is_system ORDER BY code", String.class);
        Integer hrPermissions = jdbc.queryForObject("""
                SELECT count(*) FROM hrm.role_permissions rp JOIN hrm.roles r ON r.id = rp.role_id
                WHERE r.code = 'HR'
                """, Integer.class);
        Integer demoUsers = jdbc.queryForObject(
                "SELECT count(*) FROM hrm.users WHERE email = 'admin@sspack.vn'", Integer.class);

        assertThat(roles).containsExactly("ADMIN", "EMPLOYEE", "HR", "INTERVIEWER", "MANAGER");
        assertThat(hrPermissions).isPositive();
        assertThat(demoUsers).as("tài khoản demo chỉ có ở profile dev").isZero();
    }

    @Test
    void leaveOverlapExclusionConstraintExists() {
        Integer constraints = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = 'hrm' AND c.conname = 'ex_leave_no_overlap' AND c.contype = 'x'
                """, Integer.class);

        assertThat(constraints).isEqualTo(1);
    }
}
