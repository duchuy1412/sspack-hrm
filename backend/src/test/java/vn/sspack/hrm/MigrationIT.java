package vn.sspack.hrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.sspack.hrm.support.PostgresTestcontainersConfig;

/**
 * Kiểm tra migration Flyway chạy được trên PostgreSQL 16 thật và tạo đúng lược đồ trong docs/schema.sql.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfig.class)
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

        assertThat(versioned).extracting(m -> m.getVersion().getVersion()).contains("1");
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
    void leaveOverlapExclusionConstraintExists() {
        Integer constraints = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = 'hrm' AND c.conname = 'ex_leave_no_overlap' AND c.contype = 'x'
                """, Integer.class);

        assertThat(constraints).isEqualTo(1);
    }
}
