package jp.yuya.dev.developerdungeon.app.javalearning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import javax.sql.DataSource;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProgressStatus;
import jp.yuya.dev.developerdungeon.app.javalearning.persistence.JdbcJavaProgressRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class JdbcJavaProgressRepositoryIT {
    private static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18.4-alpine3.23@sha256:865a3cfdac164ea2608bea36342a9bb5c11e99b5aa8ed9b83b662185e556e178")
            .asCompatibleSubstituteFor("postgres");
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(POSTGRES)
            .withDatabaseName("developer_dungeon").withUsername("postgres").withPassword("postgres");
    private static DataSource appDataSource;

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE developer_dungeon_migrator LOGIN PASSWORD 'migrator-password'");
            statement.execute("CREATE ROLE developer_dungeon_app LOGIN PASSWORD 'app-password'");
            statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            statement.execute("CREATE SCHEMA developer_dungeon AUTHORIZATION developer_dungeon_migrator");
            statement.execute("ALTER ROLE developer_dungeon_app IN DATABASE developer_dungeon SET search_path = developer_dungeon");
        }
        String url = database.getJdbcUrl() + "?currentSchema=developer_dungeon";
        Flyway.configure().dataSource(url, "developer_dungeon_migrator", "migrator-password")
                .defaultSchema("developer_dungeon").schemas("developer_dungeon").load().migrate();
        appDataSource = dataSource(url, "developer_dungeon_app", "app-password");
    }

    @Test
    void upsertsOnlyAllowedProgressStatesWithAppRole() {
        JdbcJavaProgressRepository repository = new JdbcJavaProgressRepository(new JdbcTemplate(appDataSource));
        repository.save("JAVA-LIBRARY-BEGINNER", JavaProgressStatus.IN_PROGRESS);
        JdbcTemplate jdbc = new JdbcTemplate(appDataSource);
        jdbc.update("UPDATE java_problem_progress SET updated_at=TIMESTAMPTZ '2000-01-01 00:00:00+00' WHERE problem_key='JAVA-LIBRARY-BEGINNER'");
        repository.save("JAVA-LIBRARY-BEGINNER", JavaProgressStatus.IN_PROGRESS);

        assertThat(repository.findAll()).containsEntry("JAVA-LIBRARY-BEGINNER", JavaProgressStatus.IN_PROGRESS);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM java_problem_progress WHERE problem_key='JAVA-LIBRARY-BEGINNER'", java.time.OffsetDateTime.class))
                .isAfter(java.time.OffsetDateTime.parse("2000-01-01T00:00:00Z"));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO java_problem_progress(problem_key,status,updated_at) VALUES ('INVALID','UNKNOWN',CURRENT_TIMESTAMP)"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM java_problem_progress WHERE problem_key='JAVA-LIBRARY-BEGINNER'"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.execute("DROP TABLE java_problem_progress"))
                .isInstanceOf(RuntimeException.class);
    }

    private static DataSource dataSource(String url, String user, String password) {
        SimpleDriverDataSource source = new SimpleDriverDataSource();
        source.setDriverClass(org.postgresql.Driver.class); source.setUrl(url); source.setUsername(user); source.setPassword(password);
        return source;
    }
}
