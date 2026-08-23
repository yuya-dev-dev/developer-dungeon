package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MemoryContainerOwnershipLedgerTest {
    private static final String ATTEMPT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_ATTEMPT_ID = "22222222-2222-2222-2222-222222222222";
    private static final String IMAGE_ID = "sha256:" + "a".repeat(64);
    private static final String FINGERPRINT = "b".repeat(64);

    @Test
    void deletedTombstoneMatchesIdentityAndGenerationWithoutComparingCleanupRequest() {
        MemoryContainerOwnershipLedger ledger = ledger();
        String workspaceId = "33333333-3333-3333-3333-333333333333";
        ledger.recordIntent(ATTEMPT_ID, workspaceId, 4, IMAGE_ID, FINGERPRINT);
        ledger.attachContainer(workspaceId, "c".repeat(64));
        ledger.markDeleted(workspaceId, "44444444-4444-4444-4444-444444444444");

        assertThat(ledger.wasDeleted(ATTEMPT_ID, workspaceId, 4,
                "55555555-5555-5555-5555-555555555555")).isTrue();
        assertThat(ledger.wasDeleted(OTHER_ATTEMPT_ID, workspaceId, 4,
                "44444444-4444-4444-4444-444444444444")).isFalse();
        assertThat(ledger.wasDeleted(ATTEMPT_ID, workspaceId, 5,
                "44444444-4444-4444-4444-444444444444")).isFalse();
    }

    @Test
    void pruningDeletedEntriesRemovesOnlyOlderGenerationsForTheSameAttempt() {
        MemoryContainerOwnershipLedger ledger = ledger();
        addDeleted(ledger, ATTEMPT_ID, "33333333-3333-3333-3333-333333333331", 1);
        addDeleted(ledger, ATTEMPT_ID, "33333333-3333-3333-3333-333333333332", 2);
        addDeleted(ledger, OTHER_ATTEMPT_ID, "33333333-3333-3333-3333-333333333333", 1);

        ledger.pruneDeletedBeforeGeneration(ATTEMPT_ID, 2);

        assertThat(ledger.entries()).extracting(ContainerOwnershipLedger.Entry::workspaceId)
                .containsExactly("33333333-3333-3333-3333-333333333332",
                        "33333333-3333-3333-3333-333333333333");
    }

    private MemoryContainerOwnershipLedger ledger() {
        return new MemoryContainerOwnershipLedger(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private void addDeleted(MemoryContainerOwnershipLedger ledger, String attemptId, String workspaceId,
                            long generation) {
        ledger.recordIntent(attemptId, workspaceId, generation, IMAGE_ID, FINGERPRINT);
        ledger.markDeleted(workspaceId, "44444444-4444-4444-4444-444444444444");
    }
}
