package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileContainerOwnershipLedgerTest {
    private static final String ATTEMPT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String WORKSPACE_ID = "22222222-2222-2222-2222-222222222222";
    private static final String CLEANUP_REQUEST_ID = "33333333-3333-3333-3333-333333333333";
    private static final String IMAGE_ID = "sha256:" + "a".repeat(64);
    private static final String FINGERPRINT = "b".repeat(64);
    private static final String CONTAINER_ID = "c".repeat(64);

    @Test
    void restoresDeletionTombstoneAndOriginalCreationTime(@TempDir Path directory) {
        Path path = directory.resolve("runner-owned-containers.json");
        Instant createdAt = Instant.parse("2026-07-12T00:00:00Z");
        FileContainerOwnershipLedger first = ledger(path, createdAt);
        first.initialize();
        first.recordIntent(ATTEMPT_ID, WORKSPACE_ID, 7, IMAGE_ID, FINGERPRINT);
        first.attachContainer(WORKSPACE_ID, CONTAINER_ID);
        first.markDeleted(WORKSPACE_ID, CLEANUP_REQUEST_ID);
        first.close();

        FileContainerOwnershipLedger restored = ledger(path, createdAt.plusSeconds(60));
        restored.initialize();
        try {
            assertThat(restored.entries()).singleElement().satisfies(entry -> {
                assertThat(entry.state()).isEqualTo(ContainerOwnershipLedger.State.DELETED);
                assertThat(entry.createdAt()).isEqualTo(createdAt);
                assertThat(entry.containerId()).isEqualTo(CONTAINER_ID);
                assertThat(entry.cleanupRequestId()).isEqualTo(CLEANUP_REQUEST_ID);
            });
            assertThat(restored.wasDeleted(ATTEMPT_ID, WORKSPACE_ID, 7, CLEANUP_REQUEST_ID)).isTrue();
        } finally {
            restored.close();
        }
    }

    @Test
    void rejectsDuplicateFieldsWithoutRewritingLedger(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("runner-owned-containers.json");
        writeIntent(path);
        String corrupted = Files.readString(path).replaceFirst("\\{", "{\"state\":\"INTENT\",");
        Files.writeString(path, corrupted);

        FileContainerOwnershipLedger ledger = ledger(path, Instant.parse("2026-07-12T00:01:00Z"));
        assertThatThrownBy(ledger::initialize).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(path)).isEqualTo(corrupted);
    }

    @Test
    void rejectsDuplicateFieldWhenFirstValueIsNull(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("runner-owned-containers.json");
        writeIntent(path);
        String corrupted = Files.readString(path).replace("\"containerId\":null", "\"containerId\":null,\"containerId\":null");
        Files.writeString(path, corrupted);

        FileContainerOwnershipLedger ledger = ledger(path, Instant.parse("2026-07-12T00:01:00Z"));
        assertThatThrownBy(ledger::initialize).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(path)).isEqualTo(corrupted);
    }

    @Test
    void rejectsDuplicateWorkspaceEntriesWithoutRewritingLedger(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("runner-owned-containers.json");
        writeIntent(path);
        String object = Files.readString(path).strip();
        object = object.substring(1, object.length() - 1);
        String corrupted = "[" + object + "," + object + "]\n";
        Files.writeString(path, corrupted);

        FileContainerOwnershipLedger ledger = ledger(path, Instant.parse("2026-07-12T00:01:00Z"));
        assertThatThrownBy(ledger::initialize).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(path)).isEqualTo(corrupted);
    }

    @Test
    void rejectsSecondOwnerBeforeReadingOrChangingLedger(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("runner-owned-containers.json");
        FileContainerOwnershipLedger first = ledger(path, Instant.parse("2026-07-12T00:00:00Z"));
        first.initialize();
        String original = Files.readString(path);
        try {
            FileContainerOwnershipLedger second = ledger(path, Instant.parse("2026-07-12T00:01:00Z"));
            assertThatThrownBy(second::initialize).isInstanceOf(IllegalStateException.class);
            assertThat(Files.readString(path)).isEqualTo(original);
        } finally {
            first.close();
        }
    }

    @Test
    void rollsBackRecordIntentWhenPersistenceFails(@TempDir Path directory) {
        Path path = directory.resolve("runner-owned-containers.json");
        AtomicBoolean fail = new AtomicBoolean();
        FileContainerOwnershipLedger ledger = ledger(path, Instant.parse("2026-07-12T00:00:00Z"), fail);
        ledger.initialize();
        fail.set(true);

        assertThatThrownBy(() -> ledger.recordIntent(ATTEMPT_ID, WORKSPACE_ID, 7, IMAGE_ID, FINGERPRINT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledger.entries()).isEmpty();
        ledger.close();

        FileContainerOwnershipLedger restored = ledger(path, Instant.parse("2026-07-12T00:01:00Z"));
        restored.initialize();
        try { assertThat(restored.entries()).isEmpty(); } finally { restored.close(); }
    }

    @Test
    void rollsBackDeletionTombstoneWhenPersistenceFails(@TempDir Path directory) {
        Path path = directory.resolve("runner-owned-containers.json");
        AtomicBoolean fail = new AtomicBoolean();
        FileContainerOwnershipLedger ledger = ledger(path, Instant.parse("2026-07-12T00:00:00Z"), fail);
        ledger.initialize();
        ledger.recordIntent(ATTEMPT_ID, WORKSPACE_ID, 7, IMAGE_ID, FINGERPRINT);
        ledger.attachContainer(WORKSPACE_ID, CONTAINER_ID);
        fail.set(true);

        assertThatThrownBy(() -> ledger.markDeleted(WORKSPACE_ID, CLEANUP_REQUEST_ID)).isInstanceOf(IllegalStateException.class);
        assertThat(ledger.entries()).singleElement().satisfies(entry -> assertThat(entry.state()).isEqualTo(ContainerOwnershipLedger.State.ACTIVE));
        assertThat(ledger.wasDeleted(ATTEMPT_ID, WORKSPACE_ID, 7, CLEANUP_REQUEST_ID)).isFalse();
        ledger.close();

        FileContainerOwnershipLedger restored = ledger(path, Instant.parse("2026-07-12T00:01:00Z"));
        restored.initialize();
        try {
            assertThat(restored.entries()).singleElement().satisfies(entry -> assertThat(entry.state()).isEqualTo(ContainerOwnershipLedger.State.ACTIVE));
            assertThat(restored.wasDeleted(ATTEMPT_ID, WORKSPACE_ID, 7, CLEANUP_REQUEST_ID)).isFalse();
        } finally { restored.close(); }
    }

    private void writeIntent(Path path) {
        FileContainerOwnershipLedger ledger = ledger(path, Instant.parse("2026-07-12T00:00:00Z"));
        ledger.initialize();
        ledger.recordIntent(ATTEMPT_ID, WORKSPACE_ID, 7, IMAGE_ID, FINGERPRINT);
        ledger.close();
    }

    private FileContainerOwnershipLedger ledger(Path path, Instant now) {
        RunnerProperties properties = new RunnerProperties("token", IMAGE_ID, FINGERPRINT, "docker", path.toString());
        return new FileContainerOwnershipLedger(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
    private FileContainerOwnershipLedger ledger(Path path, Instant now, AtomicBoolean fail) {
        RunnerProperties properties = new RunnerProperties("token", IMAGE_ID, FINGERPRINT, "docker", path.toString());
        return new FileContainerOwnershipLedger(properties, Clock.fixed(now, ZoneOffset.UTC), () -> {
            if (fail.get()) throw new IllegalStateException("injected persistence failure");
        });
    }
}
