package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class JdbcStagePersistenceIT {
    private static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18.4-alpine3.23@sha256:865a3cfdac164ea2608bea36342a9bb5c11e99b5aa8ed9b83b662185e556e178")
            .asCompatibleSubstituteFor("postgres");
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(POSTGRES).withDatabaseName("developer_dungeon").withUsername("postgres").withPassword("postgres");
    private static DataSource appDataSource;

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE developer_dungeon_migrator LOGIN PASSWORD 'migrator-password'");
            statement.execute("CREATE ROLE developer_dungeon_app LOGIN PASSWORD 'app-password'");
            statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            statement.execute("CREATE SCHEMA developer_dungeon AUTHORIZATION developer_dungeon_migrator");
            statement.execute("ALTER ROLE developer_dungeon_app IN DATABASE developer_dungeon SET search_path = developer_dungeon");
        }
        String migrationUrl = database.getJdbcUrl() + "?currentSchema=developer_dungeon";
        Flyway.configure().dataSource(migrationUrl, "developer_dungeon_migrator", "migrator-password").defaultSchema("developer_dungeon").schemas("developer_dungeon").load().migrate();
        Flyway.configure().dataSource(migrationUrl, "developer_dungeon_migrator", "migrator-password").defaultSchema("developer_dungeon").schemas("developer_dungeon").load().migrate();
        appDataSource = dataSource(migrationUrl, "developer_dungeon_app", "app-password");
    }

    @Test
    void persistsLifecycleConstraintsAndLeastPrivilege() throws Exception {
        JdbcStagePersistence store = store();
        UUID attemptId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        assertThatThrownBy(() -> new JdbcTemplate(appDataSource).update("INSERT INTO stage_attempt(id,stage_key,status,current_generation,started_at) VALUES (?,?, 'ACTIVE',0,?)",
                UUID.randomUUID(), "STAGE-GIT-01", java.sql.Timestamp.from(now))).isInstanceOf(RuntimeException.class);
        var starting = store.createStarting(attemptId, "STAGE-GIT-01", UUID.randomUUID(), now);
        assertThatThrownBy(() -> store.createStarting(UUID.randomUUID(), "STAGE-GIT-01", UUID.randomUUID(), now)).isInstanceOf(RuntimeException.class);

        var created = store.workspaceCreated(attemptId, starting.version(), UUID.randomUUID());
        var active = store.activate(attemptId, created.version(), created.workspaceId());
        assertThat(active.createRequestId()).isNull();
        assertThat(active.cleanupRequestId()).isNull();
        UUID commandRequest = UUID.randomUUID();
        assertThat(store.beginCommand(attemptId, active.version(), commandRequest, 1, active.generation(), "STATUS", "STATUS", now)).isTrue();
        var afterCommand = store.finishCommand(attemptId, active.version() + 1, commandRequest, "SUCCEEDED", 0, 12, null, null);
        var afterRejected = store.recordRejected(attemptId, afterCommand.version(), UUID.randomUUID(), 2, afterCommand.generation(), "INVALID_SYNTAX", now);
        assertThat(new JdbcTemplate(appDataSource).queryForObject("SELECT entered_text FROM command_history WHERE result_kind='REJECTED'", String.class)).isNull();
        assertThatThrownBy(() -> store.increaseHint(attemptId, afterCommand.version(), 1)).isInstanceOf(IllegalStateException.class);

        var resetting = store.beginReset(attemptId, afterRejected.version(), UUID.randomUUID(), UUID.randomUUID());
        var restarting = store.completeReset(attemptId, resetting.version(), false, UUID.randomUUID(), now);
        assertThat(restarting.generation()).isEqualTo(1);
        assertThat(restarting.playerResets()).isEqualTo(1);
        assertThat(restarting.systemRecoveries()).isZero();
        var recreated = store.workspaceCreated(attemptId, restarting.version(), UUID.randomUUID());
        var activeAgain = store.activate(attemptId, recreated.version(), recreated.workspaceId());
        var clearing = store.beginClearing(attemptId, activeAgain.version(), UUID.randomUUID(), 3);
        var cleared = store.completeClear(attemptId, clearing.version(), now);
        assertThat(cleared.status()).isEqualTo("CLEARED");
        assertThat(store.highestStars("STAGE-GIT-01")).isEqualTo(3);

        try (Connection connection = appDataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE app_must_not_create(id integer)"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void persistsEveryCurrentCommandKindAndRejectsUnknownKind() {
        JdbcStagePersistence store = store();
        UUID attemptId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        var training = store.createStarting(UUID.randomUUID(), "TRAINING-GIT-01", UUID.randomUUID(), now);
        assertThat(new JdbcTemplate(appDataSource).queryForObject("SELECT stage_key FROM stage_attempt WHERE id=?",
                String.class, training.id())).isEqualTo("TRAINING-GIT-01");
        var starting = store.createStarting(attemptId, "STAGE-GIT-02", UUID.randomUUID(), now);
        var created = store.workspaceCreated(attemptId, starting.version(), UUID.randomUUID());
        var active = store.activate(attemptId, created.version(), created.workspaceId());
        long version = active.version();
        long sequence = 1;

        for (CommandKind kind : CommandKind.values()) {
            UUID requestId = UUID.randomUUID();
            assertThat(store.beginCommand(attemptId, version, requestId, sequence, active.generation(), kind.name(), kind.name(), now)).isTrue();
            var afterCommand = store.finishCommand(attemptId, version + 1, requestId, "SUCCEEDED", 0, 1, null, null);
            version = afterCommand.version();
            sequence++;
        }

        long invalidSequence = sequence;
        assertThatThrownBy(() -> new JdbcTemplate(appDataSource).update(
                "INSERT INTO command_history(attempt_id,request_id,sequence_no,workspace_generation,entered_text,command_kind,result_kind,duration_ms,executed_at) VALUES (?,?,?,?,?,?, 'SUCCEEDED',?,?)",
                attemptId, UUID.randomUUID(), invalidSequence, active.generation(), "UNKNOWN", "NOT_A_COMMAND", 1, java.sql.Timestamp.from(now)))
                .isInstanceOf(RuntimeException.class);
    }

    private JdbcStagePersistence store() {
        JdbcTemplate jdbc = new JdbcTemplate(appDataSource);
        return new JdbcStagePersistence(jdbc, new TransactionTemplate(new DataSourceTransactionManager(appDataSource)));
    }
    private static DataSource dataSource(String url, String user, String password) {
        SimpleDriverDataSource source = new SimpleDriverDataSource();
        source.setDriverClass(org.postgresql.Driver.class); source.setUrl(url); source.setUsername(user); source.setPassword(password); return source;
    }
}
