package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayAppliesInitialSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);
        assertThat(tables).contains("users", "accounts", "orders", "trades", "positions");

        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }
}
