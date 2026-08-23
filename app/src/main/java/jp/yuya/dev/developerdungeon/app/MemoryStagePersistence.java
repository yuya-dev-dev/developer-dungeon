package jp.yuya.dev.developerdungeon.app;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

final class MemoryStagePersistence implements StagePersistence {
    private final Map<UUID, SavedAttempt> attempts = new LinkedHashMap<>();
    private final Map<UUID, Boolean> requests = new LinkedHashMap<>();

    @Override
    public SavedAttempt createStarting(UUID attemptId, String stageKey, UUID createRequestId, Instant now) {
        if (findOpen(stageKey).isPresent()) {
            throw new IllegalStateException("attempt persistence conflict");
        }
        return put(new SavedAttempt(attemptId, "STARTING", 0, 0, null, createRequestId, createRequestId,
                null, 0, 0, 0, 0, null));
    }

    @Override
    public SavedAttempt workspaceCreated(UUID attemptId, long expectedVersion, UUID workspaceId) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, "STARTING", workspaceId, attempt.createRequestId(), null, null,
                        attempt.generation(), attempt.highestHint(), attempt.playerResets(),
                        attempt.systemRecoveries(), attempt.lastSequence(), attempt.stars()));
    }

    @Override
    public SavedAttempt activate(UUID attemptId, long expectedVersion, UUID workspaceId) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, "ACTIVE", workspaceId, null, null, null, attempt.generation(),
                        attempt.highestHint(), attempt.playerResets(), attempt.systemRecoveries(),
                        attempt.lastSequence(), attempt.stars()));
    }

    @Override
    public Optional<SavedAttempt> findOpen(String stageKey) {
        return attempts.values().stream()
                .filter(attempt -> !terminal(attempt.status()))
                .findFirst();
    }

    @Override
    public SavedAttempt increaseHint(UUID attemptId, long expectedVersion, int hint) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, attempt.status(), attempt.workspaceId(), attempt.createRequestId(),
                        attempt.cleanupRequestId(), attempt.pendingStars(), attempt.generation(),
                        Math.max(attempt.highestHint(), hint), attempt.playerResets(), attempt.systemRecoveries(),
                        attempt.lastSequence(), attempt.stars()));
    }

    @Override
    public boolean beginCommand(UUID attemptId, long expectedVersion, UUID requestId, long sequence,
                                long generation, String canonicalText, String commandKind, Instant now) {
        if (requests.putIfAbsent(requestId, Boolean.TRUE) != null) {
            return false;
        }
        change(attemptId, expectedVersion,
                attempt -> copy(attempt, "EXECUTING", attempt.workspaceId(), attempt.createRequestId(),
                        attempt.cleanupRequestId(), attempt.pendingStars(), attempt.generation(),
                        attempt.highestHint(), attempt.playerResets(), attempt.systemRecoveries(), sequence,
                        attempt.stars()));
        return true;
    }

    @Override
    public SavedAttempt finishCommand(UUID attemptId, long expectedVersion, UUID requestId, String resultKind,
                                      Integer exitCode, long durationMs, UUID cleanupRequestId,
                                      Integer pendingStars) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, pendingStars == null ? "ACTIVE" : "CLEARING", attempt.workspaceId(),
                        pendingStars == null ? attempt.createRequestId() : null, cleanupRequestId, pendingStars,
                        attempt.generation(), attempt.highestHint(), attempt.playerResets(),
                        attempt.systemRecoveries(), attempt.lastSequence(), attempt.stars()));
    }

    @Override
    public SavedAttempt recordRejected(UUID attemptId, long expectedVersion, UUID requestId, long sequence,
                                       long generation, String reasonCode, Instant now) {
        if (requests.putIfAbsent(requestId, Boolean.TRUE) != null) {
            return required(attemptId);
        }
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, attempt.status(), attempt.workspaceId(), attempt.createRequestId(),
                        attempt.cleanupRequestId(), attempt.pendingStars(), attempt.generation(),
                        attempt.highestHint(), attempt.playerResets(), attempt.systemRecoveries(), sequence,
                        attempt.stars()));
    }

    @Override
    public SavedAttempt prepareSystemRecovery(UUID attemptId, long expectedVersion, UUID cleanupRequestId,
                                               UUID createRequestId) {
        SavedAttempt attempt = required(attemptId);
        if (!attempt.status().equals("ACTIVE") && !attempt.status().equals("EXECUTING")) {
            return attempt;
        }
        return change(attemptId, expectedVersion,
                current -> copy(current, "RESETTING", current.workspaceId(), createRequestId, cleanupRequestId,
                        null, current.generation(), current.highestHint(), current.playerResets(),
                        current.systemRecoveries(), current.lastSequence(), current.stars()));
    }

    @Override
    public SavedAttempt beginReset(UUID attemptId, long expectedVersion, UUID cleanupRequestId,
                                   UUID createRequestId) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, "RESETTING", attempt.workspaceId(), createRequestId, cleanupRequestId,
                        null, attempt.generation(), attempt.highestHint(), attempt.playerResets(),
                        attempt.systemRecoveries(), attempt.lastSequence(), attempt.stars()));
    }

    @Override
    public SavedAttempt beginCreateCleanup(UUID attemptId, long expectedVersion, UUID workspaceId) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, "CLEANUP_PENDING", workspaceId, attempt.createRequestId(),
                        attempt.cleanupRequestId(), null, attempt.generation(), attempt.highestHint(),
                        attempt.playerResets(), attempt.systemRecoveries(), attempt.lastSequence(),
                        attempt.stars()));
    }

    @Override
    public SavedAttempt markCleanupPending(UUID attemptId, long expectedVersion) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, "CLEANUP_PENDING", attempt.workspaceId(), attempt.createRequestId(),
                        attempt.cleanupRequestId(), attempt.pendingStars(), attempt.generation(),
                        attempt.highestHint(), attempt.playerResets(), attempt.systemRecoveries(),
                        attempt.lastSequence(), attempt.stars()));
    }

    @Override
    public SavedAttempt restartStartingAfterCleanup(UUID attemptId, long expectedVersion) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, "STARTING", null, attempt.createRequestId(), attempt.createRequestId(),
                        null, attempt.generation(), attempt.highestHint(), attempt.playerResets(),
                        attempt.systemRecoveries(), attempt.lastSequence(), attempt.stars()));
    }

    @Override
    public SavedAttempt completeReset(UUID attemptId, long expectedVersion, boolean systemRecovery,
                                      UUID historyRequestId, Instant now) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, "STARTING", null, attempt.createRequestId(), attempt.createRequestId(),
                        null, attempt.generation() + 1, attempt.highestHint(),
                        attempt.playerResets() + (systemRecovery ? 0 : 1),
                        attempt.systemRecoveries() + (systemRecovery ? 1 : 0), attempt.lastSequence() + 1,
                        attempt.stars()));
    }

    @Override
    public SavedAttempt beginClearing(UUID attemptId, long expectedVersion, UUID cleanupRequestId, int stars) {
        return change(attemptId, expectedVersion,
                attempt -> copy(attempt, "CLEARING", attempt.workspaceId(), null, cleanupRequestId, stars,
                        attempt.generation(), attempt.highestHint(), attempt.playerResets(),
                        attempt.systemRecoveries(), attempt.lastSequence(), attempt.stars()));
    }

    @Override
    public SavedAttempt completeClear(UUID attemptId, long expectedVersion, Instant now) {
        SavedAttempt attempt = required(attemptId);
        return change(attemptId, expectedVersion,
                current -> copy(current, "CLEARED", null, null, null, null, current.generation(),
                        current.highestHint(), current.playerResets(), current.systemRecoveries(),
                        current.lastSequence(), attempt.pendingStars()));
    }

    @Override
    public int highestStars(String stageKey) {
        return attempts.values().stream()
                .filter(attempt -> attempt.status().equals("CLEARED") && attempt.stars() != null)
                .mapToInt(SavedAttempt::stars)
                .max()
                .orElse(0);
    }

    private SavedAttempt change(UUID attemptId, long expectedVersion,
                                 Function<SavedAttempt, SavedAttempt> transform) {
        SavedAttempt attempt = required(attemptId);
        if (attempt.version() != expectedVersion) {
            throw new IllegalStateException("attempt persistence conflict");
        }
        SavedAttempt transformed = transform.apply(attempt);
        SavedAttempt next = new SavedAttempt(transformed.id(), transformed.status(), expectedVersion + 1,
                transformed.generation(), transformed.workspaceId(), transformed.createRequestId(),
                transformed.cleanupRequestId(), transformed.pendingStars(), transformed.highestHint(),
                transformed.playerResets(), transformed.systemRecoveries(), transformed.lastSequence(),
                transformed.stars());
        return put(next);
    }

    private SavedAttempt copy(SavedAttempt attempt, String status, UUID workspaceId, UUID createRequestId,
                              UUID cleanupRequestId, Integer pendingStars, long generation, int highestHint,
                              int playerResets, int systemRecoveries, long lastSequence, Integer stars) {
        return new SavedAttempt(attempt.id(), status, attempt.version(), generation, workspaceId, createRequestId,
                cleanupRequestId, pendingStars, highestHint, playerResets, systemRecoveries, lastSequence, stars);
    }

    private SavedAttempt put(SavedAttempt attempt) {
        attempts.put(attempt.id(), attempt);
        return attempt;
    }

    private SavedAttempt required(UUID attemptId) {
        SavedAttempt attempt = attempts.get(attemptId);
        if (attempt == null) {
            throw new IllegalStateException("attempt persistence is missing");
        }
        return attempt;
    }

    private boolean terminal(String status) {
        return status.equals("CLEARED") || status.equals("FAILED") || status.equals("EXPIRED")
                || status.equals("ABANDONED");
    }
}
