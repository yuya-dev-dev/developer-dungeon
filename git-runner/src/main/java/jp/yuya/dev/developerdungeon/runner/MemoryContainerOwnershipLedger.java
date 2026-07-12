package jp.yuya.dev.developerdungeon.runner;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class MemoryContainerOwnershipLedger implements ContainerOwnershipLedger {
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final Clock clock;
    MemoryContainerOwnershipLedger(Clock clock) { this.clock = clock; }
    @Override public synchronized List<Entry> entries() { return List.copyOf(entries.values()); }
    @Override public synchronized void restore(Entry entry) { entries.put(entry.workspaceId(), entry); }
    @Override public synchronized void recordIntent(String attemptId, String workspaceId, long generation, String imageId, String fingerprint) {
        entries.put(workspaceId, new Entry(State.INTENT, clock.instant(), 0, attemptId, workspaceId, generation, imageId, fingerprint, null, null));
    }
    @Override public synchronized void attachContainer(String workspaceId, String containerId) { replace(workspaceId, entry -> new Entry(State.ACTIVE, entry.createdAt(), entry.confirmationCount(), entry.attemptId(), entry.workspaceId(), entry.generation(), entry.imageId(), entry.fingerprint(), containerId, null)); }
    @Override public synchronized void markDeleted(String workspaceId, String cleanupRequestId) { replace(workspaceId, entry -> new Entry(State.DELETED, entry.createdAt(), entry.confirmationCount(), entry.attemptId(), entry.workspaceId(), entry.generation(), entry.imageId(), entry.fingerprint(), entry.containerId(), cleanupRequestId)); }
    @Override public synchronized void remove(String workspaceId) { entries.remove(workspaceId); }
    @Override public synchronized void incrementConfirmation(String workspaceId) { replace(workspaceId, entry -> new Entry(entry.state(), entry.createdAt(), entry.confirmationCount() + 1, entry.attemptId(), entry.workspaceId(), entry.generation(), entry.imageId(), entry.fingerprint(), entry.containerId(), entry.cleanupRequestId())); }
    @Override public synchronized boolean wasDeleted(String attemptId, String workspaceId, long generation, String cleanupRequestId) {
        Entry entry = entries.get(workspaceId); return entry != null && entry.state() == State.DELETED && entry.attemptId().equals(attemptId) && entry.generation() == generation;
    }
    @Override public synchronized void pruneDeletedBeforeGeneration(String attemptId, long generation) { new ArrayList<>(entries.values()).stream().filter(e -> e.state() == State.DELETED && e.attemptId().equals(attemptId) && e.generation() < generation).map(Entry::workspaceId).forEach(entries::remove); }
    private void replace(String workspaceId, java.util.function.Function<Entry, Entry> function) { Entry entry = entries.get(workspaceId); if (entry == null) throw new IllegalStateException("ownership entry is missing"); entries.put(workspaceId, function.apply(entry)); }
}
