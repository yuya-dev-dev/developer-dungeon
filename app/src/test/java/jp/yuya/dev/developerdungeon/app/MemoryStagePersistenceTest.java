package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryStagePersistenceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void duplicateCommandRequestReturnsBeforeCheckingTheNowStaleVersion() {
        MemoryStagePersistence persistence = new MemoryStagePersistence();
        UUID attemptId = UUID.randomUUID();
        StagePersistence.SavedAttempt active = activeAttempt(persistence, attemptId);
        UUID requestId = UUID.randomUUID();

        boolean first = persistence.beginCommand(attemptId, active.version(), requestId, 1, 0,
                "git status", "STATUS", NOW);
        boolean duplicate = persistence.beginCommand(attemptId, active.version(), requestId, 1, 0,
                "git status", "STATUS", NOW);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
    }

    @Test
    void newCommandRequestIsRememberedBeforeTheVersionConflictIsReported() {
        MemoryStagePersistence persistence = new MemoryStagePersistence();
        UUID attemptId = UUID.randomUUID();
        StagePersistence.SavedAttempt active = activeAttempt(persistence, attemptId);
        UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> persistence.beginCommand(attemptId, active.version() - 1, requestId, 1, 0,
                "git status", "STATUS", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attempt persistence conflict");

        // recordRejected intentionally uses the same request-registration order.
        assertThat(persistence.beginCommand(attemptId, active.version(), requestId, 1, 0,
                "git status", "STATUS", NOW)).isFalse();
        assertThat(persistence.findOpen("STAGE-GIT-01")).containsSame(active);
    }

    @Test
    void recoveryForStartingAttemptReturnsBeforeCheckingTheExpectedVersion() {
        MemoryStagePersistence persistence = new MemoryStagePersistence();
        UUID attemptId = UUID.randomUUID();
        StagePersistence.SavedAttempt starting = persistence.createStarting(attemptId, "STAGE-GIT-01",
                UUID.randomUUID(), NOW);

        StagePersistence.SavedAttempt result = persistence.prepareSystemRecovery(attemptId, 99,
                UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).isSameAs(starting);
    }

    @Test
    void completingClearCopiesThePendingStarsIntoTheFinalResult() {
        MemoryStagePersistence persistence = new MemoryStagePersistence();
        UUID attemptId = UUID.randomUUID();
        StagePersistence.SavedAttempt active = activeAttempt(persistence, attemptId);
        StagePersistence.SavedAttempt clearing = persistence.beginClearing(attemptId, active.version(),
                UUID.randomUUID(), 3);

        StagePersistence.SavedAttempt cleared = persistence.completeClear(attemptId, clearing.version(), NOW);

        assertThat(cleared.status()).isEqualTo("CLEARED");
        assertThat(cleared.pendingStars()).isNull();
        assertThat(cleared.stars()).isEqualTo(3);
    }

    private StagePersistence.SavedAttempt activeAttempt(MemoryStagePersistence persistence, UUID attemptId) {
        StagePersistence.SavedAttempt starting = persistence.createStarting(attemptId, "STAGE-GIT-01",
                UUID.randomUUID(), NOW);
        UUID workspaceId = UUID.randomUUID();
        StagePersistence.SavedAttempt workspaceCreated = persistence.workspaceCreated(attemptId,
                starting.version(), workspaceId);
        return persistence.activate(attemptId, workspaceCreated.version(), workspaceId);
    }
}
