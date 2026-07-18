package jp.yuya.dev.developerdungeon.app;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
class StageService {
    private final RunnerClient runner;
    private final StageRules rules;
    private final OutputSanitizer sanitizer;
    private final StagePersistence persistence;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new HashMap<>();

    @Autowired
    StageService(RunnerClient runner, StageRules rules, OutputSanitizer sanitizer, StagePersistence persistence, Clock clock) {
        this.runner = runner; this.rules = rules; this.sanitizer = sanitizer; this.persistence = persistence; this.clock = clock;
    }
    StageService(RunnerClient runner, StageRules rules, OutputSanitizer sanitizer) {
        this(runner, rules, sanitizer, new MemoryStagePersistence(), Clock.systemUTC());
    }
    StageService(RunnerClient runner, GitCommandParser ignoredParser, StageOneGrader ignoredGrader, OutputSanitizer sanitizer, StagePersistence persistence, Clock clock) {
        this(runner, new StageRules(), sanitizer, persistence, clock);
    }
    StageService(RunnerClient runner, GitCommandParser ignoredParser, StageOneGrader ignoredGrader, OutputSanitizer sanitizer) {
        this(runner, new StageRules(), sanitizer, new MemoryStagePersistence(), Clock.systemUTC());
    }

    synchronized StageView open() { return open("STAGE-GIT-01"); }
    synchronized StageView execute(String raw, String requestId) { return execute("STAGE-GIT-01", raw, requestId); }
    synchronized StageView hint() { return hint("STAGE-GIT-01"); }
    synchronized StageView reset() { return reset("STAGE-GIT-01"); }
    StageProgress progress() { return progress("STAGE-GIT-01"); }

    synchronized StageView open(String stageKey) {
        StageDefinition definition = rules.definition(stageKey);
        Attempt attempt = attempts.get(stageKey);
        if (attempt == null) {
            attempt = persistence.findOpen(stageKey).map(saved -> recoverSaved(definition, saved)).orElseGet(() -> newAttempt(definition));
            attempts.put(stageKey, attempt);
        }
        return attempt.view();
    }
    synchronized StageView execute(String stageKey, String raw, String requestId) {
        StageDefinition definition = rules.definition(stageKey);
        Attempt attempt = attempts.get(stageKey);
        if (attempt == null) attempt = openAttempt(definition);
        UUID persistentRequestId = requireRequestId(requestId);
        StageView recorded = attempt.completedCommandRequests.get(requestId);
        if (recorded != null) return recorded;
        if (attempt.closed) {
            attempt.lastOutput = "この課題はクリア済みです。最初からやり直す場合はリセットしてください。";
            attempt.feedbackKind = StageFeedbackKind.INFO;
            return attempt.view();
        }
        GitCommand normalized;
        long sequence = attempt.commandSequence + 1;
        try {
            normalized = rules.normalize(definition, rules.parse(definition, raw), attempt.targets, attempt.displayedShortIds);
        } catch (IllegalArgumentException exception) {
            StagePersistence.SavedAttempt saved = persistence.recordRejected(UUID.fromString(attempt.attemptId), attempt.version,
                    persistentRequestId, sequence, attempt.generation, rejectionReason(exception), clock.instant());
            attempt.apply(saved);
            attempt.lastOutput = exception.getMessage(); attempt.lastExitCode = null;
            attempt.feedbackKind = StageFeedbackKind.INPUT_REJECTED;
            StageView result = attempt.view(); attempt.completedCommandRequests.put(requestId, result); return result;
        }
        String canonical = normalized.kind().name() + (normalized.objectId() == null ? normalized.branchName() == null ? "" : " " + normalized.branchName() : " " + normalized.objectId());
        GitCommand command = normalized;
        return performOperation(stageKey, definition, attempt, requestId, persistentRequestId, sequence, canonical,
                normalized.kind().name(), StageFeedbackKind.GIT_ERROR,
                active -> runner.execute(new jp.yuya.dev.developerdungeon.contract.ExecuteRequest(active.attemptId, requestId,
                        active.workspaceId, active.generation, command)), command);
    }

