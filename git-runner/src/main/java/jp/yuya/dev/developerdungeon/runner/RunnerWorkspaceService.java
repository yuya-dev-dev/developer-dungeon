package jp.yuya.dev.developerdungeon.runner;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
class RunnerWorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(RunnerWorkspaceService.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration WORKSPACE_TTL = Duration.ofMinutes(15);
    private final ConcurrentHashMap<String, Workspace> workspaces = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> allowedObjects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> revertTargets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkspaceResponse> createdRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CommandResponse> executedRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Workspace> destroyedWorkspaces = new ConcurrentHashMap<>();
    private final Set<String> expiredWorkspaces = ConcurrentHashMap.newKeySet();
    private final DockerGateway docker;
    private final RunnerProperties properties;
    private final RunnerCommandValidator validator;
    private final Clock clock;

    @Autowired
    RunnerWorkspaceService(DockerGateway docker, RunnerProperties properties, RunnerCommandValidator validator, Clock clock) {
        this.docker = docker; this.properties = properties; this.validator = validator; this.clock = clock;
    }
    RunnerWorkspaceService(DockerGateway docker, RunnerProperties properties, RunnerCommandValidator validator) { this(docker, properties, validator, Clock.systemUTC()); }

    @PostConstruct
    void cleanupOrphansOnStartup() { cleanupOrphans(); }

    @Scheduled(fixedDelay = 60_000)
    synchronized void cleanupExpiredWorkspaces() {
        Instant cutoff = clock.instant().minus(WORKSPACE_TTL);
        workspaces.values().stream().filter(workspace -> !workspace.lastActivityAt().isAfter(cutoff)).toList().forEach(workspace -> {
            expiredWorkspaces.add(workspace.workspaceId());
            try { removeWorkspace(workspace); }
            catch (RuntimeException exception) { log.error("Expired challenge container cleanup failed: {}", workspace.workspaceId(), exception); }
        });
    }

    @PreDestroy
    synchronized void cleanupOnShutdown() {
        workspaces.values().forEach(workspace -> {
            try { removeWorkspace(workspace); }
            catch (RuntimeException exception) { log.error("Challenge container cleanup failed during shutdown: {}", workspace.workspaceId(), exception); }
        });
        workspaces.clear();
        allowedObjects.clear();
        revertTargets.clear();
        createdRequests.clear();
        executedRequests.clear();
        destroyedWorkspaces.clear();
        expiredWorkspaces.clear();
        cleanupOrphans();
    }

    synchronized WorkspaceResponse create(WorkspaceRequest request) {
        cleanupExpiredWorkspaces();
        requireStage(request.stageKey());
        requireRequest(request.attemptId(), request.requestId(), request.generation());
        String idempotencyKey = requestKey(request.attemptId(), request.requestId());
        WorkspaceResponse existing = createdRequests.get(idempotencyKey);
        if (existing != null) return existing;
        String workspaceId = UUID.randomUUID().toString();
        String imageId = requiredImageId();
        verifyChallengeImage(imageId);
        var result = docker.run(List.of("run", "-d", "--platform", "linux/amd64", "--read-only", "--network", "none", "--user", "10001:10001",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--pids-limit", "64", "--memory", "256m",
                "--cpus", "0.5", "--tmpfs", "/workspace:rw,nosuid,nodev,noexec,size=64m,mode=0700,uid=10001,gid=10001",
                "--tmpfs", "/tmp:rw,nosuid,nodev,noexec,size=16m,mode=0700,uid=10001,gid=10001",
                "--label", "io.developer-dungeon.project=developer-dungeon",
                "--label", "io.developer-dungeon.owner=git-runner",
                "--label", "io.developer-dungeon.workspace=" + workspaceId, imageId), COMMAND_TIMEOUT);
        if (result.exitCode() != 0) throw new IllegalStateException("container create failed: " + result.stderr());
        String containerId = result.stdout().trim();
        var workspace = new Workspace(workspaceId, request.attemptId(), request.generation(), containerId, clock.instant(), clock.instant());
        workspaces.put(workspaceId, workspace);
        try {
            var copy = docker.run(List.of("exec", containerId, "/bin/cp", "-a", "/opt/fixtures/stage-git-01/.", "/workspace"), COMMAND_TIMEOUT);
            if (copy.exitCode() != 0) throw new IllegalStateException("fixture copy failed: " + copy.stderr());
            var writable = docker.run(List.of("exec", containerId, "/bin/chmod", "-R", "u+rwX", "/workspace"), COMMAND_TIMEOUT);
            if (writable.exitCode() != 0) throw new IllegalStateException("workspace permission setup failed: " + writable.stderr());
            verifyCreatedContainer(workspace, imageId);
            validateWorkspace(workspace);
            RepositorySnapshot initial = snapshot(workspace);
            allowedObjects.put(workspaceId, Set.copyOf(initial.ancestorObjectIds()));
            revertTargets.put(workspaceId, initial.headObjectId());
            WorkspaceResponse response = new WorkspaceResponse(workspaceId, request.generation(), initial);
            createdRequests.put(idempotencyKey, response);
            return response;
        } catch (RuntimeException exception) {
            try { removeWorkspace(workspace); }
            catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); log.error("Challenge container cleanup failed after create error: {}", workspaceId, cleanupFailure); }
            throw exception;
        }
    }

    synchronized CommandResponse execute(ExecuteRequest request) {
        cleanupExpiredWorkspaces();
        validator.validate(request.command());
        requireRequest(request.attemptId(), request.requestId(), request.generation());
        Workspace workspace = workspace(request.workspaceId(), request.attemptId(), request.generation());
        String idempotencyKey = requestKey(workspace.workspaceId(), request.requestId());
        CommandResponse existing = executedRequests.get(idempotencyKey);
        if (existing != null) { touch(workspace); return existing; }
        validateAllowedObject(request.command(), workspace);
        long started = System.nanoTime();
        try {
            var result = docker.run(gitArguments(workspace.containerId(), request.command().kind(), request.command().objectIds()), COMMAND_TIMEOUT);
            CommandResponse response = new CommandResponse(result.exitCode(), result.stdout(), result.stderr(), result.outputTruncated(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), snapshot(workspace));
            executedRequests.put(idempotencyKey, response);
            touch(workspace);
            return response;
        } catch (RuntimeException exception) {
            try { removeWorkspace(workspace); }
            catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            throw exception;
        }
    }

    synchronized void destroy(DestroyRequest request) {
        cleanupExpiredWorkspaces();
        requireRequest(request.attemptId(), request.requestId(), request.generation());
        String idempotencyKey = requestKey(request.workspaceId(), request.requestId());
        Workspace workspace = workspaces.get(request.workspaceId());
        if (workspace == null) {
            Workspace destroyed = destroyedWorkspaces.get(idempotencyKey);
            if (destroyed != null && destroyed.attemptId().equals(request.attemptId()) && destroyed.generation() == request.generation()) return;
            throw new IllegalArgumentException("unknown workspace");
        }
        if (!workspace.attemptId().equals(request.attemptId()) || workspace.generation() != request.generation()) {
            throw new IllegalArgumentException("unknown workspace");
        }
        removeContainer(workspace.containerId());
        workspaces.remove(workspace.workspaceId(), workspace);
        allowedObjects.remove(workspace.workspaceId());
        revertTargets.remove(workspace.workspaceId());
        expiredWorkspaces.remove(workspace.workspaceId());
        destroyedWorkspaces.put(idempotencyKey, workspace);
    }

    synchronized RepositorySnapshot snapshotFor(String workspaceId, String attemptId, long generation) {
        cleanupExpiredWorkspaces();
        Workspace workspace = workspace(workspaceId, attemptId, generation);
        RepositorySnapshot snapshot = snapshot(workspace);
        touch(workspace);
        return snapshot;
    }

    private RepositorySnapshot snapshot(Workspace workspace) {
        String head = gitOutput(workspace.containerId(), List.of("rev-parse", "HEAD")).trim();
        String tree = gitOutput(workspace.containerId(), List.of("rev-parse", "HEAD^{tree}")).trim();
        String parents = gitOutput(workspace.containerId(), List.of("show", "-s", "--format=%P", "HEAD")).trim();
        String firstParentTree = parents.isBlank() ? "" : gitOutput(workspace.containerId(), List.of("rev-parse", "HEAD^1^{tree}")).trim();
        String ancestors = gitOutput(workspace.containerId(), List.of("rev-list", "HEAD")).trim();
        String status = gitOutput(workspace.containerId(), List.of("status", "--porcelain=v1")).trim();
        var parentList = parents.isBlank() ? List.<String>of() : List.of(parents.split(" "));
        var ancestorList = ancestors.isBlank() ? List.<String>of() : List.of(ancestors.split("\\R"));
        boolean reverting = docker.run(List.of("exec", workspace.containerId(), "/usr/bin/test", "-e", "/workspace/.git/REVERT_HEAD"), COMMAND_TIMEOUT).exitCode() == 0;
        return new RepositorySnapshot(head, tree, firstParentTree, parentList, status.isEmpty(), reverting, ancestorList);
    }

    private String gitOutput(String containerId, List<String> gitArguments) {
        var arguments = gitPrefix(containerId);
        arguments.add("-C"); arguments.add("/workspace");
        arguments.add("-c"); arguments.add("core.hooksPath=/opt/empty-hooks");
        arguments.add("-c"); arguments.add("core.attributesFile=/dev/null");
        arguments.add("-c"); arguments.add("credential.helper=");
        arguments.add("-c"); arguments.add("core.pager=cat");
        arguments.add("-c"); arguments.add("core.editor=:");
        arguments.add("-c"); arguments.add("protocol.file.allow=never");
        arguments.add("-c"); arguments.add("user.name=Developer Dungeon Player");
        arguments.add("-c"); arguments.add("user.email=player@developer-dungeon.invalid");
        arguments.addAll(gitArguments);
        var result = docker.run(arguments, COMMAND_TIMEOUT);
        if (result.exitCode() != 0) throw new IllegalStateException("snapshot failed: " + result.stderr());
        return result.stdout();
    }

    private List<String> gitArguments(String containerId, CommandKind kind, List<String> ids) {
        var arguments = gitPrefix(containerId);
        arguments.add("-C"); arguments.add("/workspace");
        arguments.add("-c"); arguments.add("core.hooksPath=/opt/empty-hooks"); arguments.add("-c"); arguments.add("core.attributesFile=/dev/null");
        arguments.add("-c"); arguments.add("credential.helper="); arguments.add("-c"); arguments.add("core.pager=cat"); arguments.add("-c"); arguments.add("core.editor=:"); arguments.add("-c"); arguments.add("protocol.file.allow=never");
        arguments.add("-c"); arguments.add("user.name=Developer Dungeon Player"); arguments.add("-c"); arguments.add("user.email=player@developer-dungeon.invalid");
        switch (kind) {
            case STATUS -> arguments.addAll(List.of("status", "--short"));
            case LOG_ONELINE -> arguments.addAll(List.of("log", "--oneline", "--no-decorate", "--abbrev=12"));
            case SHOW -> { arguments.addAll(List.of("show", "--no-ext-diff", "--no-textconv")); arguments.add(ids.getFirst()); }
            case REVERT_NO_EDIT -> { arguments.addAll(List.of("revert", "--no-edit")); arguments.add(ids.getFirst()); }
        }
        return arguments;
    }
    private ArrayList<String> gitPrefix(String containerId) {
        var arguments = new ArrayList<String>();
        arguments.addAll(List.of("exec", "--env", "HOME=/tmp/empty-home", "--env", "XDG_CONFIG_HOME=/tmp/empty-xdg",
                "--env", "GIT_CONFIG_NOSYSTEM=1", "--env", "GIT_CONFIG_GLOBAL=/dev/null", "--env", "GIT_TERMINAL_PROMPT=0",
                "--env", "GIT_ASKPASS=/bin/false", containerId, "/usr/bin/git"));
        return arguments;
    }

    private Workspace workspace(String workspaceId, String attemptId, long generation) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null || expiredWorkspaces.contains(workspaceId) || !workspace.attemptId().equals(attemptId) || workspace.generation() != generation) {
            throw new IllegalArgumentException("unknown workspace");
        }
        return workspace;
    }
    private void requireRequest(String attemptId, String requestId, long generation) {
        String uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        if (attemptId == null || !attemptId.matches(uuid) || requestId == null || !requestId.matches(uuid) || generation < 0) {
            throw new IllegalArgumentException("invalid request identity");
        }
    }
    private String requestKey(String scope, String requestId) { return scope + ":" + requestId; }
    private void requireStage(String stageKey) { if (!"STAGE-GIT-01".equals(stageKey)) throw new IllegalArgumentException("unknown stage"); }
    private String requiredImageId() {
        if (properties.imageId() == null || !properties.imageId().matches("sha256:[0-9a-f]{64}")) throw new IllegalStateException("challenge image ID is not configured");
        return properties.imageId();
    }
    private void verifyChallengeImage(String imageId) {
        if (properties.imageFingerprint() == null || !properties.imageFingerprint().matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("challenge image fingerprint is not configured");
        }
        var inspect = docker.run(List.of("image", "inspect", "--format", "{{.Id}}|{{.Os}}/{{.Architecture}}|{{json .Config.Labels}}", imageId), COMMAND_TIMEOUT);
        String output = inspect.stdout().trim();
        String expectedPrefix = imageId + "|linux/amd64|";
        String expectedLabel = "\"io.developer-dungeon.challenge.build-input-sha256\":\"" + properties.imageFingerprint() + "\"";
        if (inspect.exitCode() != 0 || !output.startsWith(expectedPrefix) || !output.substring(expectedPrefix.length()).contains(expectedLabel)) {
            log.warn("Challenge image verification failed: inspectExitCode={}, identityMatches={}", inspect.exitCode(), output.startsWith(expectedPrefix));
            throw new IllegalStateException("challenge image verification failed");
        }
    }
    private void verifyCreatedContainer(Workspace workspace, String imageId) {
        var inspect = docker.run(List.of("container", "inspect", "--format", "{{.Image}}|{{json .Config.Labels}}", workspace.containerId()), COMMAND_TIMEOUT);
        String output = inspect.stdout().trim();
        String expectedPrefix = imageId + "|";
        String labels = output.startsWith(expectedPrefix) ? output.substring(expectedPrefix.length()) : "";
        if (inspect.exitCode() != 0 || !output.startsWith(expectedPrefix)
                || !labels.contains("\"io.developer-dungeon.project\":\"developer-dungeon\"")
                || !labels.contains("\"io.developer-dungeon.owner\":\"git-runner\"")) {
            throw new IllegalStateException("created challenge container verification failed");
        }
    }
    private void validateAllowedObject(jp.yuya.dev.developerdungeon.contract.GitCommand command, Workspace workspace) {
        if (command.objectIds().isEmpty()) return;
        if (!allowedObjects.getOrDefault(workspace.workspaceId(), Set.of()).contains(command.objectIds().getFirst())) {
            throw new IllegalArgumentException("object is not allowed for this stage");
        }
        if (command.kind() == CommandKind.REVERT_NO_EDIT && !command.objectIds().getFirst().equals(revertTargets.get(workspace.workspaceId()))) {
            throw new IllegalArgumentException("only the stage's accidental commit can be reverted");
        }
    }
    private void validateWorkspace(Workspace workspace) {
        var configArguments = gitPrefix(workspace.containerId());
        configArguments.addAll(List.of("-C", "/workspace", "config", "--local", "--list"));
        var config = docker.run(configArguments, COMMAND_TIMEOUT);
        if (config.exitCode() != 0) throw new IllegalStateException("fixture config validation failed");
        Set<String> allowed = Set.of("core.repositoryformatversion=0", "core.filemode=true", "core.bare=false", "core.logallrefupdates=true");
        for (String line : config.stdout().split("\\R")) {
            if (!line.isBlank() && !allowed.contains(line)) throw new IllegalStateException("fixture local config is not allowed");
        }
        for (String forbidden : List.of("/workspace/.gitattributes", "/workspace/.git/info/attributes", "/workspace/.gitmodules")) {
            if (docker.run(List.of("exec", workspace.containerId(), "/usr/bin/test", "!", "-e", forbidden), COMMAND_TIMEOUT).exitCode() != 0) {
                throw new IllegalStateException("fixture contains forbidden file");
            }
        }
        var links = docker.run(List.of("exec", workspace.containerId(), "/usr/bin/find", "/workspace", "-type", "l", "-print", "-quit"), COMMAND_TIMEOUT);
        if (links.exitCode() != 0 || !links.stdout().isBlank()) throw new IllegalStateException("fixture contains symlink");
        var hooks = docker.run(List.of("exec", workspace.containerId(), "/usr/bin/find", "/workspace/.git/hooks", "-type", "f", "-print", "-quit"), COMMAND_TIMEOUT);
        if (hooks.exitCode() != 0 || !hooks.stdout().isBlank()) throw new IllegalStateException("fixture contains hook");
    }
    private void removeContainer(String containerId) {
        var result = docker.run(List.of("rm", "-f", containerId), COMMAND_TIMEOUT);
        if (result.exitCode() != 0) throw new IllegalStateException("container cleanup failed: " + result.stderr());
    }
    private void removeWorkspace(Workspace workspace) {
        removeContainer(workspace.containerId());
        workspaces.remove(workspace.workspaceId(), workspace);
        allowedObjects.remove(workspace.workspaceId());
        revertTargets.remove(workspace.workspaceId());
        expiredWorkspaces.remove(workspace.workspaceId());
        executedRequests.keySet().removeIf(key -> key.startsWith(workspace.workspaceId() + ":"));
        createdRequests.entrySet().removeIf(entry -> entry.getValue().workspaceId().equals(workspace.workspaceId()));
        destroyedWorkspaces.entrySet().removeIf(entry -> entry.getValue().workspaceId().equals(workspace.workspaceId()));
    }
    private void touch(Workspace workspace) {
        workspaces.replace(workspace.workspaceId(), workspace,
                new Workspace(workspace.workspaceId(), workspace.attemptId(), workspace.generation(), workspace.containerId(), workspace.createdAt(), clock.instant()));
    }
    private void cleanupOrphans() {
        var result = docker.run(List.of("ps", "-aq", "--filter", "label=io.developer-dungeon.project=developer-dungeon",
                "--filter", "label=io.developer-dungeon.owner=git-runner"), COMMAND_TIMEOUT);
        if (result.exitCode() != 0) return;
        for (String id : result.stdout().split("\\R")) {
            if (id.matches("[0-9a-f]{12,64}")) {
                try { removeContainer(id); }
                catch (RuntimeException exception) { log.error("Orphaned challenge container cleanup failed: {}", id, exception); }
            }
        }
    }
    private record Workspace(String workspaceId, String attemptId, long generation, String containerId, Instant createdAt, Instant lastActivityAt) { }
}
