package jp.yuya.dev.developerdungeon.runner;

import java.time.Instant;
import java.util.List;

interface ContainerOwnershipLedger {
    enum State { INTENT, ACTIVE, DELETED }
    record Entry(State state, Instant createdAt, int confirmationCount, String attemptId, String workspaceId,
                 long generation, String imageId, String fingerprint, String containerId, String cleanupRequestId) { }

    List<Entry> entries();
    void restore(Entry entry);
    void recordIntent(String attemptId, String workspaceId, long generation, String imageId, String fingerprint);
    void attachContainer(String workspaceId, String containerId);
    void markDeleted(String workspaceId, String cleanupRequestId);
    void remove(String workspaceId);
    void incrementConfirmation(String workspaceId);
    boolean wasDeleted(String attemptId, String workspaceId, long generation, String cleanupRequestId);
    void pruneDeletedBeforeGeneration(String attemptId, long generation);
}
