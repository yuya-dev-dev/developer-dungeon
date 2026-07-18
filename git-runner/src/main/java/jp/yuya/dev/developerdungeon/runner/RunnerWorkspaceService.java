package jp.yuya.dev.developerdungeon.runner;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.CharBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.FileContentResponse;
import jp.yuya.dev.developerdungeon.contract.ReadFileRequest;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.StageFileKey;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import jp.yuya.dev.developerdungeon.contract.WriteFileRequest;
import jp.yuya.dev.developerdungeon.contract.WriteFileResponse;
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
    private static final int MAX_CLEANUP_ATTEMPTS = 3;
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String CONTAINER_ID_PATTERN = "[0-9a-f]{12,64}";
    private static final String STAGE_FOUR_PATH = "src/main/resources/messages.properties";
    private static final String STAGE_FOUR_MAIN_BLOB = "a6306bacd230ac74aaf017cde7717bc3eb83684c";
    private static final String STAGE_FOUR_MAIN_TREE = "63ec3ef493c5b54618798e50fe8d2e58bc40a4a9";
    private static final String STAGE_FOUR_FEATURE_TREE = "e4d8a76dfb74d699e48a7437d60811202ba7face";
    private static final String STAGE_FIVE_C0 = "4b03c129e4d5b2bfe41fb2afd208b13dab7824a1";
    private static final String STAGE_FIVE_C1 = "39194dda957695ace62387ecdc5f77fcd5ee81ea";
    private static final String STAGE_FIVE_C1_TREE = "cbc2826a3bd49e4947b67c020e61dd5e4ca7adb3";
    private final ConcurrentHashMap<String, Workspace> workspaces = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> allowedObjects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StageTargets> stageTargets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkspaceResponse> createdRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CommandResponse> executedRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FileContentResponse> readFileRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WriteFileResponse> writeFileRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Workspace> destroyedWorkspaces = new ConcurrentHashMap<>();
    private final Set<String> expiredWorkspaces = ConcurrentHashMap.newKeySet();
    private final Set<String> cleanupPending = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> cleanupAttempts = new ConcurrentHashMap<>();
    private final DockerGateway docker;
    private final RunnerProperties properties;
    private final RunnerCommandValidator validator;
    private final Clock clock;
    private final ContainerOwnershipLedger ledger;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final AtomicBoolean degraded = new AtomicBoolean();

    @Autowired
    RunnerWorkspaceService(DockerGateway docker, RunnerProperties properties, RunnerCommandValidator validator, Clock clock, ContainerOwnershipLedger ledger) {
        this.docker = docker; this.properties = properties; this.validator = validator; this.clock = clock; this.ledger = ledger;
    }
    RunnerWorkspaceService(DockerGateway docker, RunnerProperties properties, RunnerCommandValidator validator, Clock clock) { this(docker, properties, validator, clock, new MemoryContainerOwnershipLedger(clock)); }
    RunnerWorkspaceService(DockerGateway docker, RunnerProperties properties, RunnerCommandValidator validator) { this(docker, properties, validator, Clock.systemUTC()); }

    @PostConstruct
    void cleanupOrphansOnStartup() {
        try { cleanupStartupOrphans(); }
        catch (RuntimeException exception) {
            degraded.set(true);
            log.warn("Startup challenge container recovery is incomplete");
        }
    }

    @Scheduled(fixedDelay = 60_000)
    synchronized void cleanupExpiredWorkspaces() {
        Instant cutoff = clock.instant().minus(WORKSPACE_TTL);
        workspaces.values().stream().filter(workspace -> !workspace.lastActivityAt().isAfter(cutoff)).toList().forEach(workspace -> {
            expiredWorkspaces.add(workspace.workspaceId());
            tryCleanup(workspace, UUID.randomUUID().toString(), "expired");
        });
        cleanupLedgerOrphans(cutoff);
    }

    @PreDestroy
    synchronized void cleanupOnShutdown() {
        workspaces.values().forEach(workspace -> {
            try { removeWorkspace(workspace, UUID.randomUUID().toString()); }
            catch (RuntimeException exception) { log.error("Challenge container cleanup failed during shutdown: {}", workspace.workspaceId(), exception); }
        });
        workspaces.clear();
        allowedObjects.clear();
        stageTargets.clear();
        createdRequests.clear();
        executedRequests.clear();
        readFileRequests.clear();
        writeFileRequests.clear();
        destroyedWorkspaces.clear();
        expiredWorkspaces.clear();
        cleanupPending.clear();
        cleanupAttempts.clear();
    }

    synchronized WorkspaceResponse create(WorkspaceRequest request) {
        requireOperational();
        cleanupExpiredWorkspaces();
        requireNoCleanupPending();
        requireStage(request.stageKey());
        requireRequest(request.attemptId(), request.requestId(), request.generation());
        String idempotencyKey = requestKey(request.attemptId(), request.requestId());
        WorkspaceResponse existing = createdRequests.get(idempotencyKey);
        if (existing != null) return existing;
        String workspaceId = UUID.randomUUID().toString();
        String imageId = requiredImageId();
        verifyChallengeImage(imageId);
        try { ledger.recordIntent(request.attemptId(), workspaceId, request.generation(), imageId, properties.imageFingerprint()); }
        catch (RuntimeException exception) { degraded.set(true); throw exception; }
        String containerId = null;
        Workspace workspace = null;
        try {
            var result = docker.run(List.of("run", "-d", "--platform", "linux/amd64", "--read-only", "--network", "none", "--user", "10001:10001",
                    "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--pids-limit", "64", "--memory", "256m",
                    "--cpus", "0.5", "--tmpfs", "/workspace:rw,nosuid,nodev,noexec,size=64m,mode=0700,uid=10001,gid=10001",
                    "--tmpfs", "/tmp:rw,nosuid,nodev,noexec,size=16m,mode=0700,uid=10001,gid=10001",
                    "--label", "io.developer-dungeon.project=developer-dungeon",
                    "--label", "io.developer-dungeon.owner=git-runner",
                    "--label", "io.developer-dungeon.attempt=" + request.attemptId(),
                    "--label", "io.developer-dungeon.workspace=" + workspaceId,
                    "--label", "io.developer-dungeon.challenge.build-input-sha256=" + properties.imageFingerprint(), imageId), COMMAND_TIMEOUT);
            if (result.exitCode() != 0) throw new IllegalStateException("container create failed");
            containerId = result.stdout().trim();
            if (!containerId.matches(CONTAINER_ID_PATTERN)) throw new IllegalStateException("container create returned invalid identity");
            ledger.attachContainer(workspaceId, containerId);
            workspace = new Workspace(workspaceId, request.attemptId(), request.generation(), request.stageKey(), containerId, clock.instant(), clock.instant());
            workspaces.put(workspaceId, workspace);
            var copy = docker.run(List.of("exec", containerId, "/bin/cp", "-a", fixturePath(request.stageKey()) + "/.", "/workspace"), COMMAND_TIMEOUT);
            if (copy.exitCode() != 0) throw new IllegalStateException("fixture copy failed");
            var writable = docker.run(List.of("exec", containerId, "/bin/chmod", "-R", "u+rwX", "/workspace"), COMMAND_TIMEOUT);
            if (writable.exitCode() != 0) throw new IllegalStateException("workspace permission setup failed");
            verifyCreatedContainer(workspace, imageId);
            validateWorkspace(workspace);
            RepositorySnapshot initial = snapshot(workspace);
            StageTargets targets = captureStageTargets(request.stageKey(), initial);
            allowedObjects.put(workspaceId, targets.allowedObjects());
            stageTargets.put(workspaceId, targets);
            WorkspaceResponse response = new WorkspaceResponse(workspaceId, request.generation(), initial);
            createdRequests.put(idempotencyKey, response);
            ledger.pruneDeletedBeforeGeneration(request.attemptId(), request.generation());
            return response;
        } catch (RuntimeException exception) {
            if (workspace == null) {
                recoverUnpublishedCreate(request, workspaceId, imageId, containerId, exception);
            } else {
                try { removeFailedCreate(workspace); }
                catch (RuntimeException cleanupFailure) {
                    recordCleanupFailure(workspace, "create");
                    exception.addSuppressed(cleanupFailure);
                    log.warn("Challenge container cleanup failed after create error: workspaceId={}", workspaceId);
                }
            }
            throw exception;
        }
    }

    synchronized CommandResponse execute(ExecuteRequest request) {
        requireOperational();
        cleanupExpiredWorkspaces();
        validator.validate(request.command());
        requireRequest(request.attemptId(), request.requestId(), request.generation());
        Workspace workspace = workspace(request.workspaceId(), request.attemptId(), request.generation());
        String idempotencyKey = requestKey(workspace.workspaceId(), request.requestId());
        CommandResponse existing = executedRequests.get(idempotencyKey);
        if (existing != null) { touch(workspace); return existing; }
        validateAllowedObject(request.command(), workspace);
        validateStageCommand(workspace, request.command());
        long started = System.nanoTime();
        try {
            var result = docker.run(gitArguments(workspace.containerId(), request.command()), COMMAND_TIMEOUT);
            CommandResponse response = new CommandResponse(result.exitCode(), result.stdout(), result.stderr(), result.outputTruncated(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), snapshot(workspace));
            executedRequests.put(idempotencyKey, response);
            touch(workspace);
            return response;
        } catch (RuntimeException exception) {
            cleanupPending.add(workspace.workspaceId());
            try { removeWorkspace(workspace, request.requestId()); }
            catch (RuntimeException cleanupFailure) { recordCleanupFailure(workspace, "execute"); exception.addSuppressed(cleanupFailure); }
            throw exception;
        }
    }

    synchronized FileContentResponse readFile(ReadFileRequest request) {
        requireOperational();
        cleanupExpiredWorkspaces();
        requireRequest(request.attemptId(), request.requestId(), request.generation());
        Workspace workspace = workspace(request.workspaceId(), request.attemptId(), request.generation());
        requireStageFourFile(workspace, request.fileKey());
        String idempotencyKey = requestKey("read:" + workspace.workspaceId(), request.requestId());
        FileContentResponse existing = readFileRequests.get(idempotencyKey);
        if (existing != null) { touch(workspace); return existing; }
        try {
            String content = readStageFourFile(workspace);
            FileContentResponse response = new FileContentResponse(content, versionToken(validatedEditorBytes(content, false)));
            readFileRequests.put(idempotencyKey, response);
            touch(workspace);
            return response;
        } catch (RuntimeException exception) {
            invalidateWorkspaceAfterFileFailure(workspace, request.requestId(), exception);
            throw exception;
        }
    }

    synchronized WriteFileResponse writeFile(WriteFileRequest request) {
        requireOperational();
        cleanupExpiredWorkspaces();
        requireRequest(request.attemptId(), request.requestId(), request.generation());
        Workspace workspace = workspace(request.workspaceId(), request.attemptId(), request.generation());
        requireStageFourFile(workspace, request.fileKey());
        if (request.versionToken() == null || !request.versionToken().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("file version is invalid");
        }
        NormalizedEditorContent normalized = normalizeEditorContent(request.content());
        String idempotencyKey = requestKey("write:" + workspace.workspaceId(), request.requestId());
        WriteFileResponse existing = writeFileRequests.get(idempotencyKey);
        if (existing != null) { touch(workspace); return existing; }

        RepositorySnapshot before;
        try { before = snapshot(workspace); }
        catch (RuntimeException exception) {
            invalidateWorkspaceAfterFileFailure(workspace, request.requestId(), exception);
            throw exception;
        }
        String current;
        try {
            validateStageFourEditState(before);
            current = readStageFourFile(workspace);
        }
        catch (RuntimeException exception) {
            invalidateWorkspaceAfterFileFailure(workspace, request.requestId(), exception);
            throw exception;
        }
        String currentToken = versionToken(validatedEditorBytes(current, false));
        if (!MessageDigest.isEqual(currentToken.getBytes(StandardCharsets.US_ASCII), request.versionToken().getBytes(StandardCharsets.US_ASCII))) {
            WriteFileResponse response = new WriteFileResponse(false, currentToken, before);
            writeFileRequests.put(idempotencyKey, response);
            touch(workspace);
            return response;
        }

        try {
            var result = docker.run(List.of("exec", "-i", workspace.containerId(), "/opt/image-rootfs/stage-four-file", "write"),
                    COMMAND_TIMEOUT, normalized.bytes());
            if (result.exitCode() != 0 || result.outputTruncated()) throw new IllegalStateException("stage file write failed");
            String written = readStageFourFile(workspace);
            byte[] writtenBytes = validatedEditorBytes(written, false);
            if (!MessageDigest.isEqual(writtenBytes, normalized.bytes())) throw new IllegalStateException("stage file write verification failed");
            WriteFileResponse response = new WriteFileResponse(true, versionToken(writtenBytes), snapshot(workspace));
            writeFileRequests.put(idempotencyKey, response);
            touch(workspace);
            return response;
        } catch (RuntimeException exception) {
            invalidateWorkspaceAfterFileFailure(workspace, request.requestId(), exception);
            throw exception;
        }
    }

    synchronized void destroy(DestroyRequest request) {
        cleanupExpiredWorkspaces();
        requireRequest(request.attemptId(), request.requestId(), request.generation());
        String idempotencyKey = requestKey(request.workspaceId(), request.requestId());
        if (ledger.wasDeleted(request.attemptId(), request.workspaceId(), request.generation(), request.requestId())) return;
        Workspace workspace = workspaces.get(request.workspaceId());
        if (workspace == null) {
            Workspace destroyed = destroyedWorkspaces.get(idempotencyKey);
            if (destroyed != null && destroyed.attemptId().equals(request.attemptId()) && destroyed.generation() == request.generation()) return;
            throw new IllegalArgumentException("unknown workspace");
        }
        if (!workspace.attemptId().equals(request.attemptId()) || workspace.generation() != request.generation()) {
            throw new IllegalArgumentException("unknown workspace");
        }
        cleanupPending.add(workspace.workspaceId());
        try { removeWorkspace(workspace, request.requestId()); destroyedWorkspaces.put(idempotencyKey, workspace); }
        catch (RuntimeException exception) { recordCleanupFailure(workspace, "destroy"); throw exception; }
    }

    synchronized RepositorySnapshot snapshotFor(String workspaceId, String attemptId, long generation) {
        requireOperational();
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
        boolean reverting = stateFileExists(workspace, "REVERT_HEAD");
        boolean cherryPicking = stateFileExists(workspace, "CHERRY_PICK_HEAD");
        boolean merging = stateFileExists(workspace, "MERGE_HEAD");
        boolean rebasing = stateFileExists(workspace, "rebase-merge") || stateFileExists(workspace, "rebase-apply");
        String currentBranch = gitOutput(workspace.containerId(), List.of("branch", "--show-current")).trim();
        String profileTip = "";
        String notificationTip = "";
        RepositorySnapshot.StageThreeState stageThree = RepositorySnapshot.StageThreeState.empty();
        RepositorySnapshot.StageFourState stageFour = RepositorySnapshot.StageFourState.empty();
        RepositorySnapshot.StageFiveState stageFive = RepositorySnapshot.StageFiveState.empty();
        RepositorySnapshot.TrainingState training = RepositorySnapshot.TrainingState.empty();
        if ("STAGE-GIT-02".equals(workspace.stageKey())) {
            profileTip = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/feature/profile")).trim();
            notificationTip = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/feature/notification")).trim();
        }
        if ("STAGE-GIT-03".equals(workspace.stageKey())) {
            String mainTip = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/main")).trim();
            String searchTip = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/feature/search")).trim();
            String searchParent = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/feature/search^1")).trim();
            String searchFileBlobId = gitOutput(workspace.containerId(), List.of("hash-object", "--", "search.txt")).trim();
            stageThree = new RepositorySnapshot.StageThreeState(mainTip, searchTip, searchParent, searchFileBlobId,
                    gitLines(workspace.containerId(), List.of("diff", "--name-only", "--no-ext-diff", "--")),
                    gitLines(workspace.containerId(), List.of("diff", "--cached", "--name-only", "--no-ext-diff", "--")),
                    unmergedPaths(workspace), gitLines(workspace.containerId(), List.of("ls-files", "--others", "--exclude-standard")),
                    readStashObjectIds(workspace));
        }
        if ("STAGE-GIT-04".equals(workspace.stageKey())) {
            String mainTip = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/main")).trim();
            String mainParent = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/main^1")).trim();
            String featureTip = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/feature/profile-message")).trim();
            String featureParent = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/feature/profile-message^1")).trim();
            String mainTree = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/main^{tree}")).trim();
            String featureTree = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/feature/profile-message^{tree}")).trim();
            String messagesBlob = gitOutput(workspace.containerId(), List.of("hash-object", "--", STAGE_FOUR_PATH)).trim();
            stageFour = new RepositorySnapshot.StageFourState(mainTip, mainParent, featureTip, featureParent,
                    mainTree, featureTree, messagesBlob,
                    gitLines(workspace.containerId(), List.of("diff", "--name-only", "--no-ext-diff", "--")),
                    gitLines(workspace.containerId(), List.of("diff", "--cached", "--name-only", "--no-ext-diff", "--")),
                    unmergedPaths(workspace), gitLines(workspace.containerId(), List.of("ls-files", "--others", "--exclude-standard")));
        }
        if ("STAGE-GIT-05".equals(workspace.stageKey())) {
            String mainTip = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/main")).trim();
            String paymentRetryTip = nullableLocalBranchTip(workspace, "refs/heads/feature/payment-retry");
            List<String> localBranches = gitLines(workspace.containerId(), List.of("for-each-ref", "--format=%(refname:short)", "refs/heads"))
                    .stream().sorted().toList();
            stageFive = new RepositorySnapshot.StageFiveState(mainTip, STAGE_FIVE_C1, STAGE_FIVE_C0, STAGE_FIVE_C1_TREE,
                    paymentRetryTip, localBranches);
        }
        if (workspace.stageKey().startsWith("TRAINING-GIT-")) {
            String mainTip = gitOutput(workspace.containerId(), List.of("rev-parse", "refs/heads/main")).trim();
            String trainingBranchTip = nullableLocalBranchTip(workspace, "refs/heads/feature/onboarding");
            List<String> headPaths = gitLines(workspace.containerId(), List.of("ls-tree", "-r", "--name-only", "HEAD"));
            List<String> workingPaths = gitLines(workspace.containerId(), List.of("diff", "--name-only", "--no-ext-diff", "--"));
            List<String> indexPaths = gitLines(workspace.containerId(), List.of("diff", "--cached", "--name-only", "--no-ext-diff", "--"));
            List<String> untrackedPaths = gitLines(workspace.containerId(), List.of("ls-files", "--others", "--exclude-standard"));
            List<String> ignoredPaths = gitLines(workspace.containerId(), List.of("ls-files", "--others", "--ignored", "--exclude-standard"));
            String introBlob = "TRAINING-GIT-01".equals(workspace.stageKey()) ? fileBlob(workspace, "onboarding/intro.txt") : "";
            String ignoreBlob = "TRAINING-GIT-02".equals(workspace.stageKey()) ? fileBlob(workspace, ".gitignore") : "";
            String configBlob = "TRAINING-GIT-02".equals(workspace.stageKey()) ? fileBlob(workspace, "config/application-training.properties") : "";
            String reportBlob = "TRAINING-GIT-02".equals(workspace.stageKey()) ? fileBlob(workspace, "build/training-report.txt") : "";
            String handoffBlob = "TRAINING-GIT-03".equals(workspace.stageKey()) ? fileBlob(workspace, "docs/handoff.md") : "";
            boolean reportExists = "TRAINING-GIT-02".equals(workspace.stageKey()) && pathExists(workspace, "/workspace/build/training-report.txt");
            training = new RepositorySnapshot.TrainingState(mainTip, trainingBranchTip, headPaths, workingPaths,
                    indexPaths, untrackedPaths, ignoredPaths, introBlob, ignoreBlob, configBlob, reportBlob,
                    handoffBlob, reportExists);
        }
        return new RepositorySnapshot(head, tree, firstParentTree, parentList, status.isEmpty(), reverting, ancestorList,
                currentBranch, profileTip, notificationTip, cherryPicking, merging, rebasing, stageThree, stageFour, stageFive, training);
    }

    private String fileBlob(Workspace workspace, String path) {
        String value = gitOutput(workspace.containerId(), List.of("hash-object", "--", path)).trim();
        if (!value.matches("[0-9a-f]{40}")) throw new IllegalStateException("training fixture file is invalid");
        return value;
    }

    private boolean pathExists(Workspace workspace, String path) {
        return docker.run(List.of("exec", workspace.containerId(), "/usr/bin/test", "-f", path), COMMAND_TIMEOUT).exitCode() == 0;
    }

    private String nullableLocalBranchTip(Workspace workspace, String ref) {
        var arguments = gitPrefix(workspace.containerId());
        arguments.addAll(List.of("-C", "/workspace", "show-ref", "--verify", "--quiet", ref));
        var result = docker.run(arguments, COMMAND_TIMEOUT);
        if (result.outputTruncated()) throw new IllegalStateException("branch snapshot failed");
        if (result.exitCode() == 1) return null;
        if (result.exitCode() != 0) throw new IllegalStateException("branch snapshot failed");
        return gitOutput(workspace.containerId(), List.of("rev-parse", ref)).trim();
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
        if (result.exitCode() != 0 || result.outputTruncated()) throw new IllegalStateException("snapshot failed");
        return result.stdout();
    }
    private boolean stateFileExists(Workspace workspace, String name) {
        var result = docker.run(List.of("exec", workspace.containerId(), "/usr/bin/test", "-e", "/workspace/.git/" + name), COMMAND_TIMEOUT);
        if (result.outputTruncated() || (result.exitCode() != 0 && result.exitCode() != 1)) {
            throw new IllegalStateException("Git state file check failed");
        }
        return result.exitCode() == 0;
    }
    private List<String> gitLines(String containerId, List<String> gitArguments) {
        String output = gitOutput(containerId, gitArguments);
        return output.isBlank() ? List.of() : output.lines().filter(line -> !line.isBlank()).toList();
    }
    private List<String> unmergedPaths(Workspace workspace) {
        String output = gitOutput(workspace.containerId(), List.of("ls-files", "--unmerged"));
        if (output.isBlank()) return List.of();
        var paths = new java.util.LinkedHashSet<String>();
        for (String line : output.lines().toList()) {
            int separator = line.indexOf('\t');
            if (separator <= 0 || separator == line.length() - 1) throw new IllegalStateException("invalid unmerged path output");
            paths.add(line.substring(separator + 1));
        }
        return List.copyOf(paths);
    }
    private List<String> readStashObjectIds(Workspace workspace) {
        var arguments = gitPrefix(workspace.containerId());
        arguments.addAll(List.of("-C", "/workspace", "-c", "core.hooksPath=/opt/empty-hooks", "-c", "core.attributesFile=/dev/null",
                "-c", "credential.helper=", "-c", "core.pager=cat", "-c", "core.editor=:", "-c", "protocol.file.allow=never",
                "-c", "user.name=Developer Dungeon Player", "-c", "user.email=player@developer-dungeon.invalid", "stash", "list", "--format=%H"));
        var result = docker.run(arguments, COMMAND_TIMEOUT);
        if (result.exitCode() != 0 || result.outputTruncated()) throw new IllegalStateException("stash snapshot failed");
        if (result.stdout().isBlank()) return List.of();
        List<String> objectIds = result.stdout().lines().toList();
        if (objectIds.stream().anyMatch(id -> !id.matches("[0-9a-f]{40}")) || new java.util.LinkedHashSet<>(objectIds).size() != objectIds.size()) {
            throw new IllegalStateException("invalid stash snapshot");
        }
        return List.copyOf(objectIds);
    }

    List<String> gitArguments(String containerId, GitCommand command) {
        var arguments = gitPrefix(containerId);
        arguments.add("-C"); arguments.add("/workspace");
        arguments.add("-c"); arguments.add("core.hooksPath=/opt/empty-hooks"); arguments.add("-c"); arguments.add("core.attributesFile=/dev/null");
        arguments.add("-c"); arguments.add("credential.helper="); arguments.add("-c"); arguments.add("core.pager=cat"); arguments.add("-c"); arguments.add("core.editor=:"); arguments.add("-c"); arguments.add("protocol.file.allow=never");
        arguments.add("-c"); arguments.add("user.name=Developer Dungeon Player"); arguments.add("-c"); arguments.add("user.email=player@developer-dungeon.invalid");
        switch (command.kind()) {
            case STATUS -> arguments.addAll(List.of("status", "--short"));
            case LOG_ONELINE -> arguments.addAll(List.of("log", "--oneline", "--no-decorate", "--abbrev=12"));
            case LOG_ONELINE_ALL_DECORATE -> arguments.addAll(List.of("log", "--oneline", "--all", "--decorate", "--abbrev=12"));
            case BRANCH -> arguments.add("branch");
            case SHOW -> { arguments.addAll(List.of("show", "--no-ext-diff", "--no-textconv")); arguments.add(command.objectId()); }
            case SWITCH -> { arguments.add("switch"); arguments.add(command.branchName()); }
            case CHERRY_PICK -> { arguments.add("cherry-pick"); arguments.add(command.objectId()); }
            case RESET_HARD -> { arguments.addAll(List.of("reset", "--hard")); arguments.add(command.objectId()); }
            case REVERT_NO_EDIT -> { arguments.addAll(List.of("revert", "--no-edit")); arguments.add(command.objectId()); }
            case REVERT_NO_COMMIT -> { arguments.addAll(List.of("revert", "--no-commit")); arguments.add(command.objectId()); }
            case COMMIT_RESTORE_SETTINGS -> arguments.addAll(List.of("commit", "-m", "restore-required-settings"));
            case DIFF -> arguments.addAll(List.of("diff", "--no-ext-diff", "--no-textconv", "--"));
            case DIFF_STAGED -> arguments.addAll(List.of("diff", "--staged", "--no-ext-diff", "--no-textconv", "--"));
            case STASH_PUSH -> arguments.addAll(List.of("stash", "push"));
            case STASH_LIST -> arguments.addAll(List.of("stash", "list"));
            case STASH_POP -> arguments.addAll(List.of("stash", "pop"));
            case STASH_APPLY -> arguments.addAll(List.of("stash", "apply"));
            case STASH_DROP -> arguments.addAll(List.of("stash", "drop"));
            case LOG_GRAPH_ALL -> arguments.addAll(List.of("log", "--oneline", "--all", "--decorate", "--graph", "--abbrev=12"));
            case MERGE_PROFILE_MESSAGE -> arguments.addAll(List.of("merge", "--no-edit", "feature/profile-message"));
            case ADD_PROFILE_MESSAGES -> arguments.addAll(List.of("add", "--", STAGE_FOUR_PATH));
            case COMMIT_NO_EDIT -> arguments.addAll(List.of("commit", "--no-edit"));
            case COMMIT_ALL_NO_EDIT -> arguments.addAll(List.of("commit", "-a", "--no-edit"));
            case REFLOG_HEAD -> arguments.addAll(List.of("reflog", "show", "--format=%h%x09%gs", "--abbrev=12", "--max-count=8", "HEAD"));
            case CREATE_PAYMENT_RETRY_BRANCH -> arguments.addAll(List.of("branch", "feature/payment-retry", command.objectId()));
            case SWITCH_PAYMENT_RETRY -> arguments.addAll(List.of("switch", "feature/payment-retry"));
            case SWITCH_CREATE_PAYMENT_RETRY -> { arguments.addAll(List.of("switch", "-c", "feature/payment-retry")); arguments.add(command.objectId()); }
            case ADD_TRAINING_INTRO -> arguments.addAll(List.of("add", "--", "onboarding/intro.txt"));
            case COMMIT_TRAINING_ONE -> arguments.addAll(List.of("commit", "-m", "complete-training-01"));
            case UNSTAGE_TRAINING_REPORT -> arguments.addAll(List.of("restore", "--staged", "--", "build/training-report.txt"));
            case ADD_TRAINING_IGNORE -> arguments.addAll(List.of("add", "--", ".gitignore"));
            case ADD_TRAINING_CONFIG -> arguments.addAll(List.of("add", "--", "config/application-training.properties"));
            case COMMIT_TRAINING_TWO -> arguments.addAll(List.of("commit", "-m", "complete-training-02"));
            case SWITCH_CREATE_TRAINING_BRANCH -> arguments.addAll(List.of("switch", "-c", "feature/onboarding"));
            case SWITCH_TRAINING_BRANCH -> arguments.addAll(List.of("switch", "feature/onboarding"));
            case ADD_TRAINING_HANDOFF -> arguments.addAll(List.of("add", "--", "docs/handoff.md"));
            case COMMIT_TRAINING_THREE -> arguments.addAll(List.of("commit", "-m", "complete-training-03"));
        }
        return arguments;
    }
    private ArrayList<String> gitPrefix(String containerId) {
        var arguments = new ArrayList<String>();
        arguments.addAll(List.of("exec", "--env", "HOME=/tmp/empty-home", "--env", "XDG_CONFIG_HOME=/tmp/empty-xdg",
                "--env", "GIT_CONFIG_NOSYSTEM=1", "--env", "GIT_CONFIG_GLOBAL=/dev/null", "--env", "GIT_TERMINAL_PROMPT=0",
                "--env", "GIT_ASKPASS=/bin/false", "--env", "GIT_ALLOW_PROTOCOL=", "--env", "GIT_PROTOCOL_FROM_USER=0", containerId, "/usr/bin/git"));
        return arguments;
    }

    private Workspace workspace(String workspaceId, String attemptId, long generation) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace == null || expiredWorkspaces.contains(workspaceId) || cleanupPending.contains(workspaceId) || !workspace.attemptId().equals(attemptId) || workspace.generation() != generation) {
            throw new IllegalArgumentException("unknown workspace");
        }
        return workspace;
    }
    private void requireRequest(String attemptId, String requestId, long generation) {
        if (attemptId == null || !attemptId.matches(UUID_PATTERN) || requestId == null || !requestId.matches(UUID_PATTERN) || generation < 0) {
            throw new IllegalArgumentException("invalid request identity");
        }
    }
    private String requestKey(String scope, String requestId) { return scope + ":" + requestId; }
    private void requireStage(String stageKey) {
        if (!"STAGE-GIT-01".equals(stageKey) && !"STAGE-GIT-02".equals(stageKey) && !"STAGE-GIT-03".equals(stageKey)
                && !"STAGE-GIT-04".equals(stageKey) && !"STAGE-GIT-05".equals(stageKey)
                && !"TRAINING-GIT-01".equals(stageKey) && !"TRAINING-GIT-02".equals(stageKey)
                && !"TRAINING-GIT-03".equals(stageKey)) throw new IllegalArgumentException("unknown stage");
    }
    private String fixturePath(String stageKey) {
        return switch (stageKey) {
            case "STAGE-GIT-01" -> "/opt/fixtures/stage-git-01";
            case "STAGE-GIT-02" -> "/opt/fixtures/stage-git-02";
            case "STAGE-GIT-03" -> "/opt/fixtures/stage-git-03";
            case "STAGE-GIT-04" -> "/opt/fixtures/stage-git-04";
            case "STAGE-GIT-05" -> "/opt/fixtures/stage-git-05";
            case "TRAINING-GIT-01" -> "/opt/fixtures/training-git-01";
            case "TRAINING-GIT-02" -> "/opt/fixtures/training-git-02";
            case "TRAINING-GIT-03" -> "/opt/fixtures/training-git-03";
            default -> throw new IllegalArgumentException("unknown stage");
        };
    }
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
                || !labels.contains("\"io.developer-dungeon.owner\":\"git-runner\"")
                || !labels.contains("\"io.developer-dungeon.attempt\":\"" + workspace.attemptId() + "\"")
                || !labels.contains("\"io.developer-dungeon.workspace\":\"" + workspace.workspaceId() + "\"")
                || !labels.contains("\"io.developer-dungeon.challenge.build-input-sha256\":\"" + properties.imageFingerprint() + "\"")) {
            throw new IllegalStateException("created challenge container verification failed");
        }
    }
    private void validateAllowedObject(GitCommand command, Workspace workspace) {
        if (command.objectId() == null) return;
        if (!allowedObjects.getOrDefault(workspace.workspaceId(), Set.of()).contains(command.objectId())) {
            throw new IllegalArgumentException("object is not allowed for this stage");
        }
        if (!"commit".equals(gitOutput(workspace.containerId(), List.of("cat-file", "-t", command.objectId())).trim())) {
            throw new IllegalArgumentException("object is not a commit");
        }
        StageTargets targets = stageTargets.get(workspace.workspaceId());
        if ((command.kind() == CommandKind.REVERT_NO_EDIT || command.kind() == CommandKind.REVERT_NO_COMMIT)
                && !command.objectId().equals(targets.revertTarget())) {
            throw new IllegalArgumentException("only the stage's accidental commit can be reverted");
        }
    }
    private void validateStageCommand(Workspace workspace, GitCommand command) {
        StageTargets targets = stageTargets.get(workspace.workspaceId());
        if (workspace.stageKey().startsWith("TRAINING-GIT-")) {
            validateTrainingCommand(workspace.stageKey(), command.kind());
            return;
        }
        if ("STAGE-GIT-01".equals(workspace.stageKey())) {
            if (command.kind() != CommandKind.STATUS && command.kind() != CommandKind.LOG_ONELINE
                    && command.kind() != CommandKind.SHOW && command.kind() != CommandKind.REVERT_NO_EDIT
                    && command.kind() != CommandKind.REVERT_NO_COMMIT
                    && command.kind() != CommandKind.COMMIT_RESTORE_SETTINGS) {
                throw new IllegalArgumentException("command is not allowed for this stage");
            }
            return;
        }
        if ("STAGE-GIT-04".equals(workspace.stageKey())) {
            if (command.kind() != CommandKind.STATUS && command.kind() != CommandKind.LOG_GRAPH_ALL
                    && command.kind() != CommandKind.DIFF && command.kind() != CommandKind.BRANCH
                    && command.kind() != CommandKind.MERGE_PROFILE_MESSAGE && command.kind() != CommandKind.ADD_PROFILE_MESSAGES
                    && command.kind() != CommandKind.COMMIT_NO_EDIT && command.kind() != CommandKind.COMMIT_ALL_NO_EDIT) {
                throw new IllegalArgumentException("command is not allowed for this stage");
            }
            return;
        }
        if ("STAGE-GIT-05".equals(workspace.stageKey())) {
            if (command.kind() != CommandKind.STATUS && command.kind() != CommandKind.LOG_ONELINE_ALL_DECORATE
                    && command.kind() != CommandKind.REFLOG_HEAD && command.kind() != CommandKind.SHOW
                    && command.kind() != CommandKind.CREATE_PAYMENT_RETRY_BRANCH
                    && command.kind() != CommandKind.SWITCH_PAYMENT_RETRY
                    && command.kind() != CommandKind.SWITCH_CREATE_PAYMENT_RETRY) {
                throw new IllegalArgumentException("command is not allowed for this stage");
            }
            if ((command.kind() == CommandKind.CREATE_PAYMENT_RETRY_BRANCH
                    || command.kind() == CommandKind.SWITCH_CREATE_PAYMENT_RETRY)
                    && !command.objectId().equals(targets.recoveryTarget())) {
                throw new IllegalArgumentException("only the reflog recovery commit can be used");
            }
            return;
        }
        if ("STAGE-GIT-03".equals(workspace.stageKey())) {
            if (command.kind() != CommandKind.STATUS && command.kind() != CommandKind.DIFF && command.kind() != CommandKind.DIFF_STAGED
                    && command.kind() != CommandKind.BRANCH && command.kind() != CommandKind.STASH_PUSH && command.kind() != CommandKind.STASH_LIST
                    && command.kind() != CommandKind.STASH_POP && command.kind() != CommandKind.STASH_APPLY
                    && command.kind() != CommandKind.STASH_DROP && command.kind() != CommandKind.SWITCH) {
                throw new IllegalArgumentException("command is not allowed for this stage");
            }
            if (command.kind() == CommandKind.SWITCH && !"feature/search".equals(command.branchName())) {
                throw new IllegalArgumentException("only the stage's search branch can be selected");
            }
            return;
        }
        if (command.kind() != CommandKind.STATUS && command.kind() != CommandKind.LOG_ONELINE_ALL_DECORATE
                && command.kind() != CommandKind.BRANCH && command.kind() != CommandKind.SHOW
                && command.kind() != CommandKind.SWITCH && command.kind() != CommandKind.CHERRY_PICK
                && command.kind() != CommandKind.RESET_HARD) {
            throw new IllegalArgumentException("command is not allowed for this stage");
        }
        if (command.kind() == CommandKind.CHERRY_PICK && !command.objectId().equals(targets.cherryPickTarget())) {
            throw new IllegalArgumentException("only the stage's notification commit can be cherry-picked");
        }
        if (command.kind() == CommandKind.RESET_HARD && !command.objectId().equals(targets.resetTarget())) {
            throw new IllegalArgumentException("only the stage's original branch tip can be reset");
        }
        if (command.kind() == CommandKind.SWITCH && !"feature/profile".equals(command.branchName()) && !"feature/notification".equals(command.branchName())) {
            throw new IllegalArgumentException("only the stage's branches can be selected");
        }
    }
    void validateTrainingCommand(String stageKey, CommandKind kind) {
        boolean allowed = switch (stageKey) {
            case "TRAINING-GIT-01" -> kind == CommandKind.STATUS || kind == CommandKind.DIFF
                    || kind == CommandKind.DIFF_STAGED || kind == CommandKind.LOG_ONELINE
                    || kind == CommandKind.ADD_TRAINING_INTRO || kind == CommandKind.COMMIT_TRAINING_ONE;
            case "TRAINING-GIT-02" -> kind == CommandKind.STATUS || kind == CommandKind.DIFF
                    || kind == CommandKind.DIFF_STAGED || kind == CommandKind.LOG_ONELINE
                    || kind == CommandKind.UNSTAGE_TRAINING_REPORT || kind == CommandKind.ADD_TRAINING_IGNORE
                    || kind == CommandKind.ADD_TRAINING_CONFIG || kind == CommandKind.COMMIT_TRAINING_TWO;
            case "TRAINING-GIT-03" -> kind == CommandKind.STATUS || kind == CommandKind.DIFF
                    || kind == CommandKind.DIFF_STAGED || kind == CommandKind.LOG_ONELINE || kind == CommandKind.BRANCH
                    || kind == CommandKind.SWITCH_CREATE_TRAINING_BRANCH || kind == CommandKind.SWITCH_TRAINING_BRANCH
                    || kind == CommandKind.ADD_TRAINING_HANDOFF || kind == CommandKind.COMMIT_TRAINING_THREE;
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("command is not allowed for this training");
    }
    private StageTargets captureStageTargets(String stageKey, RepositorySnapshot initial) {
        if (stageKey.startsWith("TRAINING-GIT-")) {
            validateTrainingInitial(stageKey, initial);
            return new StageTargets(null, null, null, null, Set.of(initial.headObjectId()));
        }
        if ("STAGE-GIT-01".equals(stageKey)) {
            return new StageTargets(initial.headObjectId(), null, null, null, Set.copyOf(initial.ancestorObjectIds()));
        }
        if ("STAGE-GIT-03".equals(stageKey)) {
            var state = initial.stageThree();
            if (!"main".equals(initial.currentBranch()) || !initial.headObjectId().equals(state.mainTip()) || state.mainTip().isBlank()
                    || state.featureSearchTip().isBlank() || !state.mainTip().equals(state.featureSearchParent()) || initial.clean()
                    || !state.workingTreePaths().equals(List.of("search.txt")) || !state.indexPaths().isEmpty() || !state.unmergedPaths().isEmpty()
                    || !state.untrackedPaths().isEmpty() || !state.stashObjectIds().isEmpty() || initial.revertInProgress()
                    || initial.cherryPickInProgress() || initial.mergeInProgress() || initial.rebaseInProgress()) {
                throw new IllegalStateException("stage fixture is invalid");
            }
            return new StageTargets(null, null, null, null, Set.of());
        }
        if ("STAGE-GIT-04".equals(stageKey)) {
            var state = initial.stageFour();
            if (!"main".equals(initial.currentBranch()) || !initial.headObjectId().equals(state.mainTip())
                    || state.mainTip().isBlank() || state.mainParent().isBlank() || state.featureProfileMessageTip().isBlank()
                    || !state.mainParent().equals(state.featureProfileMessageParent())
                    || !STAGE_FOUR_MAIN_BLOB.equals(state.messagesBlobId())
                    || !STAGE_FOUR_MAIN_TREE.equals(state.mainTreeId()) || !STAGE_FOUR_FEATURE_TREE.equals(state.featureTreeId())
                    || !initial.clean()
                    || !state.workingTreePaths().isEmpty() || !state.indexPaths().isEmpty() || !state.unmergedPaths().isEmpty()
                    || !state.untrackedPaths().isEmpty() || initial.revertInProgress() || initial.cherryPickInProgress()
                    || initial.mergeInProgress() || initial.rebaseInProgress()) {
                throw new IllegalStateException("stage fixture is invalid");
            }
            return new StageTargets(null, null, null, null, Set.of());
        }
        if ("STAGE-GIT-05".equals(stageKey)) {
            var state = initial.stageFive();
            if (!"main".equals(initial.currentBranch()) || !STAGE_FIVE_C0.equals(initial.headObjectId())
                    || !STAGE_FIVE_C0.equals(state.mainTip()) || !STAGE_FIVE_C1.equals(state.recoveryTargetId())
                    || !STAGE_FIVE_C0.equals(state.recoveryTargetParent()) || !STAGE_FIVE_C1_TREE.equals(state.recoveryTargetTreeId())
                    || state.paymentRetryTip() != null || !state.localBranches().equals(List.of("main")) || !initial.clean()
                    || initial.revertInProgress() || initial.cherryPickInProgress() || initial.mergeInProgress() || initial.rebaseInProgress()) {
                throw new IllegalStateException("stage fixture is invalid");
            }
            return new StageTargets(null, null, null, STAGE_FIVE_C1, Set.of(STAGE_FIVE_C0, STAGE_FIVE_C1));
        }
        String c1 = initial.headObjectId();
        String c0 = initial.featureNotificationTip();
        if (!"feature/profile".equals(initial.currentBranch()) || !c1.equals(initial.featureProfileTip())
                || c0.isBlank() || initial.headParents().size() != 1 || !c0.equals(initial.headParents().getFirst())
                || !initial.clean() || initial.cherryPickInProgress()) {
            throw new IllegalStateException("stage fixture is invalid");
        }
        return new StageTargets(null, c0, c1, null, Set.of(c0, c1));
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
        if ("STAGE-GIT-04".equals(workspace.stageKey())) validateStageFourFixturePaths(workspace);
        if ("STAGE-GIT-05".equals(workspace.stageKey())) validateStageFiveFixture(workspace);
        if (workspace.stageKey().startsWith("TRAINING-GIT-")) validateTrainingInitial(workspace.stageKey(), snapshot(workspace));
    }

    private void validateTrainingInitial(String stageKey, RepositorySnapshot snapshot) {
        var state = snapshot.training();
        boolean common = "main".equals(snapshot.currentBranch()) && snapshot.headObjectId().equals(state.mainTip())
                && state.trainingBranchTip() == null && snapshot.headParents().isEmpty()
                && !snapshot.clean() && !snapshot.revertInProgress() && !snapshot.cherryPickInProgress()
                && !snapshot.mergeInProgress() && !snapshot.rebaseInProgress() && state.untrackedPaths().isEmpty();
        boolean valid = switch (stageKey) {
            case "TRAINING-GIT-01" -> common
                    && state.headPaths().equals(List.of("onboarding/intro.txt"))
                    && state.workingTreePaths().equals(List.of("onboarding/intro.txt"))
                    && state.indexPaths().isEmpty() && state.ignoredPaths().isEmpty()
                    && !state.introBlobId().isBlank();
            case "TRAINING-GIT-02" -> common
                    && state.headPaths().equals(List.of(".gitignore", "config/application-training.properties"))
                    && state.workingTreePaths().equals(List.of(".gitignore", "config/application-training.properties"))
                    && state.indexPaths().equals(List.of("build/training-report.txt"))
                    && state.ignoredPaths().isEmpty() && state.reportExists()
                    && !state.ignoreBlobId().isBlank() && !state.configBlobId().isBlank() && !state.reportBlobId().isBlank();
            case "TRAINING-GIT-03" -> common
                    && state.headPaths().equals(List.of("docs/handoff.md"))
                    && state.workingTreePaths().equals(List.of("docs/handoff.md"))
                    && state.indexPaths().isEmpty() && state.ignoredPaths().isEmpty()
                    && !state.handoffBlobId().isBlank();
            default -> false;
        };
        if (!valid) throw new IllegalStateException("training fixture is invalid");
    }

    private void validateStageFourFixturePaths(Workspace workspace) {
        for (String ref : List.of("refs/heads/main", "refs/heads/feature/profile-message")) {
            String entry = gitOutput(workspace.containerId(), List.of("ls-tree", ref, "--", STAGE_FOUR_PATH)).trim();
            if (!entry.matches("100644 blob [0-9a-f]{40}\\t" + java.util.regex.Pattern.quote(STAGE_FOUR_PATH))) {
                throw new IllegalStateException("stage fixture file mode is invalid");
            }
            List<String> changed = gitLines(workspace.containerId(), List.of("diff", "--name-only", ref + "^1", ref, "--"));
            if (!changed.equals(List.of(STAGE_FOUR_PATH))) throw new IllegalStateException("stage fixture changes are invalid");
        }
    }

    private void validateStageFiveFixture(Workspace workspace) {
        List<String> refs = gitLines(workspace.containerId(), List.of("for-each-ref", "--format=%(refname)%09%(objectname)"));
        if (!refs.equals(List.of("refs/heads/main\t" + STAGE_FIVE_C0))) throw new IllegalStateException("stage fixture refs are invalid");
        if (!"commit".equals(gitOutput(workspace.containerId(), List.of("cat-file", "-t", STAGE_FIVE_C1)).trim())
                || !STAGE_FIVE_C0.equals(gitOutput(workspace.containerId(), List.of("show", "-s", "--format=%P", STAGE_FIVE_C1)).trim())
                || !STAGE_FIVE_C1_TREE.equals(gitOutput(workspace.containerId(), List.of("show", "-s", "--format=%T", STAGE_FIVE_C1)).trim())) {
            throw new IllegalStateException("stage fixture recovery commit is invalid");
        }
        if (!gitLines(workspace.containerId(), List.of("for-each-ref", "--contains=" + STAGE_FIVE_C1, "--format=%(refname)")).isEmpty()) {
            throw new IllegalStateException("stage fixture recovery commit is reachable");
        }
        String c0 = gitOutput(workspace.containerId(), List.of("rev-parse", "--short=12", STAGE_FIVE_C0)).trim();
        String c1 = gitOutput(workspace.containerId(), List.of("rev-parse", "--short=12", STAGE_FIVE_C1)).trim();
        if (!c0.matches("[0-9a-f]{12}") || !c1.matches("[0-9a-f]{12}") || c0.equals(c1)
                || !c0.equals(STAGE_FIVE_C0.substring(0, 12)) || !c1.equals(STAGE_FIVE_C1.substring(0, 12))) {
            throw new IllegalStateException("stage fixture abbreviated IDs are invalid");
        }
        List<String> reflog = gitLines(workspace.containerId(), List.of("reflog", "show", "--format=%h%x09%gs", "--abbrev=12", "--max-count=8", "HEAD"));
        if (reflog.stream().anyMatch(line -> !line.matches("[0-9a-f]{12}\\t[^\\r\\n]*"))
                || reflog.stream().filter(line -> line.equals(c1 + "\tcommit: C1: payment retry")).count() != 1) {
            throw new IllegalStateException("stage fixture reflog is invalid");
        }
    }

    private void requireStageFourFile(Workspace workspace, StageFileKey fileKey) {
        if (!"STAGE-GIT-04".equals(workspace.stageKey()) || fileKey != StageFileKey.PROFILE_MESSAGES) {
            throw new IllegalArgumentException("file is not allowed for this stage");
        }
    }

    private String readStageFourFile(Workspace workspace) {
        var result = docker.run(List.of("exec", workspace.containerId(), "/opt/image-rootfs/stage-four-file", "read"), COMMAND_TIMEOUT);
        if (result.exitCode() != 0 || result.outputTruncated()) throw new IllegalStateException("stage file read failed");
        validatedEditorBytes(result.stdout(), false);
        return result.stdout();
    }

    private void validateStageFourEditState(RepositorySnapshot snapshot) {
        var state = snapshot.stageFour();
        if (!"main".equals(snapshot.currentBranch()) || !snapshot.mergeInProgress() || snapshot.revertInProgress()
                || snapshot.cherryPickInProgress() || snapshot.rebaseInProgress()
                || !onlyStageFourPath(state.workingTreePaths()) || !onlyStageFourPath(state.indexPaths())
                || !onlyStageFourPath(state.unmergedPaths()) || !onlyStageFourPath(state.untrackedPaths())) {
            throw new IllegalArgumentException("stage file cannot be edited in the current repository state");
        }
    }

    private boolean onlyStageFourPath(List<String> paths) {
        return paths.stream().allMatch(STAGE_FOUR_PATH::equals);
    }

    private NormalizedEditorContent normalizeEditorContent(String content) {
        if (content == null) throw new IllegalArgumentException("file content is required");
        String normalized = content.replace("\r\n", "\n");
        return new NormalizedEditorContent(normalized, validatedEditorBytes(normalized, true));
    }

    private byte[] validatedEditorBytes(String content, boolean playerInput) {
        if (content == null || content.indexOf('\r') >= 0 || content.codePoints().anyMatch(code ->
                Character.isISOControl(code) && code != '\n' && code != '\t')) {
            throw new IllegalArgumentException(playerInput ? "file content contains an invalid control character" : "stage file content is invalid");
        }
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(content));
            byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes);
            if (bytes.length > 2048) throw new IllegalArgumentException("file content is too large");
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("file content is not valid UTF-8", exception);
        }
    }

    private String versionToken(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private void invalidateWorkspaceAfterFileFailure(Workspace workspace, String requestId, RuntimeException exception) {
        cleanupPending.add(workspace.workspaceId());
        try { removeWorkspace(workspace, requestId); }
        catch (RuntimeException cleanupFailure) { recordCleanupFailure(workspace, "file-operation"); exception.addSuppressed(cleanupFailure); }
    }
    private void removeContainer(String containerId) {
        var result = docker.run(List.of("rm", "-f", containerId), COMMAND_TIMEOUT);
        if (result.exitCode() != 0) throw new IllegalStateException("container cleanup failed");
    }
    private void removeWorkspace(Workspace workspace, String cleanupRequestId) {
        removeContainer(workspace.containerId());
        ledger.markDeleted(workspace.workspaceId(), cleanupRequestId);
        workspaces.remove(workspace.workspaceId(), workspace);
        allowedObjects.remove(workspace.workspaceId());
        stageTargets.remove(workspace.workspaceId());
        expiredWorkspaces.remove(workspace.workspaceId());
        cleanupPending.remove(workspace.workspaceId());
        cleanupAttempts.remove(workspace.workspaceId());
        executedRequests.keySet().removeIf(key -> key.startsWith(workspace.workspaceId() + ":"));
        readFileRequests.keySet().removeIf(key -> key.startsWith("read:" + workspace.workspaceId() + ":"));
        writeFileRequests.keySet().removeIf(key -> key.startsWith("write:" + workspace.workspaceId() + ":"));
        createdRequests.entrySet().removeIf(entry -> entry.getValue().workspaceId().equals(workspace.workspaceId()));
        destroyedWorkspaces.entrySet().removeIf(entry -> entry.getValue().workspaceId().equals(workspace.workspaceId()));
    }
    private void removeFailedCreate(Workspace workspace) {
        removeContainer(workspace.containerId()); ledger.remove(workspace.workspaceId()); workspaces.remove(workspace.workspaceId(), workspace);
        allowedObjects.remove(workspace.workspaceId()); stageTargets.remove(workspace.workspaceId()); cleanupPending.remove(workspace.workspaceId()); cleanupAttempts.remove(workspace.workspaceId());
    }
    private void touch(Workspace workspace) {
        workspaces.replace(workspace.workspaceId(), workspace,
                new Workspace(workspace.workspaceId(), workspace.attemptId(), workspace.generation(), workspace.stageKey(), workspace.containerId(), workspace.createdAt(), clock.instant()));
    }
    synchronized void beginShutdown() {
        shuttingDown.set(true);
        RuntimeException failure = null;
        for (Workspace workspace : List.copyOf(workspaces.values())) {
            try { cleanupPending.add(workspace.workspaceId()); removeWorkspace(workspace, UUID.randomUUID().toString()); }
            catch (RuntimeException exception) { recordCleanupFailure(workspace, "shutdown"); failure = exception; }
        }
        if (failure != null) throw new IllegalStateException("challenge cleanup is incomplete", failure);
    }
    boolean isReady() { return !shuttingDown.get() && !degraded.get(); }
    private void requireOperational() {
        if (shuttingDown.get()) throw new IllegalStateException("Git Runner is shutting down");
        if (degraded.get()) throw new IllegalStateException("Git Runner requires cleanup recovery");
    }
    private void requireNoCleanupPending() {
        if (!cleanupPending.isEmpty()) throw new IllegalStateException("challenge cleanup is incomplete");
    }
    private void cleanupStartupOrphans() {
        for (ContainerOwnershipLedger.Entry entry : ledger.entries()) {
            if (entry.state() == ContainerOwnershipLedger.State.DELETED) continue;
            String id = entry.containerId();
            if (id == null) id = resolveIntent(entry);
            verifyOwnedContainer(entry, id); removeContainer(id); ledger.markDeleted(entry.workspaceId(), UUID.randomUUID().toString());
        }
    }
    private void recoverUnpublishedCreate(WorkspaceRequest request, String workspaceId, String imageId, String knownContainerId, RuntimeException original) {
        degraded.set(true);
        ContainerOwnershipLedger.Entry intent = new ContainerOwnershipLedger.Entry(ContainerOwnershipLedger.State.INTENT, clock.instant(), 0,
                request.attemptId(), workspaceId, request.generation(), imageId, properties.imageFingerprint(), null, null);
        try {
            String containerId = knownContainerId == null ? resolveIntent(intent) : knownContainerId;
            verifyOwnedContainer(intent, containerId);
            removeContainer(containerId);
            ledger.remove(workspaceId);
            degraded.set(false);
        } catch (RuntimeException recoveryFailure) {
            original.addSuppressed(recoveryFailure);
            log.warn("Unpublished challenge container recovery is incomplete: workspaceId={}", workspaceId);
        }
    }
    private String resolveIntent(ContainerOwnershipLedger.Entry entry) {
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = docker.run(List.of("ps", "-aq", "--filter", "label=io.developer-dungeon.workspace=" + entry.workspaceId()), COMMAND_TIMEOUT);
            if (result.exitCode() != 0) throw new IllegalStateException("ownership recovery failed");
            List<String> ids = result.stdout().lines().map(String::trim).filter(id -> id.matches(CONTAINER_ID_PATTERN)).toList();
            if (ids.size() == 1) return ids.getFirst();
            if (ids.size() > 1) throw new IllegalStateException("ownership recovery is ambiguous");
            ledger.incrementConfirmation(entry.workspaceId());
            if (attempt < 2) try { Thread.sleep(2_000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("ownership recovery interrupted", exception); }
        }
        throw new IllegalStateException("unresolved container creation intent");
    }
    private void verifyOwnedContainer(ContainerOwnershipLedger.Entry entry, String containerId) {
        var inspect = docker.run(List.of("container", "inspect", "--format", "{{.Id}}|{{.Image}}|{{json .Config.Labels}}", containerId), COMMAND_TIMEOUT);
        String output = inspect.stdout().trim();
        String[] parts = output.split("\\|", 3);
        if (inspect.exitCode() != 0 || parts.length != 3 || !(parts[0].equals(containerId) || parts[0].startsWith(containerId)) || !parts[1].equals(entry.imageId())
                || !output.contains("\"io.developer-dungeon.project\":\"developer-dungeon\"")
                || !output.contains("\"io.developer-dungeon.owner\":\"git-runner\"")
                || !output.contains("\"io.developer-dungeon.attempt\":\"" + entry.attemptId() + "\"")
                || !output.contains("\"io.developer-dungeon.workspace\":\"" + entry.workspaceId() + "\"")
                || !output.contains("\"io.developer-dungeon.challenge.build-input-sha256\":\"" + entry.fingerprint() + "\"")) throw new IllegalStateException("owned container identity is invalid");
    }
    private void cleanupLedgerOrphans(Instant cutoff) {
        Set<String> active = workspaces.values().stream().map(Workspace::containerId).collect(java.util.stream.Collectors.toSet());
        for (ContainerOwnershipLedger.Entry entry : ledger.entries()) {
            if (entry.state() != ContainerOwnershipLedger.State.ACTIVE || entry.containerId() == null || active.contains(entry.containerId()) || entry.createdAt().isAfter(cutoff)) continue;
            verifyOwnedContainer(entry, entry.containerId()); removeContainer(entry.containerId());
            ledger.markDeleted(entry.workspaceId(), UUID.randomUUID().toString());
        }
    }
    private void tryCleanup(Workspace workspace, String cleanupRequestId, String reason) {
        if (cleanupAttempts.getOrDefault(workspace.workspaceId(), 0) >= MAX_CLEANUP_ATTEMPTS) {
            cleanupPending.add(workspace.workspaceId());
            log.warn("Skipping challenge container cleanup after max attempts: workspaceId={}, reason={}", workspace.workspaceId(), reason);
            return;
        }
        cleanupPending.add(workspace.workspaceId());
        try {
            removeWorkspace(workspace, cleanupRequestId);
        } catch (RuntimeException exception) {
            recordCleanupFailure(workspace, reason);
        }
    }
    private void recordCleanupFailure(Workspace workspace, String reason) {
        int attempts = cleanupAttempts.merge(workspace.workspaceId(), 1, Integer::sum);
        cleanupPending.add(workspace.workspaceId());
        log.warn("Challenge container cleanup failed: workspaceId={}, reason={}, attempts={}", workspace.workspaceId(), reason, attempts);
    }
    private record NormalizedEditorContent(String content, byte[] bytes) {
        NormalizedEditorContent { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    private record StageTargets(String revertTarget, String resetTarget, String cherryPickTarget, String recoveryTarget,
                                Set<String> allowedObjects) { }
    private record Workspace(String workspaceId, String attemptId, long generation, String stageKey, String containerId, Instant createdAt, Instant lastActivityAt) { }
}
