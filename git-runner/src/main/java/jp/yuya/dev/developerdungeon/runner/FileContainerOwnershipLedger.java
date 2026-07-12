package jp.yuya.dev.developerdungeon.runner;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
final class FileContainerOwnershipLedger implements ContainerOwnershipLedger {
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern FIELD_PATTERN = Pattern.compile("\"([a-zA-Z]+)\":(null|\"[^\"]*\"|-?\\d+)");
    private static final Set<String> ENTRY_FIELDS = Set.of("state", "createdAt", "confirmationCount", "attemptId", "workspaceId",
            "generation", "imageId", "fingerprint", "containerId", "cleanupRequestId");
    private final Path ledgerPath;
    private final Path lockPath;
    private final MemoryContainerOwnershipLedger delegate;
    private final Runnable beforePersist;
    private FileChannel lockChannel;
    private FileLock lock;

    @Autowired
    FileContainerOwnershipLedger(RunnerProperties properties, Clock clock) { this(properties, clock, () -> { }); }
    FileContainerOwnershipLedger(RunnerProperties properties, Clock clock, Runnable beforePersist) {
        if (properties.ledgerPath() == null || properties.ledgerPath().isBlank()) throw new IllegalStateException("container ownership ledger path is not configured");
        ledgerPath = Path.of(properties.ledgerPath()).toAbsolutePath().normalize();
        if (!ledgerPath.isAbsolute() || !ledgerPath.getFileName().toString().equals("runner-owned-containers.json")) throw new IllegalStateException("container ownership ledger path is invalid");
        lockPath = ledgerPath.resolveSibling("runner-owned-containers.lock"); delegate = new MemoryContainerOwnershipLedger(clock); this.beforePersist = beforePersist;
    }

