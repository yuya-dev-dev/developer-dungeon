package jp.yuya.dev.developerdungeon.app;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface StagePersistence {
    record SavedAttempt(UUID id, String status, long version, long generation, UUID workspaceId, UUID createRequestId,
                        UUID cleanupRequestId, Integer pendingStars, int highestHint, int playerResets,
                        int systemRecoveries, long lastSequence, Integer stars) { }

    SavedAttempt createStarting(UUID attemptId, String stageKey, UUID createRequestId, Instant now);
    SavedAttempt workspaceCreated(UUID attemptId, long expectedVersion, UUID workspaceId);
    SavedAttempt activate(UUID attemptId, long expectedVersion, UUID workspaceId);
    Optional<SavedAttempt> findOpen(String stageKey);
    SavedAttempt increaseHint(UUID attemptId, long expectedVersion, int hint);
    boolean beginCommand(UUID attemptId, long expectedVersion, UUID requestId, long sequence, long generation,
                         String canonicalText, String commandKind, Instant now);
    SavedAttempt finishCommand(UUID attemptId, long expectedVersion, UUID requestId, String resultKind,
                               Integer exitCode, long durationMs, UUID cleanupRequestId, Integer pendingStars);
    SavedAttempt recordRejected(UUID attemptId, long expectedVersion, UUID requestId, long sequence, long generation, String reasonCode, Instant now);
    SavedAttempt prepareSystemRecovery(UUID attemptId, long expectedVersion, UUID cleanupRequestId, UUID createRequestId);
    SavedAttempt beginReset(UUID attemptId, long expectedVersion, UUID cleanupRequestId, UUID createRequestId);
    SavedAttempt beginCreateCleanup(UUID attemptId, long expectedVersion, UUID workspaceId);
    SavedAttempt markCleanupPending(UUID attemptId, long expectedVersion);
    SavedAttempt restartStartingAfterCleanup(UUID attemptId, long expectedVersion);
    SavedAttempt completeReset(UUID attemptId, long expectedVersion, boolean systemRecovery, UUID historyRequestId, Instant now);
    SavedAttempt beginClearing(UUID attemptId, long expectedVersion, UUID cleanupRequestId, int stars);
    SavedAttempt completeClear(UUID attemptId, long expectedVersion, Instant now);
    int highestStars(String stageKey);
}