    synchronized StageEditorView editor(String stageKey) {
        if (!"STAGE-GIT-04".equals(stageKey)) throw new IllegalArgumentException("editor is not available for this stage");
        StageDefinition definition = rules.definition(stageKey);
        Attempt attempt = attempts.get(stageKey);
        if (attempt == null) attempt = openAttempt(definition);
        if (attempt.closed || attempt.snapshot == null || !attempt.snapshot.mergeInProgress()) return null;
        String readRequestId = UUID.randomUUID().toString();
        var response = runner.readFile(new jp.yuya.dev.developerdungeon.contract.ReadFileRequest(attempt.attemptId,
                readRequestId, attempt.workspaceId, attempt.generation,
                jp.yuya.dev.developerdungeon.contract.StageFileKey.PROFILE_MESSAGES));
        return new StageEditorView(response.content(), response.versionToken(), UUID.randomUUID().toString());
    }

    synchronized StageView edit(String stageKey, String content, String versionToken, String requestId) {
        if (!"STAGE-GIT-04".equals(stageKey)) throw new IllegalArgumentException("editor is not available for this stage");
        StageDefinition definition = rules.definition(stageKey);
        Attempt attempt = attempts.get(stageKey);
        if (attempt == null) attempt = openAttempt(definition);
        UUID persistentRequestId = requireRequestId(requestId);
        StageView recorded = attempt.completedWriteRequests.get(requestId);
        if (recorded != null) return recorded;
        if (attempt.closed) {
            attempt.lastOutput = "この課題はクリア済みです。最初からやり直す場合はリセットしてください。";
            attempt.feedbackKind = StageFeedbackKind.INFO;
            return attempt.view();
        }
        long sequence = attempt.commandSequence + 1;
        String normalized;
        try {
            if (attempt.snapshot == null || !attempt.snapshot.mergeInProgress()) {
                throw new StageInputException("限定エディタはmerge conflictの解消中だけ使用できます。", "EDITOR_NOT_AVAILABLE");
            }
            normalized = StageEditorContentPolicy.normalize(content);
            if (versionToken == null || !versionToken.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("ファイルのversion tokenが不正です。");
        } catch (IllegalArgumentException exception) {
            StagePersistence.SavedAttempt saved = persistence.recordRejected(UUID.fromString(attempt.attemptId), attempt.version,
                    persistentRequestId, sequence, attempt.generation, rejectionReason(exception), clock.instant());
            attempt.apply(saved); attempt.lastOutput = exception.getMessage(); attempt.lastExitCode = null;
            attempt.feedbackKind = StageFeedbackKind.INPUT_REJECTED;
            StageView result = attempt.view(); attempt.completedWriteRequests.put(requestId, result); return result;
        }
        return performOperation(stageKey, definition, attempt, requestId, persistentRequestId, sequence,
                "EDIT_PROFILE_MESSAGES", "EDIT_PROFILE_MESSAGES", StageFeedbackKind.EDIT_CONFLICT, active -> {
                    var response = runner.writeFile(new jp.yuya.dev.developerdungeon.contract.WriteFileRequest(active.attemptId,
                            requestId, active.workspaceId, active.generation,
                            jp.yuya.dev.developerdungeon.contract.StageFileKey.PROFILE_MESSAGES, normalized, versionToken));
                    return new CommandResponse(response.written() ? 0 : 1,
                            response.written() ? "messages.propertiesを保存しました。" : "",
                            response.written() ? "" : "別の操作でファイルが更新されました。画面を更新してから編集し直してください。",
                            false, 0, response.snapshot());
                }, null);
    }

    private StageView performOperation(String stageKey, StageDefinition definition, Attempt attempt, String requestId,
                                       UUID persistentRequestId, long sequence, String canonical, String operationKind,
                                       StageFeedbackKind failureFeedback, RunnerOperation operation, GitCommand displayedCommand) {
        boolean started = persistence.beginCommand(UUID.fromString(attempt.attemptId), attempt.version, persistentRequestId,
                sequence, attempt.generation, canonical, operationKind, clock.instant());
        if (!started) {
            attempt.lastOutput = "この操作は処理済みです。操作は再実行されませんでした。";
            attempt.lastExitCode = null;
            attempt.feedbackKind = StageFeedbackKind.INFO;
            return attempt.view();
        }
        attempt.version++; attempt.commandSequence = sequence;
        long startedAt = System.nanoTime();
        boolean commandPending = true;
        try {
            CommandResponse response = operation.run(attempt);
            StageGrade grade = rules.grade(definition, response.snapshot(), attempt.targets, attempt.highestHint, attempt.playerResets);
            UUID cleanupId = grade.cleared() ? UUID.randomUUID() : null;
            StagePersistence.SavedAttempt saved = persistence.finishCommand(UUID.fromString(attempt.attemptId), attempt.version,
                    persistentRequestId, response.exitCode() == 0 ? "SUCCEEDED" : "GIT_ERROR", response.exitCode(),
                    Math.max(0, (System.nanoTime() - startedAt) / 1_000_000), cleanupId, grade.cleared() ? grade.stars() : null);
            attempt.apply(saved); commandPending = false;
            attempt.lastOutput = sanitizer.sanitize(response.stdout() + (response.stderr().isBlank() ? "" : "\n" + response.stderr()) + (response.outputTruncated() ? "\n[output truncated]" : ""));
            attempt.lastExitCode = response.exitCode(); attempt.snapshot = response.snapshot();
            attempt.feedbackKind = response.exitCode() == 0 ? StageFeedbackKind.SUCCEEDED : failureFeedback;
            if (displayedCommand != null && response.exitCode() == 0 && !response.outputTruncated()) {
                rules.recordDisplayedObjects(definition, displayedCommand, response.stdout(), attempt.targets, attempt.displayedShortIds);
            }
            attempt.grade = grade;
            if (grade.cleared()) {
                attempt.cleanupRequestId = cleanupId.toString();
                try {
                    destroy(attempt, "stage-cleared");
                    saved = persistence.completeClear(UUID.fromString(attempt.attemptId), attempt.version, clock.instant());
                    attempt.apply(saved); attempt.closed = true;
                } catch (RuntimeException cleanupFailure) {
                    saved = persistence.markCleanupPending(UUID.fromString(attempt.attemptId), attempt.version);
                    attempt.apply(saved); attempt.grade = new StageGrade(false, 0, "未復旧");
                    attempt.lastOutput = "クリア状態は確認できましたが、旧環境の削除を確認できません。再接続後にもう一度試してください。";
                    attempt.lastExitCode = null;
                    attempt.feedbackKind = StageFeedbackKind.SYSTEM_ERROR;
                }
            }
        } catch (RuntimeException exception) {
            Attempt previous = attempt;
            try {
                if (commandPending) {
                    StagePersistence.SavedAttempt saved = persistence.finishCommand(UUID.fromString(previous.attemptId), previous.version,
                            persistentRequestId, "RUNNER_ERROR", null, Math.max(0, (System.nanoTime() - startedAt) / 1_000_000), null, null);
                    previous.apply(saved);
                }
                attempt = recover(definition, previous);
                attempts.put(stageKey, attempt);
                attempt.lastOutput = "Runnerへ接続できません。安全のため操作は実行されませんでした。新しい作業環境を用意しました。";
                attempt.lastExitCode = null;
                attempt.feedbackKind = StageFeedbackKind.SYSTEM_ERROR;
            } catch (RuntimeException recoveryFailure) {
                previous.lastOutput = "Runnerへ接続できません。安全のため操作は実行されませんでした。再接続後にもう一度試してください。";
                previous.lastExitCode = null; previous.feedbackKind = StageFeedbackKind.SYSTEM_ERROR;
                previous.grade = new StageGrade(false, 0, "未復旧"); previous.closed = false;
                attempt = previous; attempts.put(stageKey, attempt);
            }
        }
        StageView result = attempt.view();
        if (displayedCommand == null) attempt.completedWriteRequests.put(requestId, result);
        else attempt.completedCommandRequests.put(requestId, result);
        return result;
    }
    synchronized StageView hint(String stageKey) {
        StageDefinition definition = rules.definition(stageKey);
        Attempt attempt = attempts.get(stageKey);
        if (attempt == null) attempt = openAttempt(definition);
        attempt.highestHint = Math.min(4, attempt.highestHint + 1);
        attempt.apply(persistence.increaseHint(UUID.fromString(attempt.attemptId), attempt.version, attempt.highestHint));
        rules.revealHintTargets(definition, attempt.highestHint, attempt.targets, attempt.displayedShortIds);
        return attempt.view();
    }
    synchronized StageView reset(String stageKey) {
        StageDefinition definition = rules.definition(stageKey);
        Attempt attempt = attempts.get(stageKey);
        if (attempt != null) {
            if (attempt.closed) { attempt = newAttempt(definition); attempts.put(stageKey, attempt); return attempt.view(); }
            UUID cleanupId = attempt.cleanupRequestId == null ? UUID.randomUUID() : UUID.fromString(attempt.cleanupRequestId);
            UUID createId = UUID.randomUUID();
            attempt.apply(persistence.beginReset(UUID.fromString(attempt.attemptId), attempt.version, cleanupId, createId));
            attempt.cleanupRequestId = cleanupId.toString();
            try { destroy(attempt, "player-reset"); }
            catch (RuntimeException exception) {
                attempt.apply(persistence.markCleanupPending(UUID.fromString(attempt.attemptId), attempt.version));
                attempt.lastOutput = "旧環境の安全な削除を確認できないため、新しい作業環境は作成しません。再接続後にもう一度リセットしてください。";
                attempt.lastExitCode = null; attempt.feedbackKind = StageFeedbackKind.SYSTEM_ERROR; return attempt.view();
            }
            StagePersistence.SavedAttempt saved = persistence.completeReset(UUID.fromString(attempt.attemptId), attempt.version, false, UUID.randomUUID(), clock.instant());
            attempt = createWorkspace(definition, saved); attempts.put(stageKey, attempt);
        } else {
            attempt = newAttempt(definition); attempts.put(stageKey, attempt);
        }
        return attempt.view();
    }
    StageProgress progress(String stageKey) {
        StageDefinition definition = rules.definition(stageKey);
        return new StageProgress(definition.key(), definition.title(), definition.summary(), persistence.highestStars(definition.key()));
    }
    java.util.List<StageProgress> progresses() { return rules.definitions().stream().map(definition -> progress(definition.key())).toList(); }
    java.util.List<StageProgress> trainingProgresses() { return rules.trainingDefinitions().stream().map(definition -> progress(definition.key())).toList(); }
    StageDefinition definition(String stageKey) { return rules.definition(stageKey); }

    private Attempt openAttempt(StageDefinition definition) {
        Attempt attempt = persistence.findOpen(definition.key()).map(saved -> recoverSaved(definition, saved)).orElseGet(() -> newAttempt(definition));
        attempts.put(definition.key(), attempt); return attempt;
    }
    private Attempt newAttempt(StageDefinition definition) {
        UUID attemptId = UUID.randomUUID(); UUID createId = UUID.randomUUID();
        return createWorkspace(definition, persistence.createStarting(attemptId, definition.key(), createId, clock.instant()));
    }
    private Attempt createWorkspace(StageDefinition definition, StagePersistence.SavedAttempt saved) {
        WorkspaceResponse workspace = runner.create(new WorkspaceRequest(saved.id().toString(), saved.createRequestId().toString(), definition.key(), saved.generation()));
        UUID cleanupId = saved.cleanupRequestId();
        try {
            saved = persistence.workspaceCreated(saved.id(), saved.version(), UUID.fromString(workspace.workspaceId()));
            RepositorySnapshot initial = workspace.snapshot();
            StageRules.StageTargets targets = rules.capture(definition, initial);
            saved = persistence.activate(saved.id(), saved.version(), UUID.fromString(workspace.workspaceId()));
            Attempt attempt = new Attempt(definition, saved.id().toString(), workspace.workspaceId(), workspace.generation(), initial, targets,
                    saved.highestHint(), saved.playerResets(), saved.systemRecoveries(), saved.lastSequence(), saved.version());
            if (attempt.highestHint >= 4) rules.revealHintTargets(definition, attempt.highestHint, targets, attempt.displayedShortIds);
            return attempt;
        } catch (RuntimeException exception) {
            StagePersistence.SavedAttempt pending = null;
            try { pending = persistence.beginCreateCleanup(saved.id(), saved.version(), UUID.fromString(workspace.workspaceId())); }
            catch (RuntimeException stateFailure) { exception.addSuppressed(stateFailure); }
            try { runner.destroy(new DestroyRequest(saved.id().toString(), cleanupId.toString(), workspace.workspaceId(), workspace.generation(), "persistence-create-recovery")); }
            catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            if (pending != null) try { persistence.restartStartingAfterCleanup(pending.id(), pending.version()); }
            catch (RuntimeException stateFailure) { exception.addSuppressed(stateFailure); }
            throw exception;
        }
    }
    private Attempt recover(StageDefinition definition, Attempt previous) {
        UUID cleanupId = previous.cleanupRequestId == null ? UUID.randomUUID() : UUID.fromString(previous.cleanupRequestId);
        UUID createId = UUID.randomUUID();
        StagePersistence.SavedAttempt saved = persistence.prepareSystemRecovery(UUID.fromString(previous.attemptId), previous.version, cleanupId, createId);
        previous.apply(saved); previous.cleanupRequestId = cleanupId.toString(); destroy(previous, "system-recovery");
        saved = persistence.completeReset(UUID.fromString(previous.attemptId), previous.version, true, UUID.randomUUID(), clock.instant());
        return createWorkspace(definition, saved);
    }
    private void destroy(Attempt attempt, String reason) {
        if (attempt.cleanupRequestId == null) attempt.cleanupRequestId = UUID.randomUUID().toString();
        runner.destroy(new DestroyRequest(attempt.attemptId, attempt.cleanupRequestId, attempt.workspaceId, attempt.generation, reason));
        attempt.cleanupRequestId = null;
    }
    private Attempt recoverSaved(StageDefinition definition, StagePersistence.SavedAttempt saved) {
        if (saved.status().equals("STARTING")) return createWorkspace(definition, saved);
        if ((saved.status().equals("CLEARING") || saved.status().equals("CLEANUP_PENDING")) && saved.pendingStars() != null) {
            runner.destroy(new DestroyRequest(saved.id().toString(), saved.cleanupRequestId().toString(), saved.workspaceId().toString(), saved.generation(), "startup-clear-recovery"));
            persistence.completeClear(saved.id(), saved.version(), clock.instant()); return newAttempt(definition);
        }
        UUID cleanupId = saved.cleanupRequestId() == null ? UUID.randomUUID() : saved.cleanupRequestId();
        UUID createId = saved.createRequestId() == null ? UUID.randomUUID() : saved.createRequestId();
        if (saved.status().equals("ACTIVE") || saved.status().equals("EXECUTING")) saved = persistence.prepareSystemRecovery(saved.id(), saved.version(), cleanupId, createId);
        if (!saved.status().equals("RESETTING") && !saved.status().equals("CLEANUP_PENDING")) throw new IllegalStateException("attempt recovery state is invalid");
        runner.destroy(new DestroyRequest(saved.id().toString(), cleanupId.toString(), saved.workspaceId().toString(), saved.generation(), "startup-system-recovery"));
        saved = persistence.completeReset(saved.id(), saved.version(), true, UUID.randomUUID(), clock.instant());
        return createWorkspace(definition, saved);
    }
    private String rejectionReason(IllegalArgumentException exception) {
        if (exception instanceof StageInputException rejected) return rejected.reasonCode();
        return "INVALID_ARGUMENT";
    }

    private UUID requireRequestId(String requestId) {
        if (requestId == null || !requestId.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("request ID is invalid");
        return UUID.fromString(requestId);
    }

    @FunctionalInterface
    private interface RunnerOperation {
        CommandResponse run(Attempt attempt);
    }

    private final class Attempt {
        final StageDefinition definition; final String attemptId; final String workspaceId; final long generation; final StageRules.StageTargets targets;
        RepositorySnapshot snapshot; int highestHint; int playerResets; int systemRecoveryCount; long commandSequence; long version;
        boolean closed; Integer lastExitCode; StageFeedbackKind feedbackKind = StageFeedbackKind.INITIAL;
        String cleanupRequestId; final HashSet<String> displayedShortIds = new HashSet<>();
        final HashMap<String, StageView> completedCommandRequests = new HashMap<>();
        final HashMap<String, StageView> completedWriteRequests = new HashMap<>();
        String lastOutput = "まずは状態を調べてみましょう。";
        StageGrade grade = new StageGrade(false, 0, "未復旧");
        Attempt(StageDefinition definition, String attemptId, String workspaceId, long generation, RepositorySnapshot snapshot, StageRules.StageTargets targets,
                int highestHint, int playerResets, int systemRecoveryCount, long commandSequence, long version) {
            this.definition=definition; this.attemptId=attemptId; this.workspaceId=workspaceId; this.generation=generation; this.snapshot=snapshot; this.targets=targets;
            this.highestHint=highestHint; this.playerResets=playerResets; this.systemRecoveryCount=systemRecoveryCount; this.commandSequence=commandSequence; this.version=version;
        }
        void apply(StagePersistence.SavedAttempt saved) { version=saved.version(); highestHint=saved.highestHint(); playerResets=saved.playerResets(); systemRecoveryCount=saved.systemRecoveries(); commandSequence=saved.lastSequence(); }
        StageView view() { return new StageView(UUID.randomUUID().toString(), lastOutput, lastExitCode, feedbackKind, snapshot, highestHint, playerResets, systemRecoveryCount,
                commandSequence, grade.cleared(), grade.stars(), grade.message(), rules.hints(definition, highestHint, targets)); }
    }
}