    @PostConstruct synchronized void initialize() {
        try {
            Files.createDirectories(ledgerPath.getParent());
            lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try { lock = lockChannel.tryLock(); } catch (OverlappingFileLockException exception) { throw new IllegalStateException("another Git Runner owns the container ledger", exception); }
            if (lock == null) throw new IllegalStateException("another Git Runner owns the container ledger");
            if (Files.exists(ledgerPath)) {
                Set<String> workspaceIds = new HashSet<>();
                Set<String> containerIds = new HashSet<>();
                for (Entry entry : parseLedger(Files.readString(ledgerPath, StandardCharsets.UTF_8))) {
                    if (!workspaceIds.add(entry.workspaceId()) || (entry.containerId() != null && !containerIds.add(entry.containerId()))) {
                        throw new IllegalStateException("container ownership ledger is ambiguous");
                    }
                    loadEntry(entry);
                }
            } else persist();
        } catch (IOException | RuntimeException exception) { closeQuietly(); throw new IllegalStateException("container ownership ledger initialization failed", exception); }
    }
    private void loadEntry(Entry entry) {
        requireEntry(entry);
        delegate.restore(entry);
    }
    private void requireEntry(Entry entry) {
        String uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        if (entry == null || entry.state() == null || entry.createdAt() == null || entry.confirmationCount() < 0 || entry.attemptId() == null || !entry.attemptId().matches(uuid)
                || entry.workspaceId() == null || !entry.workspaceId().matches(uuid) || entry.generation() < 0 || entry.imageId() == null || !entry.imageId().matches("sha256:[0-9a-f]{64}")
                || entry.fingerprint() == null || !entry.fingerprint().matches("[0-9a-f]{64}") || (entry.containerId() != null && !entry.containerId().matches("[0-9a-f]{12,64}"))
                || (entry.state() == State.INTENT && (entry.containerId() != null || entry.cleanupRequestId() != null))
                || (entry.state() == State.ACTIVE && (entry.containerId() == null || entry.cleanupRequestId() != null))
                || (entry.state() == State.DELETED && (entry.containerId() == null || entry.cleanupRequestId() == null || !entry.cleanupRequestId().matches(uuid)))) {
            throw new IllegalStateException("container ownership ledger is invalid");
        }
    }
    private synchronized void persist() {
        try {
            beforePersist.run();
            Path temporary = Files.createTempFile(ledgerPath.getParent(), ".runner-owned-containers-", ".tmp");
            try {
                Files.writeString(temporary, formatLedger(delegate.entries()), StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                try { Files.move(temporary, ledgerPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException exception) { throw new IllegalStateException("atomic ledger replacement is unavailable", exception); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) { throw new IllegalStateException("container ownership ledger update failed", exception); }
    }
    @Override public synchronized List<Entry> entries() { return delegate.entries(); }
    @Override public synchronized void restore(Entry entry) { update(() -> delegate.restore(entry)); }
    @Override public synchronized void recordIntent(String a, String w, long g, String i, String f) { update(() -> delegate.recordIntent(a,w,g,i,f)); }
    @Override public synchronized void attachContainer(String w, String c) { update(() -> delegate.attachContainer(w,c)); }
    @Override public synchronized void markDeleted(String w, String r) { update(() -> delegate.markDeleted(w,r)); }
    @Override public synchronized void remove(String w) { update(() -> delegate.remove(w)); }
    @Override public synchronized void incrementConfirmation(String w) { update(() -> delegate.incrementConfirmation(w)); }
    @Override public synchronized boolean wasDeleted(String a, String w, long g, String r) { return delegate.wasDeleted(a,w,g,r); }
    @Override public synchronized void pruneDeletedBeforeGeneration(String a, long g) { update(() -> delegate.pruneDeletedBeforeGeneration(a,g)); }
    @PreDestroy synchronized void close() { closeQuietly(); }
    private void closeQuietly() { try { if (lock != null) lock.close(); } catch (IOException ignored) { } try { if (lockChannel != null) lockChannel.close(); } catch (IOException ignored) { } }

    private void update(Runnable mutation) {
        List<Entry> previous = delegate.entries();
        mutation.run();
        try {
            persist();
        } catch (RuntimeException exception) {
            delegate.entries().forEach(entry -> delegate.remove(entry.workspaceId()));
            previous.forEach(delegate::restore);
            throw exception;
        }
    }

    private List<Entry> parseLedger(String content) {
        String trimmed = content.strip();
        if ("[]".equals(trimmed)) return List.of();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) throw new IllegalStateException("container ownership ledger is invalid");
        var parsed = new java.util.ArrayList<Entry>();
        var matcher = OBJECT_PATTERN.matcher(trimmed);
        int position = 1;
        while (matcher.find()) {
            String between = trimmed.substring(position, matcher.start()).strip();
            if (!between.isEmpty() && !",".equals(between)) throw new IllegalStateException("container ownership ledger is invalid");
            parsed.add(parseEntry(matcher.group(1)));
            position = matcher.end();
        }
        String tail = trimmed.substring(position, trimmed.length() - 1).strip();
        if (!tail.isEmpty()) throw new IllegalStateException("container ownership ledger is invalid");
        return parsed;
    }
    private Entry parseEntry(String object) {
        Map<String, String> fields = new LinkedHashMap<>();
        var matcher = FIELD_PATTERN.matcher(object);
        int position = 0;
        while (matcher.find()) {
            String between = object.substring(position, matcher.start()).strip();
            if (!between.isEmpty() && !",".equals(between)) throw new IllegalStateException("container ownership ledger is invalid");
            String name = matcher.group(1);
            if (!ENTRY_FIELDS.contains(name) || fields.containsKey(name)) {
                throw new IllegalStateException("container ownership ledger is invalid");
            }
            fields.put(name, unquote(matcher.group(2)));
            position = matcher.end();
        }
        String tail = object.substring(position).strip();
        if (!tail.isEmpty()) throw new IllegalStateException("container ownership ledger is invalid");
        if (!fields.keySet().equals(ENTRY_FIELDS)) throw new IllegalStateException("container ownership ledger is invalid");
        return new Entry(State.valueOf(require(fields, "state")), Instant.parse(require(fields, "createdAt")),
                Integer.parseInt(require(fields, "confirmationCount")), require(fields, "attemptId"), require(fields, "workspaceId"),
                Long.parseLong(require(fields, "generation")), require(fields, "imageId"), require(fields, "fingerprint"),
                fields.get("containerId"), fields.get("cleanupRequestId"));
    }
    private String require(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) throw new IllegalStateException("container ownership ledger is invalid");
        return value;
    }
    private String unquote(String value) {
        if ("null".equals(value)) return null;
        if (!value.startsWith("\"")) return value;
        return value.substring(1, value.length() - 1);
    }
    private String formatLedger(List<Entry> entries) {
        var builder = new StringBuilder("[");
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) builder.append(',');
            Entry entry = entries.get(index);
            builder.append('{')
                    .append(field("state", entry.state().name())).append(',')
                    .append(field("createdAt", entry.createdAt().toString())).append(',')
                    .append("\"confirmationCount\":").append(entry.confirmationCount()).append(',')
                    .append(field("attemptId", entry.attemptId())).append(',')
                    .append(field("workspaceId", entry.workspaceId())).append(',')
                    .append("\"generation\":").append(entry.generation()).append(',')
                    .append(field("imageId", entry.imageId())).append(',')
                    .append(field("fingerprint", entry.fingerprint())).append(',')
                    .append(field("containerId", entry.containerId())).append(',')
                    .append(field("cleanupRequestId", entry.cleanupRequestId()))
                    .append('}');
        }
        return builder.append(']').append(System.lineSeparator()).toString();
    }
    private String field(String name, String value) {
        return "\"" + name + "\":" + (value == null ? "null" : "\"" + value + "\"");
    }
}
